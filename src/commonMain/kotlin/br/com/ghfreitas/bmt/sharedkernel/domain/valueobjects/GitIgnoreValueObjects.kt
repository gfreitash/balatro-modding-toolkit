package br.com.ghfreitas.bmt.sharedkernel.domain.valueobjects

import okio.Path

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


        /**
         * Parses a single line from a gitignore file and converts it into a `GitIgnorePattern` object,
         * if the line represents a valid pattern. Handles comments, negations, and normalizes the pattern.
         *
         * @param line The raw string line from the gitignore file to parse.
         * @param source The path or description of the source file where the line originates.
         * @param lineNumber The line number of the pattern in the source file, used for tracking.
         * @return A `GitIgnorePattern` object representing the parsed pattern, or `null` if the line is a comment or invalid.
         */
        fun parse(
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
            .replace("$PLACEHOLDER_DOUBLE_STAR/", "(?:.*/)?")

            // FIX: / ** / in the middle: "a/**/b" -> a/(?:[^/]+/)*b
            .replace("/$PLACEHOLDER_DOUBLE_STAR/", "/$recursiveDirs")

            // /** at the end: "a/**" -> a/.* // Matches 'a' directory and everything inside it.
            .replace("/$PLACEHOLDER_DOUBLE_STAR", "/.*")

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
