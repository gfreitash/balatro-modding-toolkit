package br.com.ghfreitas.bmt.projectmanagement.application.model

/**
 * DTO representing a mod found during scanning, to be presented to the user.
 */
data class DiscoveredModDTO(
    val name: String,
    val path: String,
    val hasManifest: Boolean,
    val hasLovelyPatches: Boolean
)

/**
 * DTO for registering a discovered mod with the user's decision.
 */
data class ModRegistrationDecision(
    val name: String,
    val path: String,
    val included: Boolean
)

/**
 * DTO representing the current state of a BMT project for presentation.
 */
data class ProjectStatusDTO(
    val rootPath: String,
    val modCount: Int,
    val lastScanTimestamp: Long
)
