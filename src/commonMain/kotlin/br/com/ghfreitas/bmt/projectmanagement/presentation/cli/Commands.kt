package br.com.ghfreitas.bmt.projectmanagement.presentation.cli

import br.com.ghfreitas.bmt.common.domain.valueobjects.Failure
import br.com.ghfreitas.bmt.common.domain.valueobjects.Success
import br.com.ghfreitas.bmt.common.domain.valueobjects.attempt
import br.com.ghfreitas.bmt.common.domain.valueobjects.isFailure
import br.com.ghfreitas.bmt.projectmanagement.application.error.ProjectError
import br.com.ghfreitas.bmt.projectmanagement.application.model.ModRegistrationDecision
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.application.service.ModDiscoveryService
import br.com.ghfreitas.bmt.projectmanagement.application.service.ProjectService
import br.com.ghfreitas.bmt.projectmanagement.domain.service.ModIdentityService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import okio.FileSystem
import okio.SYSTEM

/**
 * Root CLI entrypoint for the BMT (Balatro Modding Toolkit) CLI.
 */
class Entrypoint : CliktCommand() {
    override fun run() = Unit
}

/**
 * CLI command to initialize a BMT project in the current directory.
 */
class InitCommand : CliktCommand(name = "init") {
    private val noGitignore by option("--no-gitignore").flag().help("Disregard .gitignore exclusions")
    private val ignore by option("--ignore").multiple().help("Additional ignore glob patterns")

    override fun help(context: Context): String =
        "Initializes a BMT project in the current directory and search for manifests."

    override fun run() {
        val term = terminal
        with(FileSystem.SYSTEM) {
            val projectService = createProjectService(this)
            val projectInitialized = projectService.checkProjectStatus()

            if (projectInitialized == null) {
                echo("It was not possible to read the BMT project file. The file might be corrupted or this might be a transient failure")
                return
            }

            if (projectInitialized) {
                val result = attempt { projectService.ensureRootPathSet() }
                if (result.isFailure()) {
                    handleError(result.value)
                    return
                }

                echo("BMT project already initialized in ${result.value.rootPath}")
                findAndRegisterMods(projectService, term, noGitignore, ignore)
            } else {

                val result = attempt { projectService.initialize() }
                if (result.isFailure()) {
                    handleError(result.value)
                    return
                }

                echo("Initialized BMT project in ${result.value.rootPath}")
                findAndRegisterMods(projectService, term, noGitignore, ignore)
            }
        }
    }
}

/**
 * CLI command to find mod manifests and add them to the BMT project.
 */
class FindManifestsCommand : CliktCommand(name = "find-mods") {
    private val noGitignore by option("--no-gitignore").flag().help("Disregard .gitignore exclusions")
    private val ignore by option("--ignore").multiple().help("Additional ignore glob patterns")

    override fun help(context: Context): String =
        "Finds all mod manifests under the current directory and adds them to the BMT project."

    override fun run() {
        val term = terminal
        with(FileSystem.SYSTEM) {
            val projectService = createProjectService(this)

            if (projectService.checkProjectStatus() != true) {
                error("Not in a BMT project. Run 'bmt-cli init' first.")
            }

            findAndRegisterMods(projectService, term, noGitignore, ignore)
        }
    }
}

private fun createProjectService(fileSystem: FileSystem): ProjectService {
    return ProjectService(
        fileSystem = fileSystem,
        repository = BMTProjectRepository(fileSystem),
        modDiscoveryService = ModDiscoveryService(fileSystem),
        modIdentityService = ModIdentityService()
    )
}

/**
 * Discovers and registers new mod manifests in the project.
 */
private fun CliktCommand.findAndRegisterMods(
    projectService: ProjectService,
    terminal: Terminal,
    noGitignore: Boolean,
    ignore: List<String>
) {
    val result = attempt {
        projectService.discoverNewMods(
            respectGitignore = !noGitignore,
            additionalIgnores = ignore
        )
    }
    if (result.isFailure()) return handleError(result.value)

    val newMods = result.value
    if (newMods.isEmpty()) {
        echo("No new mods found")
        return
    }

    val decisions = newMods.map { mod ->
        echo("Found mod: ${mod.name} at ${mod.path}")
        val include = terminal.prompt(
            "Include this mod in the project?",
            choices = listOf("y", "N"),
            default = "N"
        ).let { (it ?: "n").lowercase() == "y" }

        ModRegistrationDecision(
            name = mod.name,
            path = mod.path,
            included = include
        )
    }

    when (val registerResult = attempt { projectService.registerDiscoveredMods(decisions) }) {
        is Success -> echo("Mods registered successfully")
        is Failure -> handleError(registerResult.value)
    }
}

/**
 * Handles project errors by displaying appropriate error messages to the user.
 */
private fun handleError(error: ProjectError) {
    when (error) {
        is ProjectError.NotFound -> error("Not in a BMT project. Run 'bmt-cli init' first.")
        is ProjectError.Corrupted -> error("The project file is corrupted. Please fix or delete .bmt.json and try again.")
        is ProjectError.IoError -> error("IO error: ${error.message}")
        is ProjectError.ModAlreadyExists -> error("Mod already exists at ${error.path}")
    }
}
