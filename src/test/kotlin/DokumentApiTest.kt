import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.api.DokumentDto
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.hentDokumenterResponse
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.withTokenXToken
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.withoutGyldigTokenXToken
import kotlin.test.Test

class DokumentApiTest {
    @Test
    fun `Uinnlogget bruker får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = hentDokumenterResponse(
                orgnr = "123456789",
                config = withoutGyldigTokenXToken(),
            )
            response.status.value shouldBe 401
        }
    }

    @Test
    fun `skal hente alle dokumenter for en virksomhet`() {
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        runBlocking {
            val response = hentDokumenterResponse(
                orgnr = dokumentKafkaDto.virksomhet.orgnummer,
                config = withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    )
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

            val nyResponse = hentDokumenterResponse(
                orgnr = dokumentKafkaDto.virksomhet.orgnummer,
                config = withTokenXToken(
                    claims = mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    )
                ),
            )
            nyResponse.status.value shouldBe 200
            val oppfrisketListeAvDokumenter = Json.decodeFromString<List<DokumentDto>>(nyResponse.bodyAsText())
            oppfrisketListeAvDokumenter shouldHaveSize 1

        }
    }
}
