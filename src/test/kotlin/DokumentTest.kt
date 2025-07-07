import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.kafkaContainer
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.postgresContainer
import no.nav.fia.dokument.publisering.kafka.dto.SpørreundersøkelseInnholdIDokumentDto
import org.postgresql.util.PGobject
import kotlin.test.Test

class DokumentTest {
    @Test
    fun `skal konsumere og lagre dokumenter`() {
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

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

    @Test
    fun `innhold lagres som gyldig JSON`() {
        val dokumentKafkaDto = kafkaContainer.etDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        val innhold = postgresContainer.hentEnkelKolonne<PGobject>(
            """
            SELECT innhold 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )
        val jsonValue: String = innhold.value!!
        val jsonInnhold = Json.decodeFromString<SpørreundersøkelseInnholdIDokumentDto>(jsonValue)
        jsonInnhold.spørreundersøkelseOpprettetAv shouldBe dokumentKafkaDto.innhold.spørreundersøkelseOpprettetAv
    }
}
