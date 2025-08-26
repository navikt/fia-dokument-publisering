package no.nav.fia.dokument.publisering.domene

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.helper.TestContainerHelper
import no.nav.fia.dokument.publisering.kafka.dto.SpørreundersøkelseInnholdIDokumentDto
import org.postgresql.util.PGobject
import kotlin.test.BeforeTest
import kotlin.test.Test

class DokumentTest {
    @BeforeTest
    fun setup() {
        TestContainerHelper.texasSidecarContainer.slettAlleStubs()
        TestContainerHelper.texasSidecarContainer.stubNaisTokenEndepunkt()
        TestContainerHelper.dokarkivContainer.slettAlleJournalposter()
    }

    @Test
    fun `skal konsumere og lagre dokumenter`() {
        val dokumentKafkaDto = TestContainerHelper.kafkaContainer.etVilkårligDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        TestContainerHelper.kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        TestContainerHelper.postgresContainer.hentEnkelKolonne<String>(
            """
            SELECT status 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        ) shouldBeIn Dokument.Status.entries.map { it.name }.toList()
    }

    @Test
    fun `innhold lagres som gyldig JSON`() {
        val dokumentKafkaDto = TestContainerHelper.kafkaContainer.etVilkårligDokumentTilPublisering()
        val nøkkel = "${dokumentKafkaDto.samarbeid.id}-${dokumentKafkaDto.referanseId}-${dokumentKafkaDto.type.name}"

        TestContainerHelper.kafkaContainer.sendMeldingPåKafka(
            nøkkel = nøkkel,
            melding = Json.encodeToString(dokumentKafkaDto),
        )

        val innhold = TestContainerHelper.postgresContainer.hentEnkelKolonne<PGobject>(
            """
            SELECT innhold 
            FROM dokument 
            WHERE referanse_id = '${dokumentKafkaDto.referanseId}'
            """.trimIndent(),
        )
        val jsonValue: String = innhold.value!!
        val jsonInnhold = Json.decodeFromString<SpørreundersøkelseInnholdIDokumentDto>(jsonValue)
        jsonInnhold.id shouldBe dokumentKafkaDto.innhold.id
    }
}
