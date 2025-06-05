package no.nav.fia.dokument.publisering

import ApplikasjonsHelse
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.fia.dokument.publisering")

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()
    val applikasjonsServer =
        embeddedServer(
            factory = Netty,
            port = 8080,
            host = "0.0.0.0",
            module = { fiaDokumentPubliseringApi(applikasjonsHelse = applikasjonsHelse) },
        )

    applikasjonsHelse.ready = true

    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Stopper applikajsonen fra shutdown hook")

            applikasjonsHelse.ready = false
            applikasjonsHelse.alive = false
            applikasjonsServer.stop(1000, 5000)
        },
    )
    applikasjonsServer.start(wait = true)
}

fun Application.fiaDokumentPubliseringApi(applikasjonsHelse: ApplikasjonsHelse) {
    configureRouting(applikasjonsHelse = applikasjonsHelse)
}
