package br.com.ghfreitas.bmt.projectmanagement.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain aggregate representing a BMT (Balatro Modding Toolkit) project.
 *
 * This aggregate encapsulates the project configuration and discovered mods,
 * providing business rules for mod management.
 */
@Serializable
data class BMTProject(
    val rootPath: String,
    val discoveredMods: List<DiscoveredMod> = emptyList(),
    val lastScanMilliseconds: Long = 0L
) {
    companion object {
        const val FILE_NAME = ".bmt.json"
    }

    /**
     * Checks if a mod already exists at the given manifest path.
     *
     * @param manifestPath The path to check for existing mod
     * @return true if a mod with this manifest path already exists
     */
    fun hasModAt(manifestPath: String): Boolean =
        discoveredMods.any { it.manifestPath == manifestPath }

    /**
     * Adds a discovered mod to the project with duplicate prevention.
     *
     * @param mod The mod to add
     * @return A new BMTProject with the mod added
     * @throws IllegalArgumentException if a mod already exists at the same path
     */
    fun addDiscoveredMod(mod: DiscoveredMod): BMTProject {
        require(!hasModAt(mod.manifestPath)) { "Mod already exists at ${mod.manifestPath}" }
        return copy(discoveredMods = discoveredMods + mod)
    }

    /**
     * Marks the project as scanned at the given timestamp.
     *
     * @param timestamp The scan timestamp in milliseconds
     * @return A new BMTProject with the updated scan timestamp
     */
    fun markScanned(timestamp: Long): BMTProject =
        copy(lastScanMilliseconds = timestamp)
}
