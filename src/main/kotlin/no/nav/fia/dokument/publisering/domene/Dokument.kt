package no.nav.fia.dokument.publisering.domene

import kotlinx.datetime.LocalDateTime
import java.util.UUID

class Dokument(
    val dokumentId: UUID,
    val referanseId: UUID,
    val type: Type,
    val opprettetAv: String,
    val status: Status,
    val orgnr: String,
    val saksnummer: String,
    val samarbeidId: Int,
    val samarbeidNavn: String,
    val innhold: String,
    val sendtTilPublisering: LocalDateTime,
    val opprettet: LocalDateTime,
    val publisert: LocalDateTime?,
    val sistEndret: LocalDateTime?,
) {

    enum class Type {
        EVALUERING,
        BEHOVSVURDERING,
        SAMARBEIDSPLAN,
    }

    enum class Status {
        OPPRETTET,
        PUBLISERT,
    }
}
