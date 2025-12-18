import br.com.ghfreitas.cwd
import br.com.ghfreitas.discoverManifests
import br.com.ghfreitas.readAsString
import br.com.ghfreitas.writeToFile
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.prompt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.time.Clock

val PrettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

fun CliktCommand.findManifests(noGitignore: Boolean = false, ignore: List<String> = emptyList()) = with(FileSystem.SYSTEM) {
    val project = BMTProject.load() ?: error("Not in a BMT project. Run 'bmt-cli init' first.")
    val discoveredManifests = discoverManifests(
        rootPath = project.rootPath.toPath(),
        respectGitignore = !noGitignore,
        additionalIgnores = ignore
    )

    val newMods = discoveredManifests.filterNot { manifest ->
        project.discoveredMods.any { it.manifestPath == manifest.path.toString() }
    }

    if (newMods.isEmpty()) {
        echo("No new mods found")
        return
    }

    val updatedMods = project.discoveredMods.toMutableList()

    newMods.forEach { manifest ->
        echo("Found manifest: ${manifest.path}")
        val include = terminal.prompt(
            "Include this mod in the project?",
            choices = listOf("y", "N"),
            default = "N"
        ).let { (it ?: "n").lowercase() == "y" }


        updatedMods.add(
            DiscoveredMod(
                name = manifest.metadata.id.value,
                manifestPath = manifest.path.toString(),
                included = include
            )
        )
    }

    project.copy(
        discoveredMods = updatedMods,
        lastScanMilliseconds = Clock.System.now().toEpochMilliseconds()
    ).save()
}

@Serializable
data class BMTProject(
    val rootPath: String = "",
    val discoveredMods: List<DiscoveredMod> = emptyList(),
    val lastScanMilliseconds: Long = 0L
) {

    companion object {
        const val FILE = ".bmt.json"

        context(_: FileSystem)
        fun load(): BMTProject? = runCatching {
            PrettyJson.decodeFromString<BMTProject>(FILE.toPath().readAsString())
        }.getOrElse { return null }

        context(fileSystem: FileSystem)
        fun exists(): Boolean? {
            val path = FILE.toPath()

            val fileExists = fileSystem.exists(path)
            if (!fileExists) return false


            // If the file exists, check if it has the root path not empty, otherwise fill it
            val project = load() ?: return null
            if (project.rootPath.isEmpty()) {
                project.copy(rootPath = FileSystem.cwd().toString()).save()
            }

            return true
        }
    }

    context(fileSystem: FileSystem)
    fun save() {
        FILE.toPath().writeToFile(PrettyJson.encodeToString(this@BMTProject))
    }
}

@Serializable
data class DiscoveredMod(
    val name: String,
    val manifestPath: String,
    val included: Boolean,
    val discoveredAt: Long = Clock.System.now().toEpochMilliseconds()
)

// bmt-cli init
class InitCommand : CliktCommand(name = "init") {
    private val noGitignore by option("--no-gitignore").flag().help("Disregard .gitignore exclusions")
    private val ignore by option("--ignore").multiple().help("Additional ignore glob patterns")

    override fun help(context: Context): String = "Initializes a BMT project in the current directory and search for manifests."

    override fun run() = with(FileSystem.SYSTEM) {
        val exists = BMTProject.exists()
        if (exists == null) {
            echo("It was not possible to read the BMT project file. The file might be corrupted or this might be a transient failure")
            return
        }
        if (exists) {
            echo("BMT project already initialized in ${FileSystem.cwd()}")

            findManifests(noGitignore, ignore)
            return
        }

        val project = BMTProject(rootPath = FileSystem.cwd().toString())
        project.save()
        echo("Initialized BMT project in ${FileSystem.cwd()}")

        // Immediately run discovery
        findManifests(noGitignore, ignore)
    }
}

// bmt-cli find-manifests
class FindManifestsCommand : CliktCommand(name = "find-mods") {
    private val noGitignore by option("--no-gitignore").flag().help("Disregard .gitignore exclusions")
    private val ignore by option("--ignore").multiple().help("Additional ignore glob patterns")

    override fun help(context: Context): String =
        "Finds all mod manifests under the current directory and adds them to the BMT project."

    override fun run() = findManifests(noGitignore, ignore)
}

class Entrypoint : CliktCommand() {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    Entrypoint().subcommands(
        InitCommand(),
        FindManifestsCommand()
    ).main(args)
}
