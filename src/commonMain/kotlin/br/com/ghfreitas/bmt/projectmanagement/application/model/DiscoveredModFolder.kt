package br.com.ghfreitas.bmt.projectmanagement.application.model

import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.SteamoddedManifest
import okio.Path

data class DiscoveredModFolder(
    val path: Path,
    val metadata: SteamoddedManifest?,
    val hasLovelyPatches: Boolean = false
)
