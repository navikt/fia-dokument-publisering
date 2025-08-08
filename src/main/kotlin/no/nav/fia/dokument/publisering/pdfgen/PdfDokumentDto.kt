package no.nav.fia.dokument.publisering.pdfgen

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.nav.fia.dokument.publisering.kafka.dto.SakDto
import no.nav.fia.dokument.publisering.kafka.dto.SamarbeidDto
import no.nav.fia.dokument.publisering.kafka.dto.TemaResultatDto
import no.nav.fia.dokument.publisering.kafka.dto.VirksomhetDto

@Serializable
data class PdfDokumentDto(
    val publiseringsdato: String,
    val sak: SakDto,
    val virksomhet: VirksomhetDto,
    val samarbeid: SamarbeidDto,
    val spørreundersøkelse: SpørreundersøkelseDto,
)

@Serializable
data class SpørreundersøkelseDto(
    val id: String,
    val fullførtTidspunkt: LocalDateTime,
    val innhold: ResultatDto,
)

@Serializable
data class ResultatDto(
    val id: String,
    val spørsmålMedSvarPerTema: List<TemaResultatDto>,
)
