package no.nav.fia.dokument.publisering

import io.ktor.http.HttpStatusCode

data class Feil(
    val feilmelding: String,
    val httpStatusCode: HttpStatusCode,
)
