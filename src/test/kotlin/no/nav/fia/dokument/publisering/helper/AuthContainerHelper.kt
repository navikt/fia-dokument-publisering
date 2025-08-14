package no.nav.fia.dokument.publisering.helper

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.AuthorizationCode
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.UUID

class AuthContainerHelper(
    network: Network,
    log: Logger,
) {
    private val port = 6969
    private val networkalias = "authserver"
    private val baseEndpointUrl = "http://$networkalias:$port"
    private val config = OAuth2Config()

    companion object {
        const val FNR = "12345678901"
    }

    val container: GenericContainer<*> = GenericContainer(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:2.2.1"))
        .withNetwork(network)
        .waitingFor(Wait.forHttp("/default/.well-known/openid-configuration").forStatusCode(200))
        .withExposedPorts(port)
        .withNetworkAliases(networkalias)
        .withLogConsumer(
            Slf4jLogConsumer(log)
                .withPrefix("authContainer")
                .withSeparateOutputStreams(),
        )
        .withEnv(
            mapOf(
                "SERVER_PORT" to "$port",
                "TZ" to "Europe/Oslo",
            ),
        )
        .apply { start() }

    internal fun issueToken(
        issuerId: String,
        subject: String = UUID.randomUUID().toString(),
        audience: String,
        claims: Map<String, Any> = emptyMap(),
        expiry: Long = 3600,
    ): SignedJWT {
        val issuerUrl = "$baseEndpointUrl/$issuerId"
        val tokenCallback = DefaultOAuth2TokenCallback(
            issuerId,
            subject,
            JOSEObjectType.JWT.type,
            listOf(audience),
            claims,
            expiry,
        )

        val tokenRequest = TokenRequest(
            URI.create(baseEndpointUrl),
            ClientSecretBasic(ClientID(issuerId), Secret("secret")),
            AuthorizationCodeGrant(AuthorizationCode(FNR), URI.create("http://localhost")),
            Scope(audience),
        )
        return config.tokenProvider.accessToken(tokenRequest, issuerUrl.toHttpUrl(), tokenCallback, null)
    }

    fun envVars() =
        mapOf(
            "TOKEN_X_CLIENT_ID" to "tokenx:fia-dokument-publisering",
            "TOKEN_X_ISSUER" to "http://$networkalias:$port/tokenx",
            "TOKEN_X_JWKS_URI" to "http://$networkalias:$port/tokenx/jwks",
            "TOKEN_X_TOKEN_ENDPOINT" to "http://$networkalias:$port/tokenx/token",
            "TOKEN_X_PRIVATE_JWK" to
                """
                {
                    "p": "1sKc9CQFXJ5q14wGjk6bAhIaWBiM2ZJHNCLcME0P60q_dNaC7osoj0-zDTwUWdiREIiI2y3DAArAGNlhyZqZwDNumL08_pM-ePXVoqiZWZ87Ch8g8csx27yU_AsDj6h64qRpV07x_TOzXRJdP5iQm_IO3qjyul9qlnXyd2X9h3c",
                    "kty": "RSA",
                    "q": "xkS_rKKUfowRYmHfha4birMJMvrRZEBmvOPs9gerRUyIy32R36UT5f2B8xwycExivtZpnlz-YgBrglIpWWXX1gUtgLb4dV_YQNE4rABQjWoa62NJeCeaL5mOoVJ-6Xx2mgt9Tb9JdZVyfQuC9-s74ImgKyYaN8y7LcW7EqxNa60",
                    "d": "TUr875CxdUBnuufXfGe9WELPlLE2N4tVtHO85qrVuwn41CueKKk92bF6mK4fFF_oIP6Ja22B96i7d-AY5GtLcwIJA_HNy6ndYJCWiMX9GlDJ7Y2TyYXrk4YXpZQWI3x18X7wbDs0JX1eVsxs2VWhjzyEsJfEbp0cyagBIZR_GE_WecEahhBUV2eGl9qf0qL50MnckFOZhQErEpyr0XPTfjqktwpmjZkTdONyvKoJhXhm7bngFQHl63RX3fIElsYFsvMYNpAH_I5NZg76Va79txrfR7X0diG6XZ4Kc5iUXXL1ZFnqgijVOzUYfldDikxaXc5wKPL5Jbs2GBe1fB14eQ",
                    "e": "AQAB",
                    "use": "sig",
                    "kid": "tokenx",
                    "qi": "zNeG8JxnjxSlCWbRv2sHwld6tf1OtDKTimo4VbNdOqmrm8sSUkuM9h0mrH0ZUbC7Q1n0Cp-4T_Q82QVzKXX71bGSolTI7c6NCTnzQXgTEylMaHgv-9MIG1N4raxWemlOt_0ZgdTjwDWNPXfbbx0oyc4NBJVZpQH_KEXKirAY5aI",
                    "dp": "Pbe8B2V6rP1R0xQIpkjsvxGYxIx5neUt1UvXX4Il-waGMvuasRcI1vaejEUhzBgyyD-UpPhnu9FbF0kRkzB80wF03Sw1JSwHnhd4B8DQITNjcisz-ojckTuGzVAU--n9NrjtFQw4-v0qpKqsZaRgmpBbuZ1v9COLrCXFQo7q500",
                    "alg": "RS256",
                    "dq": "Ccu_xKHLwGzfNwMq7gnqJnIuFCy8R72-1bpVLNq4JZZgc91iZbBcSVK7Ju3PuCiuAEvLsB1cHC91IF062cXkYhijZOalY_c2Ug2ERUtGr5X8eoDPUnZyccOefm37A0I5Aedra3n2AS8_FtqIwAMJVFC4bylUxkkBPoO0eHm24Yk",
                    "n": "plQx4or1C_Xany-wjM7mPHB4CAJPk3oOEdDSKpTwJ2dzGji5tEq7dUxExyhFN8f0PUjBjXyPph0gmDWaJG64fnhSSwVI-8Tdf2PppuK4rdCtWSPLgZ_DJ2DruxHgeXgwvJnX1HRfqhJF2p4ClkRUiVXZKFOhRPMGVgg18fnV9fXz5C4JacP_fmh498ktEohwcL3Pbv5DI_po_i0OiyF_M-9Iic3Ss80j22hs1wsNBGEMHvofWs7sl3ufwxmUCIstnDNSat840-n21Q4GV2v4L2kpROUw6l4ZmqZxoGl7eRSDS_VC5rPQoQEZYfyCiq6o1W5p9UXnoQin1zn0lr5Iaw"
                }
                """.trimIndent(),
        )
}
