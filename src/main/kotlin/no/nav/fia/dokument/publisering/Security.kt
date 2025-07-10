package no.nav.fia.dokument.publisering

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureSecurity(tokenxValidering: TokenxValidering) {
    val tokenxJwkProvider = JwkProviderBuilder(URI(tokenxValidering.tokenxJwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()


    authentication {
        jwt(name = "tokenx") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(tokenxJwkProvider, issuer = tokenxValidering.tokenxIssuer) {
                acceptLeeway(tokenFortsattGyldigFørUtløpISekunder)
                withAudience(tokenxValidering.tokenxClientId)
                withClaim("acr") { claim: Claim, _: DecodedJWT ->
                    claim.asString().equals("Level4") || claim.asString().equals("idporten-loa-high")
                }
                withClaimPresence("pid")
                withClaimPresence("tilgang_fia_ag")
                withClaim("tilgang_fia_ag") { claim: Claim, _: DecodedJWT ->
                    claim.asString().equals("read:dokument") // TODO: skal/kan vi legge til orgnr og sjekke mot URL parameter?
                }
            }
            validate { token ->
                println("[DEBUG] Validate claim 'tilgang_fia...': ${token.payload.getClaim("tilgang_fia_ag").asString()}")
                JWTPrincipal(token.payload)
            }
        }
    }
}
