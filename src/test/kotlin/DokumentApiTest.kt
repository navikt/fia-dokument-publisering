import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import no.nav.fia.dokument.publisering.helper.hentDokumenter
import kotlin.test.Test

class DokumentApiTest {
    @Test
    fun `skal hente alle dokumenter for en virksomhet`() {
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        runBlocking {
            hentDokumenter(dokumentKafkaDto.virksomhet.orgnummer) shouldHaveSize 0

            postgresContainer.performUpdate(
                """
                UPDATE dokument
                SET status = '${Dokument.Status.PUBLISERT}'
                WHERE referanse_id = '${dokumentKafkaDto.referanseId}' 
                    AND type = '${dokumentKafkaDto.type.name}'
                """.trimIndent(),
            )

            hentDokumenter(dokumentKafkaDto.virksomhet.orgnummer) shouldHaveSize 1
        }
    }
}
