package no.nav.fia.dokument.publisering

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

internal fun createDataSource(database: Database): DataSource =
    HikariDataSource().apply {
        jdbcUrl = database.jdbcUrl
        maximumPoolSize = 10
        minimumIdle = 1
        idleTimeout = 100000
        connectionTimeout = 100000
        maxLifetime = 300000
    }

internal fun runMigration(dataSource: DataSource) = getFlyway(dataSource = dataSource).migrate()

private fun getFlyway(dataSource: DataSource) =
    Flyway.configure()
        .validateMigrationNaming(true)
        .dataSource(dataSource).load()
