package no.nav.fia.dokument.publisering.journalpost

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.dokarkivContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.lagEntraIdToken
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.texasSidecarContainer
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class JournalpostContainerTest {
    @BeforeTest
    internal fun setup() {
        texasSidecarContainer.slettAlleStubs()
        dokarkivContainer.slettAlleJournalposter()
    }

    @Test
    fun `kaller dokarkiv for å arkivere et dokument`() {
        val entraIdToken = lagEntraIdToken()
        texasSidecarContainer.stubNaisTokenEndepunkt(entraIdToken)
        val dokumentKafkaDto = kafkaContainer.etVilkårligDokumentTilPublisering()
        val orgnr = dokumentKafkaDto.virksomhet.orgnummer
        val virksomhetsnavn = dokumentKafkaDto.virksomhet.navn
        val navenhet = dokumentKafkaDto.sak.navenhet

        val forventetJournalpostId = dokarkivContainer.leggTilJournalPost(
            JournalpostDto(
                tittel = "Behovsvurdering",
                tema = JournalpostTema.IAR,
                bruker = Bruker(
                    id = orgnr,
                    idType = IdType.ORGNR,
                ),
                dokumenter = listOf(
                    JournalpostDokument(
                        tittel = "Behovsvurdering",
                        dokumentvarianter = listOf(
                            DokumentVariant(
                                filtype = FilType.PDFA,
                                variantformat = Variantformat.ARKIV,
                                fysiskDokument = "base64EncodedPdfContent",
                            ),
                        ),
                    ),
                ),
                journalfoerendeEnhet = navenhet.enhetsnummer,
                eksternReferanseId = UUID.randomUUID().toString(),
                sak = Sak(
                    sakstype = Sakstype.FAGSAK,
                    fagsakId = dokumentKafkaDto.sak.saksnummer,
                    fagsaksystem = FagsakSystem.FIA,
                ),
                kanal = Kanal.NAV_NO_UTEN_VARSLING,
                journalposttype = JournalpostType.UTGAAENDE,
                avsenderMottaker = AvsenderMottaker(
                    id = orgnr,
                    idType = IdType.ORGNR,
                    navn = virksomhetsnavn,
                ),
            ),
        )

        val nøkkelTilKafkaMelding =
            "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"
        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkelTilKafkaMelding,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT status 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        ) shouldBe "PUBLISERT"

        postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT journalpost_id 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        ) shouldBe forventetJournalpostId
    }
}
