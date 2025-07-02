package no.nav.fia.dokument.publisering.helper

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import kotlin.use

class PostgresContainer(
    network: Network,
) {
    private val containerAlias = "postgresql-container"
    private val dbNavn = "fia-dokument-publisering-db"

    val container = PostgreSQLContainer("postgres:17")
        .withNetwork(network)
        .withNetworkAliases(containerAlias)
        .withLogConsumer(
            Slf4jLogConsumer(TestContainerHelper.log).withPrefix(containerAlias).withSeparateOutputStreams(),
        )
        .withDatabaseName(dbNavn)
        .waitingFor(HostPortWaitStrategy())
        .apply {
            start()
        }

    private val dataSource = HikariDataSource().apply {
        jdbcUrl = container.jdbcUrl
        username = container.username
        password = container.password
    }

    fun envVars() =
        "NAIS_DATABASE_FIA_DOKUMENT_PUBLISERING_FIA_DOKUMENT_PUBLISERING_DB_JDBC_URL" to
            "jdbc:postgresql://$containerAlias:5432/$dbNavn?password=${container.password}&user=${container.username}"

    fun <T> hentEnkelKolonne(sql: String): T {
        dataSource.connection.use { connection ->
            val statement = connection.createStatement()
            statement.execute(sql)
            val rs = statement.resultSet
            rs.next()
            rs.row shouldBe 1
            return rs.getObject(1) as T
        }
    }

    fun performUpdate(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
        }
    }
}
