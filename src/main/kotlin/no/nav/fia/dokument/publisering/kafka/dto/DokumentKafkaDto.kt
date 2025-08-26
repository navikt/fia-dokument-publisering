package no.nav.fia.dokument.publisering.kafka.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.nav.fia.dokument.publisering.domene.Dokument

@Serializable
data class DokumentKafkaDto(
    val sak: SakDto,
    val virksomhet: VirksomhetDto,
    val samarbeid: SamarbeidDto,
    val referanseId: String,
    val type: Dokument.Type,
    val dokumentOpprettetAv: String,
    val innhold: SpørreundersøkelseInnholdIDokumentDto,
)

@Serializable
data class VirksomhetDto(
    val orgnummer: String,
    val navn: String,
)

@Serializable
data class SakDto(
    val saksnummer: String,
    val navenhet: NavEnhet,
)

@Serializable
data class NavEnhet(
    val enhetsnummer: String,
    val enhetsnavn: String,
)

@Serializable
data class SamarbeidDto(
    val id: Int,
    val navn: String,
)

@Serializable
data class SpørreundersøkelseInnholdIDokumentDto(
    val id: String,
    val fullførtTidspunkt: LocalDateTime,
    val spørsmålMedSvarPerTema: List<TemaResultatDto>,
)

@Serializable
data class TemaResultatDto(
    val id: Int,
    val navn: String,
    val spørsmålMedSvar: List<SpørsmålResultatDto>,
)

@Serializable
data class SpørsmålResultatDto(
    val id: String,
    val tekst: String,
    val flervalg: Boolean,
    val antallDeltakereSomHarSvart: Int,
    val svarListe: List<SvarResultatDto>,
    val kategori: String,
)

@Serializable
data class SvarResultatDto(
    val id: String,
    val tekst: String,
    val antallSvar: Int,
)
