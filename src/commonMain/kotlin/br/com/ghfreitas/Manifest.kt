package br.com.ghfreitas

import br.com.ghfreitas.dto.BalatroModMetadata
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

data class DiscoveredManifest(
    val path: Path,
    val metadata: BalatroModMetadata
)

/**
 * Discovers all valid Balatro mod manifests in a given directory recursively.
 *
 * @param rootPath The root directory to begin searching for manifests.
 * @param respectGitignore Whether to exclude paths defined in a `.gitignore` file.
 * @param additionalIgnores A set of additional glob patterns to ignore during the search.
 * @return A list of discovered manifests, each containing the file path and its metadata.
 */
context(filesystem: FileSystem)
fun discoverManifests(
    rootPath: Path,
    respectGitignore: Boolean = true,
    additionalIgnores: Set<String> = emptySet()
): List<DiscoveredManifest> {
    val gitignore = if (respectGitignore) parseGitignore(rootPath) else emptySet()
    val allIgnores = gitignore + additionalIgnores + setOf(".git", ".bmt.json")

    return filesystem.listRecursively(rootPath).filter {
        it.name.endsWith(".json") && !it.name.endsWith(".bmt.json")
    }.filterNot { path ->
        println("Found: ${path.name}")
        allIgnores.any { ignore ->
            path.toString().contains(ignore) || path.matchesGlob(ignore)
        }
    }.mapNotNull { jsonPath ->
        tryParseAsBalatroManifest(jsonPath)?.let { metadata ->
            DiscoveredManifest(jsonPath, metadata)
        }
    }.toList()
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
            { if (strict) null  else metadata }, // Invalid manifest
            { metadata } // Valid manifest
        )
    } catch (e: Exception) {
        println("Failed: $jsonPath\n${e.message}")
        null
    }
}

/**
 * Parses the contents of a `.gitignore` file located at the specified root path and extracts all
 * non-comment, non-blank patterns as a set of strings.
 *
 * @param rootPath The directory path where the `.gitignore` file is expected to be located.
 * @return A set of strings representing the parsed ignore patterns from the `.gitignore` file.
 *         Returns an empty set if the file does not exist or is empty.
 */
context(filesystem: FileSystem)
fun parseGitignore(rootPath: Path): Set<String> {
    val gitignorePath = rootPath.resolve(".gitignore")
    if (!filesystem.exists(gitignorePath)) return emptySet()

    return filesystem.read(gitignorePath) {
        readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim() }
            .toSet()
    }
}

fun Path.matchesGlob(pattern: String): Boolean {
    val regex = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".")
        .toRegex()

    return regex.matches(this.toString())
}
