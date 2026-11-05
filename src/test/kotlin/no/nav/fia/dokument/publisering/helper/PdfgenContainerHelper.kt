package no.nav.fia.dokument.publisering.helper

import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.pdfgen.PdfDokumentDto
import no.nav.fia.dokument.publisering.pdfgen.PdfType
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.TimeZone
import kotlin.test.assertEquals

class PdfgenContainerHelper(
    network: Network,
    log: Logger,
) {
    private val networkAlias = "pia-pdfgen"
    private val port = 8080
    private val baseUrl = "http://$networkAlias:$port"

    // TODO: bruk en fullverdi release etter pia-pdfgen er merget
    val container: GenericContainer<*> = GenericContainer(DockerImageName.parse("ghcr.io/navikt/pia-pdfgen:v1.3.0"))
        .withNetwork(network)
        .withExposedPorts(port)
        .withNetworkAliases(networkAlias)
        .withCreateContainerCmdModifier { cmd -> cmd.withName("$networkAlias-${System.currentTimeMillis()}") }
        .waitingFor(
            Wait.forHttp("/internal/is_ready")
                .forPort(port)
                .forStatusCode(HttpStatusCode.OK.value)
                .withStartupTimeout(Duration.ofSeconds(60)),
        )
        .withLogConsumer(
            Slf4jLogConsumer(log)
                .withPrefix("pia-pdfgen")
                .withSeparateOutputStreams(),
        )
        .withEnv(
            mapOf(
                "TZ" to TimeZone.getDefault().id,
            ),
        )
        .apply { start() }

    fun envVars(): Map<String, String> =
        mapOf(
            "PIA_PDFGEN_URL" to baseUrl,
        )

    suspend fun hentDokumentPdf(dokument: PdfDokumentDto): ByteArray {
        val json = Json.encodeToString<PdfDokumentDto>(dokument)
        return hentPdf(pdfType = dokument.type, json = json)
    }

    private suspend fun hentPdf(
        pdfType: PdfType,
        json: String,
    ): ByteArray {
        val response = container.performPost(
            url = "/api/v1/genpdf/pia/${pdfType.pathIPiaPdfgen}",
            body = json,
            config = {
                header(HttpHeaders.ContentType, "application/json")
                timeout {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            },
        )
        assertEquals(HttpStatusCode.OK, response.status, "PDF generation failed")
        return response.readRawBytes()
    }
}
