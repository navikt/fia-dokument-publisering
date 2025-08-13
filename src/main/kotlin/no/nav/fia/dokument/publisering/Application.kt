package no.nav.fia.dokument.publisering

import ApplikasjonsHelse
import DokumentService
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.auth.EntraIdTokenAuthClient
import no.nav.fia.dokument.publisering.db.DokumentRepository
import no.nav.fia.dokument.publisering.journalpost.JournalpostService
import no.nav.fia.dokument.publisering.kafka.KafkaConfig
import no.nav.fia.dokument.publisering.kafka.KafkaKonsument
import no.nav.fia.dokument.publisering.kafka.KafkaTopics
import no.nav.fia.dokument.publisering.pdfgen.PiaPdfgenService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.fia.dokument.publisering")

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()

    val dataSource = createDataSource(database = NaisEnvironment().database)
    runMigration(dataSource = dataSource)

    val entraIdTokenAuthClient = EntraIdTokenAuthClient(tokenEndpoint = NaisEnvironment.Companion.tokenEndpoint)
    val pdfgenService = PiaPdfgenService()
    val journalpostService = JournalpostService(
        pdfgenService = pdfgenService,
        entraIdTokenAuthClient = entraIdTokenAuthClient,
    )
    val dokumentRepository = DokumentRepository(dataSource)
    val dokumentService = DokumentService(
        dokumentRepository = dokumentRepository,
        journalpostService = journalpostService,
    )

    val applikasjonsServer =
        embeddedServer(
            factory = Netty,
            port = 8080,
            host = "0.0.0.0",
            module = {
                fiaDokumentPubliseringApi(
                    applikasjonsHelse = applikasjonsHelse,
                    dokumentService = dokumentService
                )
            },
        )

    applikasjonsHelse.ready = true
    settOppKonsumenter(
        applikasjonsHelse = applikasjonsHelse,
        dokumentService = dokumentService,
    )

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
        filter { call ->
            call.request.path().startsWith("/dokument")
        }
        format { call ->
            val httpMethod = call.request.httpMethod.value
            val uri = call.request.uri
            val status = call.response.status()
            val harEnBearer: Boolean = call.request.header(HttpHeaders.Authorization)?.isNotBlank() == true
            "[CallLogging] Response status: $status, HTTP method: $httpMethod, Uri: $uri, med bearer (true/false): $harEnBearer"
        }
    }

    configureSecurity(tokenxValidering = TokenxValidering())
    configureRouting(applikasjonsHelse = applikasjonsHelse, dokumentService = dokumentService)
}

private fun settOppKonsumenter(
    applikasjonsHelse: ApplikasjonsHelse,
    dokumentService: DokumentService,
) {

    val dokumentKonsument = KafkaKonsument(
        kafkaConfig = KafkaConfig(),
        kafkaTopic = KafkaTopics.DOKUMENT_PUBLISERING,
        applikasjonsHelse = applikasjonsHelse,
    ) {
        dokumentService.håndterKafkaMelding(it)
    }
    dokumentKonsument.startKonsument()
}
