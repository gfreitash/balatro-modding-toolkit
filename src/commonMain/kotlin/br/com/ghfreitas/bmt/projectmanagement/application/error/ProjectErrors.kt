package br.com.ghfreitas.bmt.projectmanagement.application.error

/**
 * Sealed hierarchy of errors that can occur during project operations.
 *
 * This replaces exception-based error handling with typed functional errors,
 * enabling exhaustive pattern matching and explicit error propagation.
 */
sealed class ProjectError {
    /** The project file (.bmt.json) does not exist. */
    data object NotFound : ProjectError()

    /** The project file exists but could not be parsed. */
    data object Corrupted : ProjectError()

    /** A file system I/O operation failed. */
    data class IoError(val message: String) : ProjectError()

    /** Attempted to add a mod that already exists at the given path. */
    data class ModAlreadyExists(val path: String) : ProjectError()
}


