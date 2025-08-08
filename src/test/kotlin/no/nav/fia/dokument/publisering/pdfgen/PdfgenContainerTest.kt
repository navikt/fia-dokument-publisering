package no.nav.fia.dokument.publisering.pdfgen

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.pdfgenContainer
import no.nav.fia.dokument.publisering.kafka.dto.SakDto
import no.nav.fia.dokument.publisering.kafka.dto.SamarbeidDto
import no.nav.fia.dokument.publisering.kafka.dto.SpørsmålResultatDto
import no.nav.fia.dokument.publisering.kafka.dto.SvarResultatDto
import no.nav.fia.dokument.publisering.kafka.dto.TemaResultatDto
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
    fun `valider at genererte pdfer er i pdf-a format`() {
        runBlocking {
            val pdf = pdfgenContainer.hentDokumentPdf(
                dokument = PdfDokumentDto(
                    publiseringsdato = "2025-07-02T10:52:03Z",
                    sak = SakDto(
                        saksnummer = "01HXXPFZFNRKD41JWHAY321BEZ",
                        navenhet = "Oslo Arbeidslivssenter",
                    ),
                    virksomhet = VirksomhetDto(
                        orgnummer = "987654321",
                        navn = "SIGEN HEVNGJERRIG TIGER AS",
                    ),
                    samarbeid = SamarbeidDto(
                        id = 12345,
                        navn = "Samarbeid Navn",
                    ),
                    spørreundersøkelse = SpørreundersøkelseDto(
                        id = "2e84469f-5224-4d85-9f8b-43965502df32",
                        fullførtTidspunkt = LocalDateTime.parse("2025-07-01T15:44:11"),
                        innhold = ResultatDto(
                            id = "2e84469f-5224-4d85-9f8b-43965502df32",
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
                                                "Veldig dårlig"
                                            ).map { svar ->
                                                SvarResultatDto(
                                                    id = "${UUID.randomUUID()}",
                                                    tekst = svar,
                                                    antallSvar = 2
                                                )
                                            },
                                            kategori = "Generelt"
                                        )
                                    ),
                                ),
                            )
                        )
                    )
                )
            )
            val pdfaFlavour = PDFAFlavour.PDFA_2_U
            val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)
            Foundries.defaultInstance().createParser(ByteArrayInputStream(pdf)).use {
                assert(validator.validate(it).isCompliant)
            }
        }
    }
}
