package br.com.ghfreitas.bmt.common.domain.service

import br.com.ghfreitas.bmt.common.domain.valueobjects.GitIgnoreLevel
import br.com.ghfreitas.bmt.common.domain.valueobjects.GitIgnoreResult
import okio.Path

/**
 * Domain Service responsible for the hierarchical logic of gitignore.
 * It encapsulates the rule: "If a parent directory is excluded,
 * its children are excluded regardless of negations".
 */
class IgnoreLogicService {

    /**
     * Determines if a path is ignored by coordinating the hierarchy between
     * a parent's status and the current level's patterns.
     */
    fun evaluate(
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
