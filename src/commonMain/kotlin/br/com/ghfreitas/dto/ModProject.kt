package br.com.ghfreitas.dto

data class ModProject(
    val name: String,
    val folderName: FolderName = FolderName(name),
    val descriptionFile: String? = null, // Should be required when publishing to mod index
    val thumbnail: String? = null,
    val publishToModIndex: Boolean = true,
    val publishToNexusMods: Boolean = true
)
