package no.nav.fia.dokument.publisering.api

import kotlinx.serialization.Serializable

@Serializable
data class DokumentDto(
    val dokumentId: String,
    val type: String,
    val samarbeidNavn: String,
    val innhold: String,
)
