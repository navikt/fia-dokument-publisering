package no.nav.fia.dokument.publisering

import no.nav.fia.dokument.publisering.NaisEnvironment.Companion.hentMiljøVariabel

class NaisEnvironment(
    val database: Database = Database(),
) {
    companion object {
        val cluster = Cluster.valueOf(hentMiljøVariabel("NAIS_CLUSTER_NAME", "prod-gcp"))

        // -- kafka
        val kafkaBrokers: String = hentMiljøVariabel("KAFKA_BROKERS")
        val kafkaTruststoreLocation: String = hentMiljøVariabel("KAFKA_TRUSTSTORE_PATH")
        val kafkaKeystoreLocation: String = hentMiljøVariabel("KAFKA_KEYSTORE_PATH")
        val kafkaCredstorePassword: String = hentMiljøVariabel("KAFKA_CREDSTORE_PASSWORD")

        fun hentMiljøVariabel(
            variabelNavn: String,
            defaultVerdi: String? = null,
        ) = System.getenv(variabelNavn) ?: defaultVerdi ?: throw RuntimeException("Mangler miljøvariabel $variabelNavn")
    }
}

class Database(
    val jdbcUrl: String = hentMiljøVariabel("NAIS_DATABASE_FIA_DOKUMENT_PUBLISERING_FIA_DOKUMENT_PUBLISERING_DB_JDBC_URL"),
)

class TokenxValidering(
    val tokenxIssuer: String = System.getenv("TOKEN_X_ISSUER"),
    val tokenxJwksUri: String = System.getenv("TOKEN_X_JWKS_URI"),
    val tokenxClientId: String = System.getenv("TOKEN_X_CLIENT_ID"),
    //val tokenxPrivateJwk: String = System.getenv("TOKEN_X_PRIVATE_JWK"),
    //val tokenXTokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT"),
)
