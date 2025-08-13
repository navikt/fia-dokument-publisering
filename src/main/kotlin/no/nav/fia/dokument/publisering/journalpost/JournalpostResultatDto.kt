package no.nav.fia.dokument.publisering.journalpost

import kotlinx.serialization.Serializable

@Serializable
data class JournalpostResultatDto(
    val journalpostId: String,
    val melding: String?,
    val journalpostferdigstilt: Boolean,
)
