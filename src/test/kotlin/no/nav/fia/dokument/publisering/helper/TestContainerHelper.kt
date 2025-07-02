package no.nav.fia.dokument.publisering.helper

import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import no.nav.fia.dokument.publisering.api.DOKUMENT_PUBLSERING_PATH
import no.nav.fia.dokument.publisering.api.DokumentDto
import no.nav.fia.dokument.publisering.helper.TestContainerHelper.Companion.fiaDokumentPubliseringContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import java.time.Duration
import kotlin.io.path.Path

class TestContainerHelper {
    companion object {
        val log: Logger = LoggerFactory.getLogger(TestContainerHelper::class.java)
        private val network = Network.newNetwork()
        val postgresContainer = PostgresContainer(network = network)
        val kafkaContainer = KafkaContainer(network = network)

        val fiaDokumentPubliseringContainer =
            GenericContainer(
                ImageFromDockerfile().withDockerfile(Path("./Dockerfile")),
            )
                .dependsOn(postgresContainer.container, kafkaContainer.container)
                .withNetwork(network)
                .withExposedPorts(8080)
                .withLogConsumer(Slf4jLogConsumer(log).withPrefix("fia-dokument-publisering").withSeparateOutputStreams())
                .waitingFor(HttpWaitStrategy().forPath("/internal/isready").withStartupTimeout(Duration.ofSeconds(20)))
                .withEnv(
                    mapOf(
                        "NAIS_CLUSTER_NAME" to "lokal",
                    ).plus(
                        postgresContainer.envVars(),
                    ).plus(
                        kafkaContainer.getEnv(),
                    ),
                )
                .apply {
                    start()
                }
    }
}

infix fun GenericContainer<*>.shouldContainLog(regex: Regex) = logs shouldContain regex

suspend fun hentDokumenter(orgnr: String) =
    fiaDokumentPubliseringContainer.performGet(
        url = "$DOKUMENT_PUBLSERING_PATH/$orgnr",
    ).body<List<DokumentDto>>()
