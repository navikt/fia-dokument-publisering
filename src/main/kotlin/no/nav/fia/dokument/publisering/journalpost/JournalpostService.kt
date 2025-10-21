package no.nav.fia.dokument.publisering.journalpost

import arrow.core.getOrElse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.LocalDateTime
import no.nav.fia.dokument.publisering.NaisEnvironment
import no.nav.fia.dokument.publisering.auth.EntraIdTokenAuthClient
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.http.HttpClient.client
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import no.nav.fia.dokument.publisering.kafka.dto.SakDto
import no.nav.fia.dokument.publisering.kafka.dto.VirksomhetDto
import no.nav.fia.dokument.publisering.pdfgen.PdfDokumentDto
import no.nav.fia.dokument.publisering.pdfgen.PdfType
import no.nav.fia.dokument.publisering.pdfgen.PiaPdfgenService
import org.slf4j.LoggerFactory
import java.util.UUID

class JournalpostService(
    private val pdfgenService: PiaPdfgenService,
    private val entraIdTokenAuthClient: EntraIdTokenAuthClient,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val journalPostUrl = NaisEnvironment.journalpostUrl
    private val journalPostScope = NaisEnvironment.journalpostScope

    suspend fun journalfør(
        lagretDokument: Dokument,
        dokumentKafkaDto: DokumentKafkaDto,
        journalføringDato: LocalDateTime,
    ): JournalpostResultatDto {
        val base64EnkodetPdf = pdfgenService.genererBase64DokumentPdf(
            pdfDokumentDto = PdfDokumentDto(
                type = dokumentKafkaDto.type.tilPdfType(),
                referanseId = dokumentKafkaDto.referanseId,
                publiseringsdato = journalføringDato,
                sak = dokumentKafkaDto.sak,
                virksomhet = dokumentKafkaDto.virksomhet,
                samarbeid = dokumentKafkaDto.samarbeid,
                innhold = dokumentKafkaDto.innhold,
            ),
        )

        val journalpostDto = lagJournalpostDto(
            dokumentId = lagretDokument.dokumentId,
            virksomhetDto = dokumentKafkaDto.virksomhet,
            sakDto = dokumentKafkaDto.sak,
            journalpostTittel = dokumentKafkaDto.type.tittel(),
            dokumentTittel = dokumentKafkaDto.type.tittel(),
            pdf = base64EnkodetPdf,
        )
        return journalfør(
            journalpostDto = journalpostDto,
        )
    }

    private fun getAuthorizedHttpClient(scopeForAuthorization: String): HttpClient =
        client.config {
            install(Auth) {
                bearer {
                    loadTokens {
                        val exchangedToken = entraIdTokenAuthClient.hentMaskinTilMaskinToken(
                            scope = scopeForAuthorization,
                        ).getOrElse { feil ->
                            log.error("Klarte ikke å hente M2M-token for journalføring: '${feil.feilmelding}'")
                            throw RuntimeException("Token exchange feil")
                        }
                        BearerTokens(
                            accessToken = exchangedToken.access_token,
                            refreshToken = exchangedToken.access_token,
                        )
                    }
                }
            }
        }

    private fun lagJournalpostDto(
        dokumentId: UUID,
        virksomhetDto: VirksomhetDto,
        sakDto: SakDto,
        journalpostTittel: String,
        dokumentTittel: String,
        pdf: String,
    ): JournalpostDto {
        val journalpostDto = JournalpostDto(
            eksternReferanseId = dokumentId.toString(),
            tittel = journalpostTittel,
            tema = JournalpostTema.IAR,
            journalposttype = JournalpostType.UTGAAENDE,
            journalfoerendeEnhet = sakDto.navenhet.enhetsnummer,
            kanal = Kanal.NAV_NO_UTEN_VARSLING,
            avsenderMottaker = AvsenderMottaker(
                id = virksomhetDto.orgnummer,
                idType = IdType.ORGNR,
                navn = virksomhetDto.navn,
            ),
            bruker = Bruker(
                id = virksomhetDto.orgnummer,
                idType = IdType.ORGNR,
            ),
            sak = Sak(
                sakstype = Sakstype.FAGSAK,
                fagsakId = sakDto.saksnummer,
                fagsaksystem = FagsakSystem.FIA,
            ),
            dokumenter = listOf(
                JournalpostDokument(
                    tittel = dokumentTittel,
                    dokumentvarianter = listOf(
                        DokumentVariant(
                            filtype = FilType.PDFA,
                            variantformat = Variantformat.ARKIV,
                            fysiskDokument = pdf,
                        ),
                    ),
                ),
            ),
        )
        return journalpostDto
    }

    private suspend fun journalfør(journalpostDto: JournalpostDto): JournalpostResultatDto {
        val httpClient = getAuthorizedHttpClient(scopeForAuthorization = journalPostScope)
        val response: HttpResponse = httpClient.post {
            url(urlString = journalPostUrl)
            parameter(key = "forsoekFerdigstill", value = true)
            contentType(type = ContentType.Application.Json)
            accept(contentType = ContentType.Application.Json)
            setBody<JournalpostDto>(body = journalpostDto)
        }
        log.info(
            "Forsøk å journalføre et dokument med fagsakId ${journalpostDto.sak.fagsakId}, " +
                "på url ${response.request.url} ga status ${response.status}.",
        )

        return response.status.let { status ->
            when (status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> {
                    response.body<JournalpostResultatDto>()
                }

                else -> {
                    log.warn(
                        "Feil ved journalføring av dokument med fagsakId ${journalpostDto.sak.fagsakId}, fikk følgende status i response: ${response.status}",
                    )
                    throw RuntimeException("Feil ved journalføring av dokument med fagsakId")
                }
            }
        }
    }
}

private fun Dokument.Type.tittel(): String =
    when (this) {
        Dokument.Type.BEHOVSVURDERING -> "Behovsvurdering"
        Dokument.Type.SAMARBEIDSPLAN -> "Samarbeidsplan"
        Dokument.Type.EVALUERING -> "Evaluering"

        else -> {
            throw RuntimeException("Ukjent dokumenttype: $this")
        }
    }

private fun Dokument.Type.tilPdfType(): PdfType =
    when (this) {
        Dokument.Type.BEHOVSVURDERING -> PdfType.BEHOVSVURDERING
        Dokument.Type.SAMARBEIDSPLAN -> PdfType.SAMARBEIDSPLAN
        Dokument.Type.EVALUERING -> PdfType.EVALUERING

        else -> {
            throw RuntimeException("Ukjent dokumenttype: $this")
        }
    }
