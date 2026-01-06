package br.com.ghfreitas.bmt.common.domain.exceptions

/**
 * Shared Kernel - Domain Exceptions
 *
 * These exceptions represent domain-level errors that can occur across bounded contexts.
 * They are part of the domain language and represent business rule violations.
 */

/**
 * Base exception for all domain-level errors in the BMT application.
 */
abstract class BMTDomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when a manifest file is invalid or fails validation.
 */
class InvalidManifestException(
    message: String,
    cause: Throwable? = null
) : BMTDomainException(message, cause)

/**
 * Thrown when a BMT project cannot be found or loaded.
 */
class ProjectNotFoundException(
    message: String = "BMT project file (.bmt.json) not found in current directory",
    cause: Throwable? = null
) : BMTDomainException(message, cause)

/**
 * Thrown when a file system operation fails at the domain level.
 * This is different from infrastructure IO exceptions - it represents a domain concept of a file system error.
 */
class FileSystemException(
    message: String,
    cause: Throwable? = null
) : BMTDomainException(message, cause)
