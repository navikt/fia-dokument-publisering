import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.api.DokumentDto
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.dokarkivContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.hentAllePubliserteDokumenter
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.hentEtPublisertDokument
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.lagEntraIdToken
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.texasSidecarContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.withTokenXToken
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.withoutGyldigTokenXToken
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
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class DokumentApiTest {
    @BeforeTest
    internal fun setup() {
        texasSidecarContainer.slettAlleStubs()
        dokarkivContainer.slettAlleJournalposter()
    }

    @Test
    fun `Uinnlogget bruker får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = hentAllePubliserteDokumenter(
                orgnr = "123456789",
                config = withoutGyldigTokenXToken(),
            )
            response.status.value shouldBe 401
        }
    }

    @Test
    fun `skal hente alle publiserte dokumenter for en virksomhet`() {
        val entraIdToken = lagEntraIdToken()
        texasSidecarContainer.stubNaisTokenEndepunkt(entraIdToken)
        val dokumentKafkaDto = kafkaContainer.etVilkårligDokumentTilPublisering(orgnr = "111111111")
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"
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

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        runBlocking {
            val response = hentAllePubliserteDokumenter(
                orgnr = dokumentKafkaDto.virksomhet.orgnummer,
                config = withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )
            response.status.value shouldBe 200
            val dokumenter = Json.decodeFromString<List<DokumentDto>>(response.bodyAsText())
            dokumenter shouldHaveSize 0

            postgresContainer.performUpdate(
                """
                UPDATE dokument
                SET status = '${Dokument.Status.PUBLISERT}'
                WHERE referanse_id = '${dokumentKafkaDto.referanseId}' 
                    AND type = '${dokumentKafkaDto.type.name}'
                """.trimIndent(),
            )

            val nyResponse = hentAllePubliserteDokumenter(
                orgnr = dokumentKafkaDto.virksomhet.orgnummer,
                config = withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )
            nyResponse.status.value shouldBe 200
            val oppfrisketListeAvDokumenter = Json.decodeFromString<List<DokumentDto>>(nyResponse.bodyAsText())
            oppfrisketListeAvDokumenter shouldHaveSize 1
        }
    }

    @Test
    fun `skal returnere et publisert dokument med innhold`() {
        val entraIdToken = lagEntraIdToken()
        texasSidecarContainer.stubNaisTokenEndepunkt(entraIdToken)
        val dokumentKafkaDto = kafkaContainer.etVilkårligDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"
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

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        val dokumentId = postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT dokument_id 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )

        runBlocking {
            postgresContainer.performUpdate(
                """
                UPDATE dokument
                SET status = '${Dokument.Status.PUBLISERT}'
                WHERE referanse_id = '${dokumentKafkaDto.referanseId}' 
                    AND type = '${dokumentKafkaDto.type.name}'
                """.trimIndent(),
            )

            val response = hentEtPublisertDokument(
                dokumentId = dokumentId.tilUUID("dokumentId"),
                config = withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )

            response.status shouldBe HttpStatusCode.OK

            val publisertDokument = response.body<DokumentDto>()

            publisertDokument.dokumentId shouldBe dokumentId
            publisertDokument.innhold shouldContain dokumentKafkaDto.referanseId
        }
    }

    @Test
    fun `skal IKKE kunne hente et dokument som ikke er publisert`() {
        val entraIdToken = lagEntraIdToken()
        texasSidecarContainer.stubNaisTokenEndepunkt(entraIdToken)
        val dokumentKafkaDto = kafkaContainer.etVilkårligDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"
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

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        val dokumentId = postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT dokument_id 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )

        runBlocking {
            hentEtPublisertDokument(
                dokumentId = dokumentId.tilUUID("dokumentId"),
                config = withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            ).status shouldBe HttpStatusCode.NotFound
        }
    }
}
