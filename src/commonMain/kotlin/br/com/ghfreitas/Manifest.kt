package br.com.ghfreitas

import br.com.ghfreitas.dto.BalatroModMetadata
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

data class DiscoveredManifest(
    val path: Path,
    val metadata: BalatroModMetadata
)

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

context(filesystem: FileSystem)
fun tryParseAsBalatroManifest(jsonPath: Path): BalatroModMetadata? {
    return try {
        val content = filesystem.read(jsonPath) { readUtf8() }
        val metadata = Json.decodeFromString<BalatroModMetadata>(content)

        metadata.validate().fold(
            { null.also { println("Invalid") } }, // Invalid manifest
            { metadata } // Valid manifest
        )
    } catch (e: Exception) {
        println("Failed: $jsonPath\n${e.message}")
        null // Not a valid JSON or manifest
    }
}

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
    // Simple glob matching - you might want a more sophisticated implementation
    val regex = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".")
        .toRegex()

    return regex.matches(this.toString())
}
