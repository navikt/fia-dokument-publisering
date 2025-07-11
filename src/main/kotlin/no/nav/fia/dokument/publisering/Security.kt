package no.nav.fia.dokument.publisering

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import no.nav.fia.dokument.publisering.Security.Companion.CUSTOM_CLAIM_TILGANG_FIA
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

class Security() {

    companion object {
        const val CUSTOM_CLAIM_TILGANG_FIA = "tilgang_fia"

        fun ApplicationRequest.tilgangClaim() =
            call.principal<JWTPrincipal>()?.get(CUSTOM_CLAIM_TILGANG_FIA)
                ?: throw UgyldigForespørselException("'Tilgang claim' missing in JWT")

        fun String?.orgnrFraTilgangClaim(): String {
            // Eksempel på custom claim "tilgang_fia": "read:dokument:987654321"
            if (this.isNullOrBlank()) {
                throw UgyldigForespørselException("Ugyldig tilgang claim: '$this'")
            }

            val orgnr = try {
                this.split(":").getOrNull(2)
            } catch (e: Exception) {
                throw UgyldigForespørselException("Ugyldig tilgang claim: '$this', kunne ikke parse orgnr, pga '${e.message}'")
            }
            if (orgnr.isNullOrBlank()) {
                throw UgyldigForespørselException("Ugyldig tilgang claim: '$this'. Mangler orgnr")
            }
            return orgnr
        }
    }
}

fun Application.configureSecurity(tokenxValidering: TokenxValidering) {
    val tokenxJwkProvider = JwkProviderBuilder(URI(tokenxValidering.tokenxJwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val log = LoggerFactory.getLogger("no.nav.fia.dokument.configureSecurity")

    authentication {
        jwt(name = "tokenx") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(tokenxJwkProvider, issuer = tokenxValidering.tokenxIssuer) {
                acceptLeeway(tokenFortsattGyldigFørUtløpISekunder)
                withAudience(tokenxValidering.tokenxClientId)
                withClaim(CUSTOM_CLAIM_TILGANG_FIA) { claim: Claim, _: DecodedJWT ->
                    claim.asString().startsWith("read:dokument:")
                }
            }
            validate { token ->
                log.info("[DEBUG][TEMP] Validate claim 'tilgang_fia...': ${token.payload.getClaim(CUSTOM_CLAIM_TILGANG_FIA).asString()}")
                JWTPrincipal(token.payload)
            }
        }
    }
}
