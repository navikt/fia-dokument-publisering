package no.nav.fia.dokument.publisering.helper

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.datetime.toKotlinLocalDateTime
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.kafka.KafkaTopics
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import no.nav.fia.dokument.publisering.kafka.dto.SakDto
import no.nav.fia.dokument.publisering.kafka.dto.SamarbeidDto
import no.nav.fia.dokument.publisering.kafka.dto.SpørreundersøkelseInnholdIDokumentDto
import no.nav.fia.dokument.publisering.kafka.dto.VirksomhetDto
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.LocalDateTime.now
import java.util.TimeZone
import java.util.UUID

class KafkaContainer(
    network: Network,
) {
    private val containerAlias = "kafka-container"
    private var kafkaProducer: KafkaProducer<String, String>
    private var adminClient: AdminClient

    val container = ConfluentKafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.8.2"),
    )
        .withNetwork(network)
        .withNetworkAliases(containerAlias)
        .withLogConsumer(
            Slf4jLogConsumer(TestContainerHelper.log).withPrefix(containerAlias).withSeparateOutputStreams(),
        ).withEnv(
            mutableMapOf(
                "KAFKA_LOG4J_LOGGERS" to "org.apache.kafka.image.loader.MetadataLoader=WARN",
                "KAFKA_AUTO_LEADER_REBALANCE_ENABLE" to "false",
                "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "1",
                "TZ" to TimeZone.getDefault().id,
            ),
        )
        .waitingFor(HostPortWaitStrategy())
        .apply {
            start()
            kafkaProducer = producer()
            adminClient = AdminClient.create(mapOf(BOOTSTRAP_SERVERS_CONFIG to this.bootstrapServers))
        }

    fun getEnv() =
        mapOf(
            "KAFKA_BROKERS" to "BROKER://$containerAlias:9093,PLAINTEXT://$containerAlias:9093",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_KEYSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",
        )

    fun sendMeldingPåKafka(
        nøkkel: String = "nøkkel",
        melding: String = "melding",
    ) = sendOgVent(nøkkel, melding, KafkaTopics.DOKUMENT_PUBLISERING)

    private fun sendOgVent(
        nøkkel: String,
        melding: String,
        topic: KafkaTopics,
    ) {
        runBlocking {
            val sendtMelding = kafkaProducer.send(ProducerRecord(topic.navnMedNamespace, nøkkel, melding)).get()
            ventTilKonsumert(
                konsumentGruppeId = topic.konsumentGruppe,
                recordMetadata = sendtMelding,
            )
        }
    }

    private suspend fun ventTilKonsumert(
        konsumentGruppeId: String,
        recordMetadata: RecordMetadata,
    ) = withTimeoutOrNull(Duration.ofSeconds(5)) {
        do {
            delay(timeMillis = 1L)
        } while (consumerSinOffset(
                consumerGroup = konsumentGruppeId,
                topic = recordMetadata.topic(),
            ) <= recordMetadata.offset()
        )
    }

    private fun consumerSinOffset(
        consumerGroup: String,
        topic: String,
    ): Long {
        val offsetMetadata = adminClient.listConsumerGroupOffsets(consumerGroup)
            .partitionsToOffsetAndMetadata().get()
        return offsetMetadata[offsetMetadata.keys.firstOrNull { it.topic().contains(topic) }]?.offset() ?: -1
    }

    private fun ConfluentKafkaContainer.producer(): KafkaProducer<String, String> =
        KafkaProducer(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to this.bootstrapServers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "PLAINTEXT",
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
                ProducerConfig.LINGER_MS_CONFIG to "0",
                ProducerConfig.RETRIES_CONFIG to "0",
                ProducerConfig.BATCH_SIZE_CONFIG to "1",
                SaslConfigs.SASL_MECHANISM to "PLAIN",
            ),
            StringSerializer(),
            StringSerializer(),
        )

    fun etDokumentTilPublisering(
        referanseId: UUID = UUID.randomUUID(),
        type: Dokument.Type = Dokument.Type.BEHOVSVURDERING,
        orgnr: String = "987654321",
    ): DokumentKafkaDto {
        val navIdent = "NavIdent"
        return DokumentKafkaDto(
            sak = SakDto(
                saksnummer = "01HPGQR1626B531V7BXEQK172M",
                navenhet = "NAV Enhet",
            ),
            virksomhet = VirksomhetDto(
                orgnummer = orgnr,
                navn = "Virksomhet 1",
            ),
            samarbeid = SamarbeidDto(
                id = 44,
                navn = "Samarbeid 1",
            ),
            referanseId = referanseId.toString(),
            type = type,
            dokumentOpprettetAv = navIdent,
            innhold = SpørreundersøkelseInnholdIDokumentDto(
                id = referanseId.toString(),
                spørreundersøkelseOpprettetAv = "X12345",
                fullførtTidspunkt = now().toKotlinLocalDateTime(),
                spørsmålMedSvarPerTema = emptyList(),
            ),
        )
    }
}
