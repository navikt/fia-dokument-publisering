package no.nav.fia.dokument.publisering.api

import kotlinx.serialization.Serializable

@Serializable
data class DokumentDto(
    val dokumentId: String,
    val innhold: String,
)
