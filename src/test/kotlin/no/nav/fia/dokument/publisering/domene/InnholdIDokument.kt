package no.nav.fia.dokument.publisering.domene

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

interface InnholdIDokument {
    val id: String
}

// Samarbeidsplan

@Serializable
data class SamarbeidsplanInnholdIDokumentDto(
    override val id: String,
    val sistEndret: LocalDateTime,
    val sistPublisert: LocalDateTime?,
    val status: String,
    val temaer: List<PlanTemaDto>,
): InnholdIDokument

@Serializable
data class PlanTemaDto(
    val id: Int,
    val navn: String,
    val inkludert: Boolean,
    val undertemaer: List<PlanUnderTemaDto>,
)

@Serializable
data class PlanUnderTemaDto(
    val id: Int,
    val navn: String,
    val målsetning: String,
    val inkludert: Boolean,
    val status: String,
    val startDato: String,
    val sluttDato: String,
    val harAktiviteterISalesforce: Boolean,
)

// Spørreundersøkelse

@Serializable
data class SpørreundersøkelseInnholdIDokumentDto(
    override val id: String,
    val fullførtTidspunkt: LocalDateTime,
    val spørsmålMedSvarPerTema: List<TemaResultatDto>,
): InnholdIDokument

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
