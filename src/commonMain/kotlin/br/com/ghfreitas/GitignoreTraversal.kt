package br.com.ghfreitas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Represents a single gitignore pattern and its associated properties.
 *
 * This class models a pattern specified in a `.gitignore` file, including details such as:
 * - The pattern string itself.
 * - Whether it is a negation pattern.
 * - Whether it applies only to directories.
 * - Whether it is relative to the root directory of the `.gitignore` file.
 * - The source file from which the pattern originated.
 * - The line number within the source file where the pattern is declared.
 *
 * @property pattern The raw pattern string from the `.gitignore` file.
 * @property isNegation Whether this pattern negates a match (starts with `!`).
 * @property isDirectoryOnly Whether the pattern applies only to directories (ends with `/`).
 * @property isRelativeToRoot Whether the pattern is relative to the root directory.
 * @property source The source `.gitignore` file where this pattern is defined.
 * @property lineNumber The line number in the source file where this pattern is declared.
 */
data class GitIgnorePattern(
    val pattern: String,
    val isNegation: Boolean,
    val isDirectoryOnly: Boolean,
    val isRelativeToRoot: Boolean,
    val source: String,
    val lineNumber: Int,
    val baseDirectory: String = ""  // Relative path from repo root to the .gitignore's directory
) {

    companion object {
        // Define highly unique placeholders to prevent accidental corruption during regex building
        private const val PLACEHOLDER_ESC_STAR = "<!ESC_STAR!>"
        private const val PLACEHOLDER_ESC_QUESTION = "<!ESC_QUESTION!>"
        private const val PLACEHOLDER_ESC_BRACKET_OPEN = "<!ESC_BRACKET_OPEN!>"
        private const val PLACEHOLDER_ESC_BRACKET_CLOSE = "<!ESC_BRACKET_CLOSE!>"
        private const val PLACEHOLDER_ESC_HASH = "<!ESC_HASH!>"
        private const val PLACEHOLDER_ESC_EXCLAIM = "<!ESC_EXCLAIM!>"
        private const val PLACEHOLDER_ESC_SPACE = "<!ESC_SPACE!>"
        private const val PLACEHOLDER_ESC_BACKSLASH = "<!ESC_BACKSLASH!>"
        private const val PLACEHOLDER_SINGLE_STAR = "<!SINGLE_STAR!>"
        private const val PLACEHOLDER_QUESTION = "<!QUESTION!>"
        private const val PLACEHOLDER_DOUBLE_STAR = "<!DOUBLE_STAR!>"
    }

    private val regex: Regex by lazy {
        convertToRegex(pattern)
    }

    /**
     * Checks if the given path matches the regex pattern defined for this instance.
     *
     * @param path The file or directory path to be checked against the pattern.
     * @param isDirectory Whether the path refers to a directory.
     * @return True if the path matches the pattern, otherwise false.
     */
    fun matches(path: String, isDirectory: Boolean = false): Boolean {
        // Check directory-only constraint FIRST
        if (isDirectoryOnly && !isDirectory) {
            return false
        }

        // Make path relative to this pattern's base directory
        val pathToMatch = if (baseDirectory.isEmpty()) {
            path
        } else {
            // Pattern is from nested .gitignore
            val prefix = if (baseDirectory.endsWith("/")) baseDirectory else "$baseDirectory/"
            if (path.startsWith(prefix)) {
                path.removePrefix(prefix)
            } else if (path == baseDirectory) {
                ""  // Checking the directory itself
            } else {
                // Path is not under this pattern's base directory
                return false
            }
        }

        val normalizedPath = pathToMatch.trimStart('/')
        return regex.matches(normalizedPath)
    }

    /**
     * Converts a given glob-like pattern to a regular expression.
     *
     * The method processes the input pattern, handling special cases and wildcards
     * similar to `.gitignore` syntax, and returns a compiled `Regex` object that
     * can be used for pattern matching.
     *
     * @param pattern The glob-like pattern to convert into a regex.
     * @return A `Regex` object created from the converted pattern.
     */
    private fun convertToRegex(pattern: String): Regex {
        var regexPattern = pattern

        // The core regex for zero or more directory segments, used for middle /**/
        // (?:[^/]+/)* ensures that only full directory segments are matched, which fixes the a/**/b bug.
        val recursiveDirs = "(?:[^/]+/)*"

        // STEP 1: Handle directory-only patterns (ending with /)
        if (regexPattern.endsWith("/")) {
            regexPattern = regexPattern.dropLast(1)
        }

        // STEP 1.5: Check path relativity BEFORE processing wildcards
        val hasSlash = regexPattern.contains("/")
        val startsWithSlash = regexPattern.startsWith("/")

        // STEP 2: Process gitignore escape sequences FIRST with placeholders
        regexPattern = regexPattern
            .replace("\\*", PLACEHOLDER_ESC_STAR)
            .replace("\\?", PLACEHOLDER_ESC_QUESTION)
            .replace("\\[", PLACEHOLDER_ESC_BRACKET_OPEN)
            .replace("\\]", PLACEHOLDER_ESC_BRACKET_CLOSE)
            .replace("\\#", PLACEHOLDER_ESC_HASH)
            .replace("\\!", PLACEHOLDER_ESC_EXCLAIM)
            .replace("\\ ", PLACEHOLDER_ESC_SPACE)
            .replace("\\\\", PLACEHOLDER_ESC_BACKSLASH)

        // STEP 3: Replace ** with placeholder before single * processing
        regexPattern = regexPattern.replace("**", PLACEHOLDER_DOUBLE_STAR)

        // STEP 3.5: Replace single wildcards with placeholders
        regexPattern = regexPattern
            .replace("*", PLACEHOLDER_SINGLE_STAR)
            .replace("?", PLACEHOLDER_QUESTION)

        // STEP 4: Escape regex special characters (not covered by gitignore special syntax)
        regexPattern = regexPattern
            .replace(".", "\\.")
            .replace("+", "\\+")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("|", "\\|")

        // STEP 5: Handle ** patterns (replace placeholder with final regex)
        regexPattern = regexPattern
            // ** at the start: "**/b" -> (?:.*/)?b (Matches zero or more directories)
            .replace(PLACEHOLDER_DOUBLE_STAR + "/", "(?:.*/)?")

            // FIX: / ** / in the middle: "a/**/b" -> a/(?:[^/]+/)*b
            .replace("/" + PLACEHOLDER_DOUBLE_STAR + "/", "/" + recursiveDirs)

            // /** at the end: "a/**" -> a/.* // Matches 'a' directory and everything inside it.
            .replace("/" + PLACEHOLDER_DOUBLE_STAR, "/.*")

            // Standalone ** or part of filename: "a**b" or "**"
            .replace(PLACEHOLDER_DOUBLE_STAR, ".*")

        // STEP 6: Restore single wildcard placeholders to regex
        // This translation to [^/]* enforces the rule that * cannot cross directory boundaries, fixing the foo/* bug.
        regexPattern = regexPattern
            .replace(PLACEHOLDER_SINGLE_STAR, "[^/]*")
            .replace(PLACEHOLDER_QUESTION, "[^/]")

        // STEP 7: Restore escaped characters as literals
        regexPattern = regexPattern
            .replace(PLACEHOLDER_ESC_STAR, "\\*")
            .replace(PLACEHOLDER_ESC_QUESTION, "\\?")
            .replace(PLACEHOLDER_ESC_BRACKET_OPEN, "\\[")
            .replace(PLACEHOLDER_ESC_BRACKET_CLOSE, "\\]")
            .replace(PLACEHOLDER_ESC_HASH, "#")
            .replace(PLACEHOLDER_ESC_EXCLAIM, "!")
            .replace(PLACEHOLDER_ESC_SPACE, " ")
            .replace(PLACEHOLDER_ESC_BACKSLASH, "\\\\")

        // STEP 8: Handle path relativity
        if (startsWithSlash) {
            regexPattern = regexPattern.drop(1)
        } else if (!hasSlash) {
            // Pattern without slashes can match at any level
            regexPattern = "(?:.*/)?$regexPattern"
        }

        // STEP 9: Final anchors
        return Regex("^$regexPattern$")
    }
}

/**
 * Represents a level in the hierarchy of `gitignore` rules.
 *
 * Each level corresponds to a specific `.gitignore` file, holding its path,
 * the patterns it defines, and its relative path with respect to the repository root.
 *
 * @property patterns The list of `gitignore` patterns defined at this level.
 * @property path The absolute file path of the `.gitignore` file for this level.
 * @property relativePath The relative path of the `.gitignore` file from the repository root.
 */
data class GitIgnoreLevel(
    val patterns: List<GitIgnorePattern>,
    val path: Path,
    val relativePath: String
) {
    /**
     * Check if a path should be ignored at this level
     */
    fun isIgnored(targetPath: String, isDirectory: Boolean = false): Pair<Boolean, GitIgnorePattern?> {
        val normalizedPath = targetPath.trimStart('/')
        var isIgnored = false
        var matchedPattern: GitIgnorePattern? = null

        for (pattern in patterns) {
            if (pattern.matches(normalizedPath, isDirectory)) {
                isIgnored = !pattern.isNegation
                matchedPattern = pattern
            }
        }

        return Pair(isIgnored, matchedPattern)
    }
}

/**
 * Result of checking if a path should be ignored
 */
data class GitIgnoreResult(
    val isIgnored: Boolean,
    val matchedPattern: GitIgnorePattern?,
    val level: GitIgnoreLevel?
)

/**
 * Represents an entry in a file system, including details about its path, type, and whether it is ignored by gitignore rules.
 *
 * @property path The full, absolute path of the entry in the file system.
 * @property relativePath The path of the entry relative to a specific root directory.
 * @property isDirectory Indicates whether the entry is a directory.
 * @property gitignoreResult The result of evaluating whether this entry is ignored based on gitignore rules.
 */
data class FileSystemEntry(
    val path: Path,
    val relativePath: String,
    val isDirectory: Boolean,
    val gitignoreResult: GitIgnoreResult
)

/**
 * Hierarchical GitIgnore parser that evolves as it traverses the directory tree
 */
class HierarchicalGitIgnoreParser(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val rootPath: Path,
    private val additionalPatterns: List<String> = emptyList(),
    ignoreGitIgnore: Boolean = false
) {
    private val rootLevel = mutableListOf<GitIgnorePattern>()
    private val levelCache = mutableMapOf<Path, GitIgnoreLevel>()

    init {
        loadRepositoryExclude()
        if (!ignoreGitIgnore) {
            loadRootGitignore()
        }
        loadAdditionalPatterns()
    }

    /**
     * Loads and parses the root `.gitignore` file if it exists,
     * adding the parsed patterns to the root-level ignore list.
     */
    private fun loadRootGitignore() {
        val rootGitignoreFile = rootPath / ".gitignore"
        if (fileSystem.exists(rootGitignoreFile)) {
            val patterns = parseGitIgnoreFile(rootGitignoreFile, baseDirectory = "")
            rootLevel.addAll(patterns)
        }
    }

    /**
     * Loads and parses programmatically provided additional patterns.
     * Each pattern string is treated as a line from a `.gitignore` file at the root path.
     * These patterns have the highest priority and can override patterns from
     * `.git/info/exclude` and root `.gitignore` files.
     */
    private fun loadAdditionalPatterns() {
        additionalPatterns.forEachIndexed { index, line ->
            parsePattern(
                line = line,
                source = "additionalPatterns",
                lineNumber = index,
                baseDirectory = ""
            )?.let { pattern ->
                rootLevel.add(pattern)
            }
        }
    }

    /**
     * Loads and parses the `.git/info/exclude` file if it exists in the repository,
     * adding the parsed patterns to the root-level ignore list.
     *
     * This method accesses the `.git/info/exclude` file located under the repository's
     * root path to fetch custom ignore patterns specific to the repository. If the
     * file exists, its contents are processed and converted into a list of ignore
     * patterns using the `parseGitIgnoreFile` method. The resulting patterns are
     * then appended to the root-level ignore list of the gitignore parser.
     *
     * The `.git/info/exclude` file contains gitignore rules that are specific to an
     * individual repository but not shared with others via version control.
     */
    private fun loadRepositoryExclude() {
        val excludeFile = rootPath / ".git" / "info" / "exclude"
        if (fileSystem.exists(excludeFile)) {
            rootLevel.addAll(parseGitIgnoreFile(excludeFile, baseDirectory = ""))
        }
    }

    /**
     * Parses a `.gitignore` file at the given path and converts its contents into a list of gitignore patterns.
     *
     * Each line of the `.gitignore` file is processed to extract valid ignore patterns, while skipping comments
     * and empty lines. Patterns are converted into `GitIgnorePattern` objects, which include metadata such as
     * the original line number from the source file, whether the pattern targets directories, and if the pattern
     * is negated.
     *
     * @param gitignorePath the file path to the `.gitignore` file to be parsed
     * @return a list of `GitIgnorePattern` objects representing the parsed ignore rules, or an empty list if
     * the file does not exist or contains no valid patterns
     */
    private fun parseGitIgnoreFile(gitignorePath: Path, baseDirectory: String = ""): List<GitIgnorePattern> {
        if (!fileSystem.exists(gitignorePath)) {
            return emptyList()
        }

        val patterns = mutableListOf<GitIgnorePattern>()
        fileSystem.read(gitignorePath) {
            val lines = readLines().toList()
            lines.forEachIndexed { lineNumber, line ->
                parsePattern(line, gitignorePath.toString(), lineNumber, baseDirectory)?.let {
                    patterns.add(it)
                }
            }
        }
        return patterns
    }

    /**
     * Parses a single line from a gitignore file and converts it into a `GitIgnorePattern` object,
     * if the line represents a valid pattern. Handles comments, negations, and normalizes the pattern.
     *
     * @param line The raw string line from the gitignore file to parse.
     * @param source The path or description of the source file where the line originates.
     * @param lineNumber The line number of the pattern in the source file, used for tracking.
     * @return A `GitIgnorePattern` object representing the parsed pattern, or `null` if the line is a comment or invalid.
     */
    private fun parsePattern(
        line: String,
        source: String,
        lineNumber: Int,
        baseDirectory: String = ""
    ): GitIgnorePattern? {
        var pattern = line

        if (pattern.isBlank() || pattern.trimStart().startsWith('#')) {
            return null
        }

        if (pattern.startsWith("\\#")) {
            pattern = pattern.drop(1)
        }

        // Handle trailing spaces - preserve if escaped
        var trailingEscapedSpaces = 0
        var i = pattern.length - 1
        while (i > 0) {
            if (pattern[i] == ' ' && pattern[i - 1] == '\\') {
                trailingEscapedSpaces++
                i -= 2
            } else if (pattern[i] == ' ') {
                i--  // Unescaped space, will be trimmed
            } else {
                break
            }
        }

        // Trim unescaped trailing spaces
        pattern = pattern.trimEnd()

        // Restore escaped spaces (without backslash)
        if (trailingEscapedSpaces > 0) {
            pattern += " ".repeat(trailingEscapedSpaces)
        }

        if (pattern.isBlank()) {
            return null
        }

        val isNegation = pattern.startsWith("!")
        if (isNegation) {
            pattern = pattern.drop(1)
        }

        if (pattern.startsWith("\\!")) {
            pattern = pattern.drop(1)
        }

        val isDirectoryOnly = pattern.endsWith("/")
        val isRelativeToRoot = pattern.startsWith("/") || pattern.contains("/")

        val result = GitIgnorePattern(
            pattern = pattern,
            isNegation = isNegation,
            isDirectoryOnly = isDirectoryOnly,
            isRelativeToRoot = isRelativeToRoot,
            source = source,
            lineNumber = lineNumber,
            baseDirectory = baseDirectory
        )

        return result
    }

    /**
     * Retrieves the gitignore rules and patterns applicable to a given directory level.
     *
     * This method computes the `GitIgnoreLevel` for a specified directory by combining:
     * - Patterns from the parent directories (if applicable),
     * - Patterns defined in the `.gitignore` file within the directory,
     * - Global patterns at the root level if the directory is at the root.
     *
     * The resulting `GitIgnoreLevel` provides a comprehensive view of the patterns that
     * apply to the given directory path.
     *
     * @param dirPath The path of the directory for which the gitignore level is being retrieved.
     * @return A `GitIgnoreLevel` object containing the applicable patterns and metadata for the directory.
     */
    private fun getGitIgnoreLevel(dirPath: Path): GitIgnoreLevel {
        return levelCache.getOrPut(dirPath) {
            val relativePath = dirPath.relativeTo(rootPath).toString()
            val patterns = mutableListOf<GitIgnorePattern>()

            val parentPath = dirPath.parent
            if (parentPath != null && parentPath != dirPath && rootPath != dirPath) {
                val parentLevel = getGitIgnoreLevel(parentPath)
                patterns.addAll(parentLevel.patterns)
            } else {
                // At root level, add global patterns
                patterns.addAll(rootLevel)
            }

            // Add patterns from .gitignore file in this directory
            val gitignoreFile = dirPath / ".gitignore"
            if (fileSystem.exists(gitignoreFile)) {
                patterns.addAll(parseGitIgnoreFile(gitignoreFile, baseDirectory = relativePath))
            }

            GitIgnoreLevel(patterns, dirPath, relativePath)
        }
    }

    /**
     * Determines whether the given path is ignored based on gitignore rules.
     *
     * This method evaluates the specified path against the relevant gitignore patterns
     * from the associated hierarchy, checking if it matches any exclusion rules.
     * The result includes whether the path is ignored, the matched pattern (if any),
     * and the associated gitignore level.
     *
     * @param path the path to be evaluated against gitignore rules
     * @return a GitIgnoreResult containing information about whether the path is ignored,
     * the matched gitignore pattern (if found), and the relevant gitignore level
     */
// Updated implementation for HierarchicalGitIgnoreParser:
    fun isIgnored(path: Path): GitIgnoreResult {
        val parentDir = path.parent ?: rootPath
        val relativePath = path.relativeTo(rootPath).toString()

        // Get metadata to determine if it's a directory
        val metadata = fileSystem.metadataOrNull(path)
        val isDirectory = metadata?.isDirectory ?: false

        // 1. Check if the parent directory itself is excluded by an ancestor rule.
        // The parent's ignored status is determined by the rules from its OWN parent (grandParent).
        if (parentDir != rootPath) {
            val grandParentDir = parentDir.parent ?: rootPath
            val grandParentLevel = getGitIgnoreLevel(grandParentDir)
            val parentRelativePath = parentDir.relativeTo(rootPath).toString()

            // Check if the PARENT path is ignored *as a directory*
            val (parentIsIgnored, parentMatchedPattern) = grandParentLevel.isIgnored(
                parentRelativePath,
                isDirectory = true
            )

            // If the parent is excluded, the current path is also excluded,
            // regardless of any specific negation pattern matching the current path.
            if (parentIsIgnored) {
                // Use the level of the current file's parent directory for context
                val level = getGitIgnoreLevel(parentDir)
                return GitIgnoreResult(
                    isIgnored = true,
                    matchedPattern = parentMatchedPattern, // Return the pattern that ignored the parent
                    level = level
                )
            }
        }

        // 2. If the parent is NOT excluded, proceed with normal pattern matching
        val level = getGitIgnoreLevel(parentDir)
        val (isIgnored, matchedPattern) = level.isIgnored(relativePath, isDirectory)
        return GitIgnoreResult(isIgnored, matchedPattern, level)
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
     * @return a `Flow` emitting `FileSystemEntry` objects representing the discovered file system entries
     */
    fun traverse(): Flow<FileSystemEntry> = flow {
        traverseRecursive(rootPath, "")
    }.flowOn(Dispatchers.IO)

    /**
     * Recursively traverses a file system starting from the specified path, emitting each file system entry
     * while respecting gitignore rules. Entries include metadata such as their absolute path, relative path,
     * type (file or directory), and whether they are ignored.
     *
     * Ignored directories are skipped entirely, so their contents are not processed.
     *
     * @param currentPath The absolute path in the file system where traversal begins.
     * @param relativePath The path of the current entry relative to a defined root directory.
     */
    private suspend fun FlowCollector<FileSystemEntry>.traverseRecursive(
        currentPath: Path,
        relativePath: String
    ) {
        if (!fileSystem.exists(currentPath)) return

        try {
            val children = fileSystem.list(currentPath)

            for (childPath in children) {
                val metadata = fileSystem.metadataOrNull(childPath) ?: continue
                val childRelativePath = if (relativePath.isEmpty()) {
                    childPath.name
                } else {
                    "$relativePath/${childPath.name}"
                }

                // Check if this path should be ignored
                val gitignoreResult = isIgnored(childPath)

                val entry = FileSystemEntry(
                    path = childPath,
                    relativePath = childRelativePath,
                    isDirectory = metadata.isDirectory,
                    gitignoreResult = gitignoreResult
                )

                emit(entry)

                // If it's a directory and not ignored, traverse it recursively
                if (metadata.isDirectory && !gitignoreResult.isIgnored) {
                    traverseRecursive(childPath, childRelativePath)
                }
                // Note: If the directory is ignored, we skip traversing it entirely!
            }
        } catch (e: Exception) {
            // Handle permission errors gracefully
            println("Warning: Could not list directory $currentPath: ${e.message}")
        }
    }

    /**
     * Retrieves a flow of file paths representing tracked files in the file system.
     *
     * This method filters the file system entries emitted by `traverse()`, excluding directories
     * and files ignored by gitignore rules, and maps the results to their respective paths.
     *
     * @return a flow of `Path` objects representing the tracked files.
     */
    fun getTrackedFiles(): Flow<Path> = traverse()
        .filter { !it.gitignoreResult.isIgnored && !it.isDirectory }
        .map { it.path }

    /**
     * Retrieves a flow of file paths that are identified as ignored based on gitignore rules.
     *
     * This method filters the file system entries emitted by `traverse()`, selecting only those
     * that are marked as ignored, and maps the results to their respective file paths.
     *
     * @return a flow of `Path` objects representing ignored files.
     */
    fun getIgnoredFiles(): Flow<Path> = traverse()
        .filter { it.gitignoreResult.isIgnored }
        .map { it.path }

    /**
     * Clears the cached hierarchy of gitignore levels.
     *
     * This method resets the internal cache that stores precomputed gitignore levels
     * for directories, removing all previously stored data. It is useful in scenarios
     * where the gitignore rules have changed or the repository structure has been modified,
     * ensuring that subsequent operations use updated patterns and rules.
     */
    fun clearCache() {
        levelCache.clear()
    }
}
