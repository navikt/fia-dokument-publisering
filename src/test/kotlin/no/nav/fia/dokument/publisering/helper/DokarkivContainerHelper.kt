package no.nav.fia.dokument.publisering.helper

import kotlin.also
import kotlin.apply
import kotlin.jvm.java
import kotlin.text.trimIndent
import kotlin.to
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.fia.dokument.publisering.journalpost.JournalpostDto
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

class DokarkivContainerHelper(
    network: Network = Network.newNetwork(),
    val logger: Logger = LoggerFactory.getLogger(DokarkivContainerHelper::class.java),
) {
    private val networkAlias = "dockarkiv-container"
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
            logger.info("Startet (mock) dokarkiv container for network '${network.id}' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "JOURNALPOST_V1_URL" to "http://$networkAlias:$port/rest/journalpostapi/v1/journalpost",
            "JOURNALPOST_SCOPE" to "api://lokalt.teamdokumenthandtering.dokarkiv/.default",
        )

    fun slettAlleJournalposter() {
        slettAlleExpectations()
    }


    private fun getMockServerClient(): MockServerClient {
        if (mockServerClient == null) {
            logger.info(
                "Oppretter MockServerClient i dockarkivContainer med host '${container.host}' og port '${
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

    internal fun leggTilJournalPost(
        journalpostDto: JournalpostDto,
        forventetJournalpostId: String = "467010363",
    ): String {
        logger.debug(
            "Legger til expectation til en journalpost i dokarkiv for fagsakId '${journalpostDto.sak.fagsakId}'",
        )

        val client = getMockServerClient()
        runBlocking {
            client.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/rest/journalpostapi/v1/journalpost")
                    .withQueryStringParameter("forsoekFerdigstill", "true")
            ).respond(
                response().withBody(
                    """
                        {
                          "dokumenter": [
                            {
                              "dokumentInfoId": "${journalpostDto.eksternReferanseId}"
                            }
                          ],
                          "journalpostId": "$forventetJournalpostId",
                          "journalpostferdigstilt": true,
                          "melding": "OK"
                        }
                    """.trimIndent(),
                ).withContentType(MediaType.APPLICATION_JSON_UTF_8),
            )
        }
        return forventetJournalpostId
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonIgnoreUnknownKeys
    internal data class Expectation(
        val id: String,
    )
}
