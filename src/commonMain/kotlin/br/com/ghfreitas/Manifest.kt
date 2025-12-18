package br.com.ghfreitas

import br.com.ghfreitas.dto.BalatroModMetadata
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

private const val TAG = "Manifest"
val log = Logger(
    config = loggerConfigInit(
        platformLogWriter(),
        minSeverity = Severity.Info
    ),
    tag=TAG
)

data class DiscoveredManifest(
    val path: Path,
    val metadata: BalatroModMetadata
)

/**
 * Discovers all valid Balatro mod manifests in a given directory recursively.
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
context(filesystem: FileSystem)
fun discoverManifests(
    rootPath: Path,
    respectGitignore: Boolean = true,
    additionalIgnores: List<String> = emptyList()
): List<DiscoveredManifest> {
    val baseIgnorePatterns = listOf(".git/", ".bmt.json")
    val allPatterns = baseIgnorePatterns + additionalIgnores.toList()

    return if (respectGitignore || additionalIgnores.isNotEmpty()) {
        val parser = HierarchicalGitIgnoreParser(
            fileSystem = filesystem,
            rootPath = rootPath,
            additionalPatterns = allPatterns,
            ignoreGitIgnore = !respectGitignore
        )
        discoverManifestsWithParser(parser)
    } else {
        discoverManifestsWithoutGitignore(rootPath, allPatterns)
    }
}

context(filesystem: FileSystem)
private fun discoverManifestsWithParser(
    parser: HierarchicalGitIgnoreParser
): List<DiscoveredManifest> = runBlocking {
    parser.traverse()
        .filter { entry ->
            !entry.isDirectory &&
            !entry.gitignoreResult.isIgnored &&
            entry.path.name.endsWith(".json") &&
            !entry.path.name.endsWith(".bmt.json")
        }
        .mapNotNull { entry ->
            tryParseAsBalatroManifest(entry.path)?.let { metadata ->
                DiscoveredManifest(entry.path, metadata)
            }
        }
        .toList()
}

context(filesystem: FileSystem)
private fun discoverManifestsWithoutGitignore(
    rootPath: Path,
    ignorePatterns: List<String>
): List<DiscoveredManifest> {
    val allIgnores = ignorePatterns.toSet()

    return filesystem.listRecursively(rootPath)
        .filter { it.name.endsWith(".json") && !it.name.endsWith(".bmt.json") }
        .filterNot { path ->
            log.d { "Found: ${path.name}" }
            allIgnores.any { ignore ->
                path.toString().contains(ignore) || path.matchesGlob(ignore)
            }
        }
        .mapNotNull { jsonPath ->
            tryParseAsBalatroManifest(jsonPath)?.let { metadata ->
                DiscoveredManifest(jsonPath, metadata)
            }
        }
        .toList()
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
context(filesystem: FileSystem)
fun tryParseAsBalatroManifest(jsonPath: Path, strict: Boolean = true): BalatroModMetadata? {
    return try {
        val content = filesystem.read(jsonPath) { readUtf8() }
        val metadata = Json.decodeFromString<BalatroModMetadata>(content)

        metadata.validate().fold(
            { if (strict) null else metadata }, // Invalid manifest
            { metadata } // Valid manifest
        )
    } catch (e: Exception) {
        log.v { "Failed: $jsonPath\n${e.message}" }
        null
    }
}

private fun Path.matchesGlob(pattern: String): Boolean {
    val regex = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".")
        .toRegex()

    return regex.matches(this.toString())
}
