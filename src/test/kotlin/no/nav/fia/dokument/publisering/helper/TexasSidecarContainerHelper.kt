package no.nav.fia.dokument.publisering.helper

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.fia.dokument.publisering.auth.EntraIdTokenAuthClient.IdentityProvider
import no.nav.fia.dokument.publisering.auth.EntraIdTokenAuthClient.TokenRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import software.xdev.mockserver.client.MockServerClient
import software.xdev.mockserver.model.Format
import software.xdev.mockserver.model.HttpRequest.request
import software.xdev.mockserver.model.HttpResponse.response
import software.xdev.mockserver.model.MediaType
import software.xdev.mockserver.model.RequestDefinition
import software.xdev.testcontainers.mockserver.containers.MockServerContainer
import software.xdev.testcontainers.mockserver.containers.MockServerContainer.PORT
import kotlin.also
import kotlin.apply
import kotlin.jvm.java
import kotlin.text.trimIndent
import kotlin.to

class TexasSidecarContainerHelper(
    network: Network = Network.newNetwork(),
    val logger: Logger = LoggerFactory.getLogger(TexasSidecarContainerHelper::class.java),
) {
    private val networkAlias = "texas-sidecar-container"
    private val port =
        PORT // mockserver default port er 1080 som MockServerContainer() eksponerer selv med "this.addExposedPort(1080);"
    private var mockServerClient: MockServerClient? = null

    val dockerImageName = DockerImageName.parse("xdevsoftware/mockserver:1.0.19")
    val container = MockServerContainer(dockerImageName)
        .withNetwork(network)
        .withNetworkAliases(networkAlias)
        // OBS: logging starter på level WARN i logback-test.xml (mockserver er veldig verbose),
        // -> skru ned til INFO ved feilsøking i testene
        .withLogConsumer(Slf4jLogConsumer(logger).withPrefix(networkAlias).withSeparateOutputStreams())
        .withEnv(
            mapOf(
                "MOCKSERVER_LIVENESS_HTTP_GET_PATH" to "/isRunning",
                "SERVER_PORT" to "$port",
                "TZ" to "Europe/Oslo",
            ),
        )
        .waitingFor(Wait.forHttp("/isRunning").forStatusCode(200))
        .apply {
            start()
        }.also {
            logger.info("Startet (mock) Texas sidecar container for network '${network.id}' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "NAIS_TOKEN_ENDPOINT" to "http://$networkAlias:$port/api/v1/token", // Endepunkt for å hente token
        )

    fun slettAlleStubs() {
        slettAlleExpectations()
    }

    private fun getMockServerClient(): MockServerClient {
        if (mockServerClient == null) {
            logger.info(
                "Oppretter MockServerClient i texasSidecar med host '${container.host}' og port '${
                    container.getMappedPort(port)
                }'",
            )
            mockServerClient = MockServerClient(
                container.host,
                container.getMappedPort(port),
            )
        }
        return mockServerClient!!
    }

    private fun slettAlleExpectations() {
        val client = getMockServerClient()
        val allExpectations = hentAlleExpectationIds()

        runBlocking {
            allExpectations.forEach { expectation ->
                client.clear(expectation.id)
            }
            logger.info("Funnet og slettet '${allExpectations.size}' aktive expectations")
        }
    }

    internal fun hentAlleExpectationIds(): List<Expectation> {
        val client = getMockServerClient()
        return runBlocking {
            val alleAktiveRequestDefinition: RequestDefinition? = null
            val activeExpectations = client.retrieveActiveExpectations(alleAktiveRequestDefinition, Format.JSON)
            Json.decodeFromString<List<Expectation>>(activeExpectations)
        }
    }

    internal fun stubNaisTokenEndepunkt(accessToken: String) {
        logger.info(
            "Lager stub for endepunkt /api/v1/token for uthenting av token fra Texas sidecar",
        )

        val client = getMockServerClient()
        runBlocking {
            client.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/api/v1/token")
                    .withBody(
                        Json.encodeToString(
                            TokenRequest(
                                identity_provider = IdentityProvider.azuread,
                                resource = null,
                                skip_cache = false,
                                target = "api://lokalt.teamdokumenthandtering.dokarkiv/.default",
                            ),
                        ),
                    ),
            ).respond(
                response().withBody(
                    """
                    {
                        "access_token": "$accessToken",
                        "expires_in": 3599,
                        "token_type": "Bearer"
                    }
                    """.trimIndent(),
                ).withContentType(MediaType.APPLICATION_JSON_UTF_8),
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonIgnoreUnknownKeys
    internal data class Expectation(
        val id: String,
    )
}
