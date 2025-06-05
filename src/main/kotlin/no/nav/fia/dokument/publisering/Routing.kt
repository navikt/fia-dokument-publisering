package no.nav.fia.dokument.publisering

import ApplikasjonsHelse
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.fia.dokument.publisering.helse.helse

fun Application.configureRouting(applikasjonsHelse: ApplikasjonsHelse) {
    routing {
        get("internal/isalive") {
            call.application.log.info("Hei loggen!")
            call.respondText("Hello World!")
        }

        get("internal/isready") {
            call.application.log.info("Hei loggen!")
            call.respondText("Hello World!")
        }

        helse(lever = { applikasjonsHelse.alive }, klar = { applikasjonsHelse.ready })
    }
}
