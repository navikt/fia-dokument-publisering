package no.nav.fia.dokument.publisering.api

import DokumentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.dokument.publisering.domene.Dokument
import tilDto

const val DOKUMENT_PUBLISERING_PATH = "/dokument"

fun Route.dokumentPubliseringRoutes(dokumentService: DokumentService) {
    get("$DOKUMENT_PUBLISERING_PATH/{orgnr}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(
            status = HttpStatusCode.BadRequest,
            message = "Ugyldig orgnr",
        )

        call.respond(
            status = HttpStatusCode.OK,
            message = dokumentService.hentDokumenter(orgnr = orgnr, status = Dokument.Status.PUBLISERT).tilDto(),
        )
    }
}
