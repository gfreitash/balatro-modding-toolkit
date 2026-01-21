package br.com.ghfreitas.bmt.projectmanagement.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Value object representing a discovered mod within a BMT project.
 *
 * Part of the BMTProject aggregate, this captures the essential information
 * about a mod discovered during project scanning.
 */
@Serializable
data class DiscoveredMod(
    val name: String,
    val manifestPath: String,
    val included: Boolean,
    val discoveredAt: Long = Clock.System.now().toEpochMilliseconds()
)
