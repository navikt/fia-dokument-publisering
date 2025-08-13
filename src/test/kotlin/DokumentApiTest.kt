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
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.hentAllePubliserteDokumenter
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.hentEtPublisertDokument
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.withTokenXToken
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.withoutGyldigTokenXToken
import kotlin.test.Test

class DokumentApiTest {
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
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering(orgnr = "111111111")
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

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
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

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
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

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
