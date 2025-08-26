package no.nav.fia.dokument.publisering.eksport

import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.helper.TestContainerHelper
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.journalpost.AvsenderMottaker
import no.nav.fia.dokument.publisering.journalpost.Bruker
import no.nav.fia.dokument.publisering.journalpost.DokumentVariant
import no.nav.fia.dokument.publisering.journalpost.FagsakSystem
import no.nav.fia.dokument.publisering.journalpost.FilType
import no.nav.fia.dokument.publisering.journalpost.IdType
import no.nav.fia.dokument.publisering.journalpost.JournalpostDokument
import no.nav.fia.dokument.publisering.journalpost.JournalpostDto
import no.nav.fia.dokument.publisering.journalpost.JournalpostTema
import no.nav.fia.dokument.publisering.journalpost.JournalpostType
import no.nav.fia.dokument.publisering.journalpost.Kanal
import no.nav.fia.dokument.publisering.journalpost.Sak
import no.nav.fia.dokument.publisering.journalpost.Sakstype
import no.nav.fia.dokument.publisering.journalpost.Variantformat
import no.nav.fia.dokument.publisering.kafka.KafkaTopics
import no.nav.fia.dokument.publisering.kafka.KvitteringDto
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import org.junit.AfterClass
import org.junit.BeforeClass
import java.util.UUID
import kotlin.test.Test

class KvitteringProdusentTest {
    companion object {
        private val topic = KafkaTopics.DOKUMENT_KVITTERING
        private val konsument = kafkaContainer.nyKonsument(topic = topic)

        @BeforeClass
        @JvmStatic
        fun setUp() = konsument.subscribe(mutableListOf(topic.navnMedNamespace))

        @AfterClass
        @JvmStatic
        fun tearDown() {
            konsument.unsubscribe()
            konsument.close()
        }
    }

    @Test
    fun `skal sende kvittering for et dokument på kafka`() {
        TestContainerHelper.texasSidecarContainer.stubNaisTokenEndepunkt()
        val dokumentKafkaDto = kafkaContainer.etVilkårligDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"
        val orgnr = dokumentKafkaDto.virksomhet.orgnummer
        val virksomhetsnavn = dokumentKafkaDto.virksomhet.navn
        val navenhet = dokumentKafkaDto.sak.navenhet

        TestContainerHelper.dokarkivContainer.leggTilJournalPost(
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

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString<DokumentKafkaDto>(dokumentKafkaDto),
        )

        val dokumentId = TestContainerHelper.postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT dokument_id 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )

        runBlocking {
            kafkaContainer.ventOgKonsumerKafkaMeldinger(
                key = dokumentId,
                konsument = konsument,
                { meldinger ->
                    meldinger.forAtLeastOne { melding ->
                        val kvitteringDto = Json.decodeFromString<KvitteringDto>(melding)
                        kvitteringDto.dokumentId shouldBe dokumentId
                    }
                },
            )
        }
    }
}
