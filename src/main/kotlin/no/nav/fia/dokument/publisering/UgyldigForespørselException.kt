package no.nav.fia.dokument.publisering

class UgyldigForespørselException(
    override val message: String = "Ugyldig forespørsel",
) : Exception(message)
