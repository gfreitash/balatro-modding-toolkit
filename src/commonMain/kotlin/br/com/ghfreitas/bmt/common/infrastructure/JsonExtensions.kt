package br.com.ghfreitas.bmt.common.infrastructure

import kotlinx.serialization.json.Json

/**
 * Shared Kernel - JSON Infrastructure Utilities
 *
 * Pre-configured JSON serializers for consistent serialization across the application.
 */

/**
 * JSON serializer configured for pretty-printing and lenient parsing.
 * - Pretty prints JSON output for readability
 * - Ignores unknown keys when deserializing for forward compatibility
 */
val PrettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}
