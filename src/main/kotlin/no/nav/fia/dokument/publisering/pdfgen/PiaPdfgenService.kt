package no.nav.fia.dokument.publisering.pdfgen

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
import no.nav.fia.dokument.publisering.Feil
import no.nav.fia.dokument.publisering.NaisEnvironment
import no.nav.fia.dokument.publisering.http.HttpClient.client
import java.util.Base64

class PiaPdfgenService() {
    val piaPdfgenUrl = NaisEnvironment.Companion.piaPdfgenUrl

    private fun getHttpClient(): HttpClient {
        return client.config {}
    }

    suspend fun genererBase64DokumentPdf(spørreundersøkelsePdfDokumentDto: PdfDokumentDto)
        : Either<Feil, String> =
        when (NaisEnvironment.Companion.cluster) {
            Cluster.`prod-gcp`, Cluster.`dev-gcp` -> genererPdfDokument(
                pdfType = PdfType.BEHOVSVURDERING,
                json = Json.encodeToString<PdfDokumentDto>(spørreundersøkelsePdfDokumentDto),
            ).map { it.tilBase64() }

            else -> "".right()
        }


    private suspend fun genererPdfDokument(
        pdfType: PdfType,
        json: String,
    ): Either<Feil, ByteArray> {
        val httpClient = getHttpClient()
        val response: HttpResponse = httpClient.post {
            url("$piaPdfgenUrl/api/v1/genpdf/pia/${pdfType.type}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(json)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.readRawBytes().right()
            else -> Feil(
                "Klarte ikke å generere Pdf. Status: ${response.status}",
                httpStatusCode = response.status
            ).left()
        }
    }

    private fun ByteArray.tilBase64() = String(Base64.getEncoder().encode(this))
}
