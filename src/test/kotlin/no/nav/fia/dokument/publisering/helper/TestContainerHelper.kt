package no.nav.fia.dokument.publisering.helper

import io.kotest.matchers.string.shouldContain
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import java.time.Duration
import kotlin.apply
import kotlin.io.path.Path
import kotlin.jvm.java

class TestContainerHelper {
    companion object {
        val log: Logger = LoggerFactory.getLogger(TestContainerHelper::class.java)
        private val network = Network.newNetwork()
        val postgresContainer = PostgresContainer(network = network)

        val fiaDokumentPubliseringContainer =
            GenericContainer(
                ImageFromDockerfile().withDockerfile(Path("./Dockerfile")),
            )
                .dependsOn(postgresContainer.container)
                .withNetwork(network)
                .withExposedPorts(8080)
                .withLogConsumer(Slf4jLogConsumer(log).withPrefix("fia-dokument-publisering").withSeparateOutputStreams())
                .waitingFor(HttpWaitStrategy().forPath("/internal/isready").withStartupTimeout(Duration.ofSeconds(20)))
                .withEnv(
                    mapOf(
                        "NAIS_CLUSTER_NAME" to "lokal",
                    ).plus(
                        postgresContainer.envVars(),
                    ),
                )
                .apply {
                    start()
                }
    }
}

infix fun GenericContainer<*>.shouldContainLog(regex: Regex) = logs shouldContain regex
