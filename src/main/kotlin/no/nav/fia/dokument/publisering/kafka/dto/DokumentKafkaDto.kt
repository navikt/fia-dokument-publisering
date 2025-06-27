package no.nav.fia.dokument.publisering.kafka.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.nav.fia.dokument.publisering.domene.Dokument

@Serializable
data class DokumentKafkaDto(
    val referanseId: String,
    val type: Dokument.Type,
    val opprettetAv: String,
    val orgnr: String,
    val saksnummer: String,
    val samarbeidId: Int,
    val samarbeidNavn: String,
    val innhold: String,
    val sendtTilPublisering: LocalDateTime,
)
