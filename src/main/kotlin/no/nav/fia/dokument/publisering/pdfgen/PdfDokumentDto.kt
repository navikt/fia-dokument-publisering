package no.nav.fia.dokument.publisering.pdfgen

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import no.nav.fia.dokument.publisering.kafka.dto.SakDto
import no.nav.fia.dokument.publisering.kafka.dto.SamarbeidDto
import no.nav.fia.dokument.publisering.kafka.dto.VirksomhetDto

@Serializable
data class PdfDokumentDto(
    val type: PdfType,
    val referanseId: String,
    val publiseringsdato: LocalDateTime,
    val virksomhet: VirksomhetDto,
    val sak: SakDto,
    val samarbeid: SamarbeidDto,
    val innhold: JsonObject,
)
