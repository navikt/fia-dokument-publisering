package no.nav.fia.dokument.publisering.kafka.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import no.nav.fia.dokument.publisering.domene.Dokument

/*
  Matcher med DokumentPubliseringMedInnhold i Lydia-api
 */
@Serializable
data class DokumentKafkaDto(
    val type: Dokument.Type,
    val virksomhet: VirksomhetDto,
    val sak: SakDto,
    val samarbeid: SamarbeidDto,
    val referanseId: String,
    val dokumentOpprettetAv: String,
    /* Json objekt 'innhold' skal være av to typer:
       - SamarbeidsplanInnholdIDokumentDto -> Samarbeidsplan
       - SpørreundersøkelseInnholdIDokumentDto -> Behovsvurdering (og senere Evaluering)
     */
    val innhold: JsonObject,
)

@Serializable
data class VirksomhetDto(
    val orgnummer: String,
    val navn: String,
)

@Serializable
data class SakDto(
    val saksnummer: String,
    val navenhet: NavEnhet,
)

@Serializable
data class NavEnhet(
    val enhetsnummer: String,
    val enhetsnavn: String,
)

@Serializable
data class SamarbeidDto(
    val id: Int,
    val navn: String,
)
