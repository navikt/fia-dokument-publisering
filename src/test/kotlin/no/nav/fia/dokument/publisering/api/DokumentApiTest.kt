package no.nav.fia.dokument.publisering.api

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.helper.TestContainerHelper
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
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import tilUUID
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class DokumentApiTest {
    @BeforeTest
    internal fun setup() {
        TestContainerHelper.texasSidecarContainer.slettAlleStubs()
        TestContainerHelper.dokarkivContainer.slettAlleJournalposter()
    }

    @Test
    fun `Uinnlogget bruker får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentEtPublisertDokument(
                orgnr = "123456789",
                dokumentId = UUID.randomUUID(),
                config = TestContainerHelper.withoutGyldigTokenXToken(),
            )
            response.status.value shouldBe 401
        }
    }

    @Test
    fun `skal returnere et publisert dokument (BEHOVSVURDERING) med innhold`() {
        val dokumentKafkaDto = TestContainerHelper.kafkaContainer.etVilkårligDokumentTilPublisering()
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

        TestContainerHelper.kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        val dokumentId = TestContainerHelper.postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT dokument_id 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )

        runBlocking {
            TestContainerHelper.postgresContainer.performUpdate(
                """
                UPDATE dokument
                SET status = '${Dokument.Status.PUBLISERT}'
                WHERE referanse_id = '${dokumentKafkaDto.referanseId}' 
                    AND type = '${dokumentKafkaDto.type.name}'
                """.trimIndent(),
            )

            val response = TestContainerHelper.hentEtPublisertDokument(
                dokumentId = dokumentId.tilUUID("dokumentId"),
                orgnr = orgnr,
                config = TestContainerHelper.withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )

            response.status shouldBe HttpStatusCode.OK

            val publisertDokument = response.body<DokumentDto>()
            publisertDokument.dokumentId shouldBe dokumentId
            publisertDokument.innhold shouldBe dokumentKafkaDto.innhold
        }
    }

    @Test
    fun `skal IKKE kunne hente et dokument som ikke er publisert`() {
        val dokumentKafkaDto = TestContainerHelper.kafkaContainer.etVilkårligDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        TestContainerHelper.kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        val dokumentId = TestContainerHelper.postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT dokument_id 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )

        runBlocking {
            TestContainerHelper.hentEtPublisertDokument(
                dokumentId = dokumentId.tilUUID("dokumentId"),
                orgnr = dokumentKafkaDto.virksomhet.orgnummer,
                config = TestContainerHelper.withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            ).status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `skal IKKE kunne hente dokument for et annet orgnr enn sitt eget`() {
        val dokumentKafkaDto = mockAltSomSkalTilForAtEtDokumentSkalBliPublisert("987654321")
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        TestContainerHelper.kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        runBlocking {
            val dokumentId = TestContainerHelper.postgresContainer.hentEnkelKolonne<String>(
                """
                SELECT dokument_id from dokument where referanse_id = '${dokumentKafkaDto.referanseId}'
                """.trimIndent(),
            )

            TestContainerHelper.hentEtPublisertDokument(
                dokumentId = dokumentId.tilUUID("dokumentId"),
                orgnr = "123456789",
                config = TestContainerHelper.withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            ).status shouldBe HttpStatusCode.BadRequest
        }
    }

    private fun mockAltSomSkalTilForAtEtDokumentSkalBliPublisert(orgnr: String): DokumentKafkaDto {
        TestContainerHelper.texasSidecarContainer.stubNaisTokenEndepunkt()
        val dokumentKafkaDto = TestContainerHelper.kafkaContainer.etVilkårligDokumentTilPublisering(orgnr = orgnr)

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
        return dokumentKafkaDto
    }
}
