package no.nav.fia.dokument.publisering.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.Cluster
import no.nav.fia.dokument.publisering.NaisEnvironment
import no.nav.fia.dokument.publisering.http.HttpClient.client
import java.util.Base64

class PiaPdfgenService {
    val piaPdfgenUrl = NaisEnvironment.piaPdfgenUrl

    private fun getHttpClient(): HttpClient = client.config {}

    suspend fun genererBase64DokumentPdf(spørreundersøkelsePdfDokumentDto: PdfDokumentDto): String =
        when (NaisEnvironment.cluster) {
            Cluster.`prod-gcp`, Cluster.`dev-gcp` -> genererPdfDokument(
                pdfType = PdfType.BEHOVSVURDERING,
                json = Json.encodeToString<PdfDokumentDto>(spørreundersøkelsePdfDokumentDto),
            ).tilBase64()
            else -> ""
        }

    private suspend fun genererPdfDokument(
        pdfType: PdfType,
        json: String,
    ): ByteArray {
        val httpClient = getHttpClient()
        val response: HttpResponse = httpClient.post {
            url("$piaPdfgenUrl/api/v1/genpdf/pia/${pdfType.type}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(json)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.readRawBytes()
            else -> throw RuntimeException("Klarte ikke å generere Pdf. Status: ${response.status}")
        }
    }

    private fun ByteArray.tilBase64() = String(Base64.getEncoder().encode(this))
}
