package br.com.ghfreitas.bmt.projectmanagement.application.service

import br.com.ghfreitas.bmt.common.domain.valueobjects.Invalid
import br.com.ghfreitas.bmt.common.domain.valueobjects.Valid
import br.com.ghfreitas.bmt.common.domain.valueobjects.attempt
import br.com.ghfreitas.bmt.common.domain.valueobjects.validating
import br.com.ghfreitas.bmt.common.infrastructure.FileSystemEntry
import br.com.ghfreitas.bmt.common.infrastructure.GitIgnoreScanner
import br.com.ghfreitas.bmt.common.infrastructure.readAsString
import br.com.ghfreitas.bmt.common.infrastructure.toSegments
import br.com.ghfreitas.bmt.projectmanagement.domain.model.FolderName
import br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject.DiscoveredMod
import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.SteamoddedManifest
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

private const val TAG = "ManifestService"
private val log = Logger(
    config = loggerConfigInit(
        platformLogWriter(),
        minSeverity = Severity.Info
    ),
    tag = TAG
)

class ModDiscoveryService(
    private val fileSystem: FileSystem
) {
    private fun FileSystemEntry.isJsonFile(): Boolean = !this.isDirectory && this.path.name.endsWith(".json")
    private fun FileSystemEntry.isLovelyFolder(): Boolean = this.isDirectory && this.path.name.lowercase() == "lovely"
    private fun FileSystemEntry.isLovelyTomlFile(): Boolean =
        !this.isDirectory && this.path.name.lowercase() == "lovely.toml"

    /**
     * Discovers all valid Balatro mod folders in a given directory recursively.
     *
     * This function respects gitignore semantics including:
     * - Root `.gitignore` file
     * - Hierarchical `.gitignore` files in subdirectories
     * - `.git/info/exclude` patterns
     * - Proper gitignore pattern matching (negation, directory-only, etc.)
     *
     * @param rootPath The root directory to begin searching for manifests.
     * @param respectGitignore Whether to exclude paths defined in gitignore files.
     *        When true, respects .gitignore files at all levels and .git/info/exclude.
     *        When false, only respects additionalIgnores.
     * @param additionalIgnores A set of gitignore-style patterns to ignore during the search.
     *        These patterns follow gitignore syntax and are always applied.
     *        Note: .git/ and .bmt.json are always excluded.
     * @return A list of discovered manifests, each containing the file path and its metadata.
     */
    suspend fun discoverMods(
        rootPath: Path,
        respectGitignore: Boolean = true,
        additionalIgnores: List<String> = emptyList()
    ): Flow<DiscoveredMod> {
        val baseIgnorePatterns = listOf(".git/", ".bmt.json")
        val allPatterns = baseIgnorePatterns + additionalIgnores.toList()

        val parser = GitIgnoreScanner.create(
            fileSystem = fileSystem,
            rootPath = rootPath,
            additionalPatterns = allPatterns,
            ignoreGitIgnore = !respectGitignore,
        )

        return discoverModsWithParser(parser)
    }

    private fun discoverModsWithParser(parser: GitIgnoreScanner): Flow<DiscoveredMod> = flow {
        // Map to accumulate files for the directory currently being scanned
        val currentFolderEntries = mutableListOf<FileSystemEntry>()
        var lastParent: Path? = null

        parser.dfs()
            .filter { it.gitignoreResult.isIgnored.not() }
            .collect { entry ->
                val currentParent = entry.path.parent

                // Heuristic: If the parent changes, the previous folder's scan is likely finished
                if (lastParent != null && currentParent != lastParent) {
                    emitFolderIfValid(lastParent!!, currentFolderEntries)?.let { emit(it) }
                    currentFolderEntries.clear()
                }

                // Collect entries that matter for mod discovery
                if (entry.isJsonFile() || entry.isLovelyTomlFile() || entry.isLovelyFolder()) {
                    currentFolderEntries.add(entry)
                }

                lastParent = currentParent
            }

        // Remember of the last folder in the stack!
        lastParent?.let {
            emitFolderIfValid(it, currentFolderEntries)?.let { emit(it) }
        }
    }

    private fun emitFolderIfValid(modPath: Path, entries: List<FileSystemEntry>): DiscoveredMod? {
        val manifest = entries
            .filter { it.isJsonFile() }
            .firstNotNullOfOrNull { tryParseAsSteamoddedManifest(it.path) }

        val hasLovely =
            entries.any { it.isLovelyFolder() || it.isLovelyTomlFile() }

        val modName = attempt {
            DiscoveredMod.resolveModName(FolderName(modPath.name), manifest)
        }.getOrNull() ?: return null

        val result = validating {
            DiscoveredMod.create(
                name = modName,
                path = modPath.toSegments(),
                metadata = manifest,
                hasLovelyPatches = hasLovely
            )
        }

        return when (result) {
            is Valid -> result.value
            is Invalid -> null
        }
    }

    /**
     * Attempts to parse and validate a JSON file located at the given path as a Balatro mod manifest.
     *
     * If the file contains valid JSON and matches the expected structure for a Balatro mod manifest,
     * the function returns an instance of `BalatroModMetadata`.
     * If the content is invalid or fails validation, the behavior depends on the `strict` parameter.
     *
     * @param jsonPath The file path to the JSON file being parsed.
     * @param strict Determines whether invalid manifests should be ignored. If true, invalid manifests will return null.
     *               If false, the parsed but invalid metadata may still be returned.
     * @return An instance of `BalatroModMetadata` if the file is valid, or null otherwise.
     */
    fun tryParseAsSteamoddedManifest(jsonPath: Path, strict: Boolean = true): SteamoddedManifest? {
        return try {
            val content = with(fileSystem) { jsonPath.readAsString() }
            val metadata = Json.decodeFromString<SteamoddedManifest>(content)

            when (validating { metadata.constraints() }) {
                is Valid -> metadata
                is Invalid -> if (strict) null else metadata
            }

        } catch (e: Exception) {
            log.v { "Failed: $jsonPath\n${e.message}" }
            null
        }
    }
}
