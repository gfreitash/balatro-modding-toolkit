package br.com.ghfreitas.bmt.projectmanagement.domain.service

import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.SteamoddedManifest

/**
 * Domain service for resolving mod identity and naming.
 *
 * Handles the business logic for determining a mod's name based on available metadata.
 */
class ModIdentityService {

    /**
     * Resolves the mod name from metadata or falls back to folder name.
     *
     * Business rule: prefer manifest ID when available, otherwise use the parent folder name.
     *
     * @param parentFolderName The name of the folder containing the mod
     * @param metadata Parsed Steamodded manifest (if available)
     * @return The resolved mod name
     */
    fun resolveModName(
        parentFolderName: String,
        metadata: SteamoddedManifest?,
    ): String = metadata?.id?.value ?: parentFolderName
}
