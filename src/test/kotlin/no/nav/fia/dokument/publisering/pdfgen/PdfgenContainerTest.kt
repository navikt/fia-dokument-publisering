package no.nav.fia.dokument.publisering.pdfgen

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.domene.SpørsmålResultatDto
import no.nav.fia.dokument.publisering.domene.SvarResultatDto
import no.nav.fia.dokument.publisering.domene.TemaResultatDto
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.pdfgenContainer
import no.nav.fia.dokument.publisering.kafka.dto.NavEnhet
import no.nav.fia.dokument.publisering.kafka.dto.SakDto
import no.nav.fia.dokument.publisering.kafka.dto.SamarbeidDto
import no.nav.fia.dokument.publisering.kafka.dto.VirksomhetDto
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class PdfgenContainerTest {
    @BeforeTest
    internal fun setup() = VeraGreenfieldFoundryProvider.initialise()

    @Test
    fun `valider at genererte pdf for samarbeidsplan er i pdf-a format`() {
        assertGenerertPdfErValid(
            pdfDokReferanseId = UUID.randomUUID().toString(),
            innholdTilDokument = getInnholdForSamarbeidsplan(samarbeidsplanReferanseId = UUID.randomUUID().toString()),
            PdfType.SAMARBEIDSPLAN
        )
    }

    @Test
    fun `valider at genererte pdf for behovsvurdering er i pdf-a format`() {
        assertGenerertPdfErValid(
            pdfDokReferanseId = UUID.randomUUID().toString(),
            innholdTilDokument = getInnholdForBehovsvurdering(behovsvurderingReferanseId = UUID.randomUUID().toString()),
            PdfType.BEHOVSVURDERING
        )
    }

    @Test
    fun `valider at genererte pdf for evaluering er i pdf-a format`() {
        assertGenerertPdfErValid(
            pdfDokReferanseId = UUID.randomUUID().toString(),
            innholdTilDokument = getInnholdForEvaluering(evalueringReferanseId = UUID.randomUUID().toString()),
            pdfType = PdfType.EVALUERING
        )
    }

    private fun assertGenerertPdfErValid(
        pdfDokReferanseId: String,
        innholdTilDokument: String,
        pdfType: PdfType,
    ) {
        runBlocking {
            val pdf = pdfgenContainer.hentDokumentPdf(
                dokument = PdfDokumentDto(
                    type = pdfType,
                    referanseId = pdfDokReferanseId,
                    publiseringsdato = LocalDateTime.parse("2025-07-02T10:52:03"),
                    sak = SakDto(
                        saksnummer = "01HXXPFZFNRKD41JWHAY321BEZ",
                        navenhet = NavEnhet(
                            enhetsnummer = "1234567",
                            enhetsnavn = "Oslo Arbeidslivssenter",
                        ),
                    ),
                    virksomhet = VirksomhetDto(
                        orgnummer = "987654321",
                        navn = "SIGEN HEVNGJERRIG TIGER AS",
                    ),
                    samarbeid = SamarbeidDto(
                        id = 12345,
                        navn = "Samarbeid Navn",
                    ),
                    innhold = Json.decodeFromString(innholdTilDokument),
                ),
            )
            val pdfaFlavour = PDFAFlavour.PDFA_2_U
            val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)
            Foundries.defaultInstance().createParser(ByteArrayInputStream(pdf)).use {
                assert(validator.validate(it).isCompliant)
            }
        }
    }

    fun getInnholdForBehovsvurdering(behovsvurderingReferanseId: String): String =
        Json.encodeToString(
            serializer = SpørreundersøkelseInnholdTilPdfGenDto.serializer(),
            value = SpørreundersøkelseInnholdTilPdfGenDto(
                id = behovsvurderingReferanseId,
                fullførtTidspunkt = LocalDateTime.parse("2025-07-01T15:44:11"),
                spørsmålMedSvarPerTema = listOf(
                    TemaResultatDto(
                        id = 19,
                        navn = "Partssamarbeid",
                        spørsmålMedSvar = listOf(
                            SpørsmålResultatDto(
                                id = "2e84469f-5224-4d85-9f8b-43965502df32",
                                tekst = "Hvordan opplever du samarbeidet med NAV?",
                                flervalg = false,
                                antallDeltakereSomHarSvart = 10,
                                svarListe = listOf(
                                    "Veldig bra",
                                    "Bra",
                                    "Nøytral",
                                    "Dårlig",
                                    "Veldig dårlig",
                                ).map { svar ->
                                    SvarResultatDto(
                                        id = "${UUID.randomUUID()}",
                                        tekst = svar,
                                        antallSvar = 2,
                                    )
                                },
                                kategori = "Generelt",
                            ),
                        ),
                    ),
                ),
            ),
        )

    fun getInnholdForSamarbeidsplan(samarbeidsplanReferanseId: String): String =
        Json.encodeToString(
            serializer = SpørreundersøkelseInnholdTilPdfGenDto.serializer(),
            value = SpørreundersøkelseInnholdTilPdfGenDto(
                id = samarbeidsplanReferanseId,
                fullførtTidspunkt = LocalDateTime.parse("2025-07-01T15:44:11"),
                spørsmålMedSvarPerTema = listOf(
                    TemaResultatDto(
                        id = 19,
                        navn = "Partssamarbeid",
                        spørsmålMedSvar = listOf(
                            SpørsmålResultatDto(
                                id = "2e84469f-5224-4d85-9f8b-43965502df32",
                                tekst = "Hvordan opplever du samarbeidet med NAV?",
                                flervalg = false,
                                antallDeltakereSomHarSvart = 10,
                                svarListe = listOf(
                                    "Veldig bra",
                                    "Bra",
                                    "Nøytral",
                                    "Dårlig",
                                    "Veldig dårlig",
                                ).map { svar ->
                                    SvarResultatDto(
                                        id = "${UUID.randomUUID()}",
                                        tekst = svar,
                                        antallSvar = 2,
                                    )
                                },
                                kategori = "Generelt",
                            ),
                        ),
                    ),
                ),
            ),
        )

    fun getInnholdForEvaluering(evalueringReferanseId: String): String =
        Json.encodeToString(
            serializer = SpørreundersøkelseInnholdTilPdfGenDto.serializer(),
            value = SpørreundersøkelseInnholdTilPdfGenDto(
                id = evalueringReferanseId,
                fullførtTidspunkt = LocalDateTime.parse("2025-07-01T15:44:11"),
                spørsmålMedSvarPerTema = listOf(
                    TemaResultatDto(
                        id = 19,
                        navn = "Partssamarbeid",
                        spørsmålMedSvar = listOf(
                            SpørsmålResultatDto(
                                id = "2e84469f-5224-4d85-9f8b-43965502df32",
                                tekst = "Hvordan opplever du at partssamarbeidet har utviklet seg i løpet av samarbeidsperioden?",
                                flervalg = false,
                                antallDeltakereSomHarSvart = 10,
                                svarListe = listOf(
                                    "Veldig bra",
                                    "Bra",
                                    "Nøytral",
                                    "Dårlig",
                                    "Veldig dårlig",
                                ).map { svar ->
                                    SvarResultatDto(
                                        id = "${UUID.randomUUID()}",
                                        tekst = svar,
                                        antallSvar = 2,
                                    )
                                },
                                kategori = "Generelt",
                            ),
                        ),
                    ),
                ),
            ),
        )

    @Serializable
    data class SpørreundersøkelseInnholdTilPdfGenDto(
        val id: String,
        val fullførtTidspunkt: LocalDateTime,
        val spørsmålMedSvarPerTema: List<TemaResultatDto>,
    )
}
