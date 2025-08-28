package no.nav.fia.dokument.publisering.api

import DokumentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tilDto
import tilUUID

const val DOKUMENT_PUBLISERING_PATH = "/dokument"

fun Route.dokumentPubliseringRoutes(dokumentService: DokumentService) {
    get("$DOKUMENT_PUBLISERING_PATH/orgnr/{orgnr}/dokumentId/{dokumentId}") {
        val orgnr = call.orgnr ?: return@get call.respond(
            status = HttpStatusCode.BadRequest,
            message = "Ugyldig orgnr",
        )
        val dokumentId = call.dokumentId ?: return@get call.respond(
            status = HttpStatusCode.BadRequest,
            message = "Ugyldig dokumentId",
        )

        dokumentService.hentEtPublisertDokument(dokumentId = dokumentId).map { dokument ->
            if (orgnr != dokument.orgnr) {
                return@map call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Dette dokumentet hører ikke til ditt orgnr",
                )
            }
            call.respond(
                status = HttpStatusCode.OK,
                message = dokument.tilDto(),
            )
        }.mapLeft { feil ->
            call.respond(status = feil.httpStatusCode, message = feil.feilmelding)
        }
    }
}

val ApplicationCall.orgnr get() = parameters["orgnr"]
val ApplicationCall.dokumentId get() = parameters["dokumentId"]?.tilUUID("dokumentId")
