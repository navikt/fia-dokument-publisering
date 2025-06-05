package no.nav.fia.dokument.publisering

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/") {
            call.application.log.info("Hei loggen!")
            call.respondText("Hello World!")
        }
    }
}
