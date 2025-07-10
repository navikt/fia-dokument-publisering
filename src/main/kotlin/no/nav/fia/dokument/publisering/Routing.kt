package no.nav.fia.dokument.publisering

import ApplikasjonsHelse
import DokumentService
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import no.nav.fia.dokument.publisering.api.dokumentPubliseringRoutes
import no.nav.fia.dokument.publisering.helse.helse

fun Application.configureRouting(
    applikasjonsHelse: ApplikasjonsHelse,
    dokumentService: DokumentService,
) {
    routing {
        helse(lever = { applikasjonsHelse.alive }, klar = { applikasjonsHelse.ready })
        authenticate("tokenx") {
            // 'authenticate' validerer TokenX token + sjekker custom claim 'tilgang_fia_ag' med verdi 'read:dokument'
            dokumentPubliseringRoutes(dokumentService = dokumentService)
        }
    }
}
