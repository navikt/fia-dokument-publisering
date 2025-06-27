import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import kotlin.test.Test

class DokumentTest {
    @Test
    fun `skal konsumere og lagre dokumenter`() {
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeidId}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT status 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        ) shouldBe "OPPRETTET"
    }
}
