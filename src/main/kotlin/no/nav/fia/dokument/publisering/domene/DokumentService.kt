import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.api.DokumentDto
import no.nav.fia.dokument.publisering.db.DokumentRepository
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.journalpost.JournalpostService
import no.nav.fia.dokument.publisering.kafka.KvitteringDto
import no.nav.fia.dokument.publisering.kafka.KvitteringProdusent
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import no.nav.fia.dokument.publisering.kafka.dto.SpørreundersøkelseInnholdIDokumentDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime.now
import java.util.UUID
import kotlin.jvm.java

class DokumentService(
    val dokumentRepository: DokumentRepository,
    val journalpostService: JournalpostService,
    val kvitteringProdusent: KvitteringProdusent
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun håndterKafkaMelding(kafkamelding: String) {
        // -- TODO: fjern try/catch når feilende melgin er konsumert
        val dokumentKafkaDto = try {
            json.decodeFromString<DokumentKafkaDto>(kafkamelding)
        } catch (e: Exception) {
            log.error("Kunne ikke deserialisere kafkamelding", e)
            return
        }
        // --

        /*
          TODO: implement denne slik at det skal være mulig å committe meldingen i Kafka etter dokumentet er først opprettet.
           => Hvis det er feil ETTER 'Opprett dokument', så skal meldingen committe.
           - Opprett dokument (insert) --> produserer et dokumentId {if error IKKE commit Kafka meldingen}
           - POST til pdfgen (if error commit melding, status = FEILET_PDF_GENERERING eller status = PDF_GENERERT)
           - Maybe: Lagre generert PDF som en Blob i DB (if error commit melding, status = FEILET_PDF_GENERERING)
           - POST til journalpost (if error commit melding, STATUS: FEILET_JOURNALFØRING eller status = PUBLISERT)
         */
        log.info("Skal lagre et nytt dokument med referanseId: '${dokumentKafkaDto.referanseId}'")
        val dokument = dokumentKafkaDto.tilDomene()
        dokumentRepository.lagreDokument(dokument = dokument)

        runBlocking {
            try {
                val journalføringDato = now().toKotlinLocalDateTime()

                val journalpostResultat = journalpostService.journalfør(
                    lagretDokument = dokument,
                    dokumentKafkaDto = dokumentKafkaDto,
                    journalføringDato = journalføringDato,
                )
                log.info(
                    "Dokument med referanseId: '${dokumentKafkaDto.referanseId}' er journalført " +
                        "med journalpostId: ${journalpostResultat.journalpostId}",
                )
                dokumentRepository.oppdaterDokument(
                    dokumentId = dokument.dokumentId,
                    status = Dokument.Status.PUBLISERT,
                    journalpostId = journalpostResultat.journalpostId,
                    publisertDato = journalføringDato,
                )
                kvitteringProdusent.sendPåKafka(
                    KvitteringDto(
                        dokumentId = dokument.dokumentId.toString(),
                        referanseId = dokument.referanseId.toString(),
                        samarbeidId = dokument.samarbeidId,
                        journalpostId = journalpostResultat.journalpostId,
                        type = dokument.type.name,
                        publisertDato = journalføringDato,
                    )
                )

            } catch (e: Exception) {
                log.warn(
                    "Feil under journalføring av dokument med referanseId: '${dokumentKafkaDto.referanseId}'",
                    e,
                )
                dokumentRepository.oppdaterDokument(
                    dokumentId = dokument.dokumentId,
                    status = Dokument.Status.FEILET_JOURNALFØRING,
                )
            }
        }
    }

    fun hentPubliserteDokumenter(orgnr: String): List<Dokument> =
        dokumentRepository.hentPubliserteDokumenter(orgnr = orgnr)

    fun hentEtPublisertDokument(dokumentId: UUID): Either<Feil, Dokument> =
        dokumentRepository.hentEtPublisertDokument(dokumentId = dokumentId)?.right()
            ?: DokumentFeil.`fant ikke dokument`.left()
}

fun DokumentKafkaDto.tilDomene(): Dokument =
    Dokument(
        dokumentId = UUID.randomUUID(),
        referanseId = UUID.fromString(referanseId),
        type = type,
        opprettetAv = dokumentOpprettetAv,
        status = Dokument.Status.OPPRETTET,
        orgnr = virksomhet.orgnummer,
        saksnummer = sak.saksnummer,
        samarbeidId = samarbeid.id,
        samarbeidNavn = samarbeid.navn,
        innhold = Json.encodeToString(serializer = SpørreundersøkelseInnholdIDokumentDto.serializer(), value = innhold),
        sendtTilPublisering = now().toKotlinLocalDateTime(),
        opprettet = now().toKotlinLocalDateTime(),
        publisert = null,
        sistEndret = null,
    )

fun List<Dokument>.tilDto() = this.map { it.tilDto() }

fun Dokument.tilDto(): DokumentDto =
    DokumentDto(
        dokumentId = dokumentId.toString(),
        innhold = innhold,
    )

fun String.tilUUID(hvaErJeg: String): UUID =
    try {
        UUID.fromString(this)
    } catch (e: Exception) {
        throw IllegalArgumentException(
            "Kunne ikke konvertere '$this' til UUID for $hvaErJeg",
            e,
        )
    }

class Feil(
    val feilmelding: String,
    val httpStatusCode: HttpStatusCode,
)

object DokumentFeil {
    val `fant ikke dokument` = Feil(
        feilmelding = "Fant ikke dokument",
        httpStatusCode = HttpStatusCode.NotFound,
    )
}
