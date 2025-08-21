package no.nav.fia.dokument.publisering.kafka

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class KvitteringProdusent(
    kafka: KafkaConfig,
    topic: KafkaTopics = KafkaTopics.DOKUMENT_KVITTERING
) : KafkaProdusent<KvitteringDto>(kafka = kafka, topic = topic) {
    override fun tilKafkaMelding(input: KvitteringDto): Pair<String, String> {
        return input.dokumentId to Json.encodeToString(input)
    }
}

@Serializable
data class KvitteringDto(
    val dokumentId: String,
    val referanseId: String,
    val samarbeidId: Int,
    val journalpostId: String,
    val type: String,
    val publisertDato: LocalDateTime,
)
