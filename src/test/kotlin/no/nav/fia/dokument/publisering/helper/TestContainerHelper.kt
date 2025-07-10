package no.nav.fia.dokument.publisering.helper

import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import no.nav.fia.dokument.publisering.api.DOKUMENT_PUBLISERING_PATH
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
        val authContainerHelper = AuthContainerHelper(network = network, log = log)
        val postgresContainer = PostgresContainer(network = network)
        val kafkaContainer = KafkaContainer(network = network)

        val fiaDokumentPubliseringContainer =
            GenericContainer(
                ImageFromDockerfile().withDockerfile(Path("./Dockerfile")),
            )
                .dependsOn(
                    authContainerHelper.container,
                    postgresContainer.container,
                    kafkaContainer.container
                )
                .withNetwork(network)
                .withExposedPorts(8080)
                .withLogConsumer(
                    Slf4jLogConsumer(log).withPrefix("fia-dokument-publisering").withSeparateOutputStreams()
                )
                .waitingFor(HttpWaitStrategy().forPath("/internal/isready").withStartupTimeout(Duration.ofSeconds(20)))
                .withEnv(
                    mapOf(
                        "NAIS_CLUSTER_NAME" to "lokal",
                    ).plus(
                        authContainerHelper.envVars(),
                    ).plus(
                        postgresContainer.envVars(),
                    ).plus(
                        kafkaContainer.getEnv(),
                    ),
                )
                .apply {
                    start()
                }


        suspend fun hentDokumenterResponse(orgnr: String, config: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
            fiaDokumentPubliseringContainer.performGet(
                url = "$DOKUMENT_PUBLISERING_PATH/$orgnr",
                config = config,
            )

        internal fun withTokenXToken(claims: Map<String, String>): HttpRequestBuilder.() -> Unit {
            return {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${tokenXAccessToken(claims = claims).serialize()}"
                )
            }
        }

        val ikkeGyldigJwtToken =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.KMUFsIDTnFmyG3nMiGM6H9FNFUROf3wh7SmqJp-QV30"
        internal fun withoutGyldigTokenXToken(): HttpRequestBuilder.() -> Unit =
            {
                header(HttpHeaders.Authorization, "Bearer $ikkeGyldigJwtToken")
            }


        private fun tokenXAccessToken(
            subject: String = "123",
            audience: String = "tokenx:fia-dokument-publisering",
            claims: Map<String, String>,
        ) = authContainerHelper.issueToken(
            subject = subject,
            audience = audience,
            claims = claims,
            issuerId = "tokenx",
        )
    }
}

infix fun GenericContainer<*>.shouldContainLog(regex: Regex) = logs shouldContain regex

