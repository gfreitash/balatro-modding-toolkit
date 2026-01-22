package br.com.ghfreitas.bmt.common.infrastructure

import br.com.ghfreitas.bmt.common.domain.service.IgnoreLogicService
import br.com.ghfreitas.bmt.common.domain.valueobjects.FileSystemEntry
import br.com.ghfreitas.bmt.common.domain.valueobjects.GitIgnoreLevel
import br.com.ghfreitas.bmt.common.domain.valueobjects.GitIgnorePattern
import br.com.ghfreitas.bmt.common.domain.valueobjects.GitIgnoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Infrastructure Service that coordinates IO and traversal.
 */
class GitIgnoreScanner(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val rootPath: Path,
    private val logicService: IgnoreLogicService = IgnoreLogicService(),
    private val additionalPatterns: List<String> = emptyList(),
    private val ignoreGitIgnore: Boolean = false
) {
    private val rootLevelPatterns = mutableListOf<GitIgnorePattern>()
    private val levelCache = mutableMapOf<Path, GitIgnoreLevel>()

    init {
        loadGlobalPatterns()
    }

    private fun loadGlobalPatterns() {
        // Load .git/info/exclude
        val excludeFile = rootPath / ".git" / "info" / "exclude"
        if (fileSystem.exists(excludeFile)) {
            rootLevelPatterns.addAll(parseGitIgnoreFile(excludeFile, baseDir = ""))
        }

        // Load root .gitignore
        if (!ignoreGitIgnore) {
            val rootGitignoreFile = rootPath / ".gitignore"
            if (fileSystem.exists(rootGitignoreFile)) {
                val patterns = parseGitIgnoreFile(rootGitignoreFile, baseDir = "")
                rootLevelPatterns.addAll(patterns)
            }
        }

        // Load programmatic patterns
        additionalPatterns.forEachIndexed { index, line ->
            GitIgnorePattern.parse(line, "additional", index, "")?.let {
                rootLevelPatterns.add(it)
            }
        }
    }

    private suspend fun FlowCollector<FileSystemEntry>.traverseRecursive(
        currentPath: Path,
        relativePath: String
    ) {
        if (!fileSystem.exists(currentPath)) return

        val children = try {
            fileSystem.list(currentPath)
        } catch (_: Exception) {
            emptyList()
        }

        for (childPath in children) {
            val metadata = fileSystem.metadataOrNull(childPath) ?: continue
            val childRelative = if (relativePath.isEmpty()) childPath.name else "$relativePath/${childPath.name}"

            val result = getIgnoreResult(childPath)
            val entry = FileSystemEntry(childPath, childRelative, metadata.isDirectory, result)

            emit(entry)

            // Skip recursion if directory is ignored
            if (metadata.isDirectory && !result.isIgnored) {
                traverseRecursive(childPath, childRelative)
            }
        }
    }


    /**
     * Traverses a file system hierarchy starting from a root path, emitting each file system entry
     * while respecting gitignore rules. Entries include metadata such as their absolute path,
     * relative path, type (file or directory), and whether they are ignored.
     *
     * This function explores the directory structure recursively, skipping ignored directories
     * as determined by the gitignore rules. For directories that are not ignored, their contents
     * are also recursively traversed.
     *
     * @return a [Flow] emitting [FileSystemEntry] objects representing the discovered file system entries
     */
    fun scan(): Flow<FileSystemEntry> = flow {
        traverseRecursive(rootPath, "")
    }.flowOn(Dispatchers.IO)

    /**
     * Analyzes the specified path to determine whether it should be ignored based on gitignore patterns
     * and hierarchy rules.
     *
     * @param path The file system path to check against gitignore rules.
     * @return A [GitIgnoreResult] containing details about whether the path is ignored, the matched
     *         pattern (if any), and the evaluation level.
     */
    fun getIgnoreResult(path: Path): GitIgnoreResult {
        val parentDir = path.parent ?: rootPath
        val currentLevel = getOrCreateLevel(parentDir)
        val isDirectory = fileSystem.metadataOrNull(path)?.isDirectory ?: false

        // Find the parent level for hierarchical checking
        val grandParentDir = parentDir.parent
        val parentLevel = if (grandParentDir != null && parentDir != rootPath) {
            getOrCreateLevel(grandParentDir)
        } else null

        return logicService.evaluate(path, rootPath, isDirectory, parentLevel, currentLevel)
    }

    private fun getOrCreateLevel(dirPath: Path): GitIgnoreLevel {
        return levelCache.getOrPut(dirPath) {
            val relPath = dirPath.relativeTo(rootPath).toString()
            val patterns = mutableListOf<GitIgnorePattern>()

            // Inherit patterns from parent
            val parent = dirPath.parent
            if (parent != null && parent != dirPath && rootPath != dirPath) {
                patterns.addAll(getOrCreateLevel(parent).patterns)
            } else {
                patterns.addAll(rootLevelPatterns)
            }

            // Add local .gitignore
            val localGitignore = dirPath / ".gitignore"
            if (fileSystem.exists(localGitignore)) {
                patterns.addAll(parseGitIgnoreFile(localGitignore, relPath))
            }

            GitIgnoreLevel(patterns, dirPath, relPath)
        }
    }

    private fun parseGitIgnoreFile(path: Path, baseDir: String): List<GitIgnorePattern> {
        val patterns = mutableListOf<GitIgnorePattern>()
        fileSystem.read(path) {
            readLines().forEachIndexed { index, line ->
                GitIgnorePattern.parse(line, path.toString(), index, baseDir)?.let {
                    patterns.add(it)
                }
            }
        }
        return patterns
    }
}
