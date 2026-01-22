package br.com.ghfreitas.bmt.projectmanagement.application.model


/**
 * DTO representing the current state of a BMT project for presentation.
 */
data class ProjectStatusDTO(
    val rootPath: String,
    val modCount: Int,
    val lastScanTimestamp: Long?
)
