package no.nav.fia.dokument.publisering.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import no.nav.fia.dokument.publisering.Feil
import no.nav.fia.dokument.publisering.http.HttpClient.client
import org.slf4j.LoggerFactory

/*
   Klient til Entra-ID auth server (tidl. kjent som Azure-AD)
   Brukes for å hente maskin-til-maskin (M2M) tokens. Tokenet brukes ved kall til internt api-er.
 **/
class EntraIdTokenAuthClient(
    val tokenEndpoint: String,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun getHttpClient(): HttpClient {
        return client.config {}
    }

    suspend fun hentMaskinTilMaskinToken(
        scope: String,
    ): Either<Feil, TokenResponse> {
        val httpClient = getHttpClient()
        val response: HttpResponse = httpClient.post {
            url(tokenEndpoint)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody<TokenRequest>(
                TokenRequest(
                    identity_provider = IdentityProvider.azuread,
                    resource = null,
                    skip_cache = false,
                    target = scope,
                )
            )
        }

        return response.status.let { status ->
            when (status) {
                HttpStatusCode.OK -> {
                    response.body<TokenResponse>().right()
                }
                else -> {
                    log.error("Feil ved henting av M2M-token for scope '$scope', response status: '${response.status}'")
                    Feil(
                        "Klarte ikke å hente M2M-token for scope $scope",
                        httpStatusCode = status
                    ).left()
                }
            }
        }
    }

    @Serializable
    data class TokenRequest(
        val identity_provider: IdentityProvider,
        // Resource indicator for audience-restricted tokens (RFC 8707).
        val resource: String?,
        // Force renewal of token. Defaults to false if omitted.
        val skip_cache: Boolean?,
        // Scope or identifier for the target application.
        val target: String,
    )

    @Suppress("EnumEntryName")
    enum class IdentityProvider {
        azuread,
        tokenx,
        maskinporten,
        idporten,
    }
}

