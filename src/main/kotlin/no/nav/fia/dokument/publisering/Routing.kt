package no.nav.fia.dokument.publisering

import ApplikasjonsHelse
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.fia.dokument.publisering.helse.helse

fun Application.configureRouting(applikasjonsHelse: ApplikasjonsHelse) {
    routing {
        helse(lever = { applikasjonsHelse.alive }, klar = { applikasjonsHelse.ready })
    }
}
