package no.nav.fia.dokument.publisering

import ApplikasjonsHelse
import DokumentService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.db.DokumentRepository
import no.nav.fia.dokument.publisering.kafka.KafkaConfig
import no.nav.fia.dokument.publisering.kafka.KafkaKonsument
import no.nav.fia.dokument.publisering.kafka.KafkaTopics
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("no.nav.fia.dokument.publisering")

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()

    val dataSource = createDataSource(database = NaisEnvironment().database)
    runMigration(dataSource = dataSource)

    val dokumentRepository = DokumentRepository(dataSource)
    val dokumentService = DokumentService(dokumentRepository)

    val applikasjonsServer =
        embeddedServer(
            factory = Netty,
            port = 8080,
            host = "0.0.0.0",
            module = { fiaDokumentPubliseringApi(applikasjonsHelse = applikasjonsHelse, dokumentService = dokumentService) },
        )

    applikasjonsHelse.ready = true
    settOppKonsumenter(applikasjonsHelse = applikasjonsHelse, dataSource = dataSource)

    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Stopper applikasjonen fra shutdown hook")

            applikasjonsHelse.ready = false
            applikasjonsHelse.alive = false
            applikasjonsServer.stop(1000, 5000)
        },
    )
    applikasjonsServer.start(wait = true)
}

fun Application.fiaDokumentPubliseringApi(
    applikasjonsHelse: ApplikasjonsHelse,
    dokumentService: DokumentService,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CallLogging) {
        // TODO: best å fjerne denne i drift (unødvendig)
        format { call ->
            val httpMethod = call.request.httpMethod.value
            val uri = call.request.uri
            val status = call.response.status()
            "[CallLogging] Response status: $status, HTTP method: $httpMethod, Uri: $uri"
        }
    }

    configureSecurity(tokenxValidering = TokenxValidering())
    configureRouting(applikasjonsHelse = applikasjonsHelse, dokumentService = dokumentService)
}

private fun settOppKonsumenter(
    applikasjonsHelse: ApplikasjonsHelse,
    dataSource: DataSource,
) {
    val dokumentRepository = DokumentRepository(dataSource = dataSource)
    val dokumentService = DokumentService(dokumentRepository = dokumentRepository)
    val dokumentKonsument = KafkaKonsument(
        kafkaConfig = KafkaConfig(),
        kafkaTopic = KafkaTopics.DOKUMENT_PUBLISERING,
        applikasjonsHelse = applikasjonsHelse,
    ) {
        dokumentService.håndterKafkaMelding(it)
    }
    dokumentKonsument.startKonsument()
}
