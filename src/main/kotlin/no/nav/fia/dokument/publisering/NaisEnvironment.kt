package no.nav.fia.dokument.publisering

import no.nav.fia.dokument.publisering.NaisEnvironment.Companion.hentMiljøVariabel

class NaisEnvironment(
    val database: Database = Database(),
) {
    companion object {
        val cluster = Cluster.valueOf(hentMiljøVariabel("NAIS_CLUSTER_NAME", "prod-gcp"))

        fun hentMiljøVariabel(
            variabelNavn: String,
            defaultVerdi: String? = null,
        ) = System.getenv(variabelNavn) ?: defaultVerdi ?: throw RuntimeException("Mangler miljøvariabel $variabelNavn")
    }
}

class Database(
    val jdbcUrl: String = hentMiljøVariabel("NAIS_DATABASE_FIA_DOKUMENT_PUBLISERING_DB_JDBC_URL"),
)
