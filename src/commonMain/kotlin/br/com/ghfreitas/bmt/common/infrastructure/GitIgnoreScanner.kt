package br.com.ghfreitas.bmt.common.infrastructure

import br.com.ghfreitas.bmt.common.infrastructure.GitIgnoreScanner.Companion.create
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.Path

/**
 * Scans a file system hierarchy to assess whether files or directories are ignored
 * based on gitignore rules. The class supports recursive traversal, evaluates patterns
 * hierarchically, and integrates additional programmatic patterns.
 *
 * This class is particularly useful for identifying excluded files or directories
 * in Git repositories and similar projects that follow `.gitignore` conventions.
 *
 * @constructor Private constructor to ensure that instances are created through the
 * [create] method, which initializes the scanner with appropriate patterns.
 */
class GitIgnoreScanner private constructor(
    private val fileSystem: FileSystem,
    private val rootPath: Path,
    private val rootLevelPatterns: List<GitIgnorePattern>
) {
    private val levelCache = mutableMapOf<Path, GitIgnoreLevel>()

    companion object {
        context(fileSystem: FileSystem)
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

        /**
         * Creates a new instance of [GitIgnoreScanner], which is responsible for identifying ignored files
         * and directories in a file system based on gitignore patterns. This function loads and combines
         * patterns from `.git/info/exclude`, the root `.gitignore` file (if not ignored), and any additional
         * programmatically provided patterns.
         *
         * @param fileSystem The file system to use for file operations.
         * @param rootPath The root path of the repository or directory being scanned.
         * @param additionalPatterns A list of additional gitignore-style patterns to include in the scan.
         * @param ignoreGitIgnore A flag indicating whether to ignore the root `.gitignore` file during pattern loading.
         * @return A [GitIgnoreScanner] instance initialized with the loaded and combined gitignore patterns.
         */
        suspend fun create(
            fileSystem: FileSystem,
            rootPath: Path,
            additionalPatterns: List<String> = emptyList(),
            ignoreGitIgnore: Boolean = false
        ) = withContext(Dispatchers.IO) {
            val gitInfoExcludePatterns = async {
                // Load .git/info/exclude
                val patterns = mutableListOf<GitIgnorePattern>()
                val excludeFile = rootPath / ".git" / "info" / "exclude"
                with(fileSystem) {
                    if (exists(excludeFile)) {
                        patterns.addAll(parseGitIgnoreFile(excludeFile, baseDir = ""))
                    }
                }

                patterns
            }

            val rootIgnorePatterns = async {
                // Load root .gitignore
                val patterns = mutableListOf<GitIgnorePattern>()
                if (!ignoreGitIgnore) {
                    val rootGitignoreFile = rootPath / ".gitignore"
                    with(fileSystem) {
                        if (exists(rootGitignoreFile)) {
                            patterns.addAll(parseGitIgnoreFile(rootGitignoreFile, baseDir = ""))
                        }
                    }
                }

                patterns
            }

            val additionalPatterns = async {
                val patterns = mutableListOf<GitIgnorePattern>()
                // Load programmatic patterns
                additionalPatterns.forEachIndexed { index, line ->
                    GitIgnorePattern.parse(line, "additional", index, "")?.let {
                        patterns.add(it)
                    }
                }

                patterns
            }

            val combinedPatterns = awaitAll(gitInfoExcludePatterns, rootIgnorePatterns, additionalPatterns).flatten()
            GitIgnoreScanner(fileSystem, rootPath, combinedPatterns)
        }


        /**
         * Determines if a path is ignored by coordinating the hierarchy between
         * a parent's status and the current level's patterns.
         */
        private fun isPathIgnored(
            path: Path,
            rootPath: Path,
            isDirectory: Boolean,
            parentLevel: GitIgnoreLevel?,
            currentLevel: GitIgnoreLevel
        ): GitIgnoreResult {
            val relativePath = path.relativeTo(rootPath).toString()

            // 1. Check if the parent directory itself was excluded by an ancestor
            if (parentLevel != null && path.parent != rootPath) {
                val parentPath = path.parent ?: rootPath
                val parentRelativePath = parentPath.relativeTo(rootPath).toString()

                // In git, if a parent directory is ignored,
                // no patterns inside it (even negations) can re-include children.
                val (parentIsIgnored, parentMatchedPattern) = parentLevel.isIgnored(
                    parentRelativePath,
                    isDirectory = true
                )

                if (parentIsIgnored) {
                    return GitIgnoreResult(
                        isIgnored = true,
                        matchedPattern = parentMatchedPattern,
                        level = currentLevel
                    )
                }
            }

            // 2. Evaluate patterns at the current level
            val (isIgnored, matchedPattern) = currentLevel.isIgnored(relativePath, isDirectory)

            return GitIgnoreResult(
                isIgnored = isIgnored,
                matchedPattern = matchedPattern,
                level = currentLevel
            )
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
    fun dfs(): Flow<FileSystemEntry> = flow {
        val stack = ArrayDeque<Pair<Path, String>>()

        if (fileSystem.exists(rootPath)) {
            stack.add(rootPath to "")
        }

        while (stack.isNotEmpty()) {
            val (currentPath, currentRelative) = stack.removeLast()

            val children = try {
                fileSystem.list(currentPath)
            } catch (_: Exception) {
                emptyList()
            }

            for (childPath in children) {
                // Check for cancellation at the start of each child
                currentCoroutineContext().ensureActive()

                val metadata = fileSystem.metadataOrNull(childPath) ?: continue
                val childRelative = if (currentRelative.isEmpty()) {
                    childPath.name
                } else {
                    "$currentRelative/${childPath.name}"
                }

                val result = getIgnoreResult(childPath)
                val entry = FileSystemEntry(childPath, childRelative, metadata.isDirectory, result)

                emit(entry)

                // Depth-first traversal: push directories to the stack
                if (metadata.isDirectory && !result.isIgnored) {
                    stack.add(childPath to childRelative)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Analyzes the specified path to determine whether it should be ignored based on gitignore patterns
     * and hierarchy rules.
     *
     * @param path The file system path to check against gitignore rules.
     * @return A [GitIgnoreResult] containing details about whether the path is ignored, the matched
     *         pattern (if any), and the evaluation level.
     */
    suspend fun getIgnoreResult(path: Path): GitIgnoreResult = withContext(Dispatchers.IO) {
        val parentDir = path.parent ?: rootPath
        val currentLevel = getOrCreateLevel(parentDir)
        // blocking metadata call
        val isDirectory = fileSystem.metadataOrNull(path)?.isDirectory ?: false

        val grandParentDir = parentDir.parent
        val parentLevel = if (grandParentDir != null && parentDir != rootPath) {
            getOrCreateLevel(grandParentDir)
        } else null

        isPathIgnored(path, rootPath, isDirectory, parentLevel, currentLevel)
    }

    private suspend fun getOrCreateLevel(dirPath: Path): GitIgnoreLevel = withContext(Dispatchers.IO) {
        // levelCache is a MutableMap, which is NOT thread-safe.
        // Since we are using parallel-capable Dispatchers.IO,
        // we should use a lock or a thread-safe map if we ever parallelize.
        // For now, keeping it simple as scan() calls this sequentially.
        levelCache.getOrPut(dirPath) {
            val relPath = dirPath.relativeTo(rootPath).toString()
            val patterns = mutableListOf<GitIgnorePattern>()

            val parent = dirPath.parent
            if (parent != null && parent != dirPath && rootPath != dirPath) {
                patterns.addAll(getOrCreateLevel(parent).patterns)
            } else {
                patterns.addAll(rootLevelPatterns)
            }

            val localGitignore = dirPath / ".gitignore"
            // blocking exists call
            with(fileSystem) {
                if (fileSystem.exists(localGitignore)) {
                    patterns.addAll(parseGitIgnoreFile(localGitignore, relPath))
                }
            }
            GitIgnoreLevel(patterns, dirPath, relPath)
        }
    }
}
