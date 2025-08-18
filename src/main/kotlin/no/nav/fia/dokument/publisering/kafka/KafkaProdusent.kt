package no.nav.fia.dokument.publisering.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

abstract class KafkaProdusent<T>(
    protected val kafka: KafkaConfig,
    protected val topic: KafkaTopics,
    protected val clientId: String = topic.konsumentGruppe,
) {
    private val produsent: KafkaProducer<String, String> = KafkaProducer(kafka.producerProperties(clientId = clientId))

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                produsent.close()
            },
        )
    }

    fun sendMelding(
        nøkkel: String,
        verdi: String,
    ) {
        produsent.send(
            ProducerRecord(
                topic.navn,
                nøkkel,
                verdi,
            ),
        )
    }

    protected abstract fun tilKafkaMelding(input: T): Pair<String, String>

    fun sendPåKafka(input: T) {
        val (key, value) = tilKafkaMelding(input)
        sendMelding(key, value)
    }
}
