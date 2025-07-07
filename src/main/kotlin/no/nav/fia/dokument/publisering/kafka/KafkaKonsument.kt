package no.nav.fia.dokument.publisering.kafka

import ApplikasjonsHelse
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration

class KafkaKonsument(
    kafkaConfig: KafkaConfig,
    private val kafkaTopic: KafkaTopics,
    private val applikasjonsHelse: ApplikasjonsHelse,
    private val block: (String) -> Unit,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val konsument = KafkaConsumer(
        kafkaConfig.consumerProperties(kafkaTopic.konsumentGruppe),
        StringDeserializer(),
        StringDeserializer(),
    )

    @OptIn(DelicateCoroutinesApi::class)
    fun startKonsument() {
        GlobalScope.launch(Dispatchers.IO) {
            log.info("Starter kafka konsument for topic: ${kafkaTopic.navnMedNamespace}")
            while (applikasjonsHelse.ready) {
                try {
                    lyttPåTopic()
                } catch (e: Exception) {
                    log.error("Klarte ikke å starte konsument for topic: ${kafkaTopic.navnMedNamespace}. Forsøker igjen om 10 sekunder.", e)
                    konsument.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private fun lyttPåTopic() {
        konsument.subscribe(listOf(kafkaTopic.navnMedNamespace))
        log.info("Lytter nå på topic: ${kafkaTopic.navnMedNamespace}")
        while (applikasjonsHelse.ready) {
            konsument.poll(Duration.ofMillis(10))
                .map { melding ->
                    log.info("Mottok kafkamelding med nøkkel: ${melding.key()}")
                    melding.value()
                }.forEach { dokument ->
                    block(dokument)
                }
            konsument.commitSync()
        }
    }
}
