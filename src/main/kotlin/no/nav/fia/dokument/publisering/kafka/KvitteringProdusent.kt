package no.nav.fia.dokument.publisering.kafka

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class KvitteringProdusent(
    kafka: KafkaConfig,
    topic: KafkaTopics = KafkaTopics.DOKUMENT_PUBLISERING
) : KafkaProdusent<Kvittering>(kafka = kafka, topic = topic) {
    override fun tilKafkaMelding(input: Kvittering): Pair<String, String> {
        return getKafkaMeldingKey(
            referanseId = input.referanseId,
            samarbeidId = input.samarbeidId,
            dokumentId = input.dokumentId,
            type = "BEHOVSVURDERING",
        ) to Json.encodeToString(input)
    }

    fun getKafkaMeldingKey(
        samarbeidId: Int,
        dokumentId: String,
        referanseId: String,
        type: String,
    ) = "$samarbeidId-$dokumentId-$referanseId-$type"
}

@Serializable
data class Kvittering(
    val referanseId: String,
    val samarbeidId: Int,
    val dokumentId: String,
    val journalpostId: String,
    val publisertDato: LocalDateTime,
)
