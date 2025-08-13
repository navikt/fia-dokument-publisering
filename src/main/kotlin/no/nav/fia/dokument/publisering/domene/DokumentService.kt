import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.api.DokumentDto
import no.nav.fia.dokument.publisering.db.DokumentRepository
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.journalpost.JournalpostService
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import no.nav.fia.dokument.publisering.kafka.dto.SpørreundersøkelseInnholdIDokumentDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime.now
import java.util.UUID

class DokumentService(
    val dokumentRepository: DokumentRepository,
    val journalpostService: JournalpostService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun håndterKafkaMelding(kafkamelding: String) {
        val dokumentKafkaDto = json.decodeFromString<DokumentKafkaDto>(kafkamelding)

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
            val journalføringDato = now().toKotlinLocalDateTime()

            journalpostService.journalfør(
                lagretDokument = dokument,
                dokumentKafkaDto = dokumentKafkaDto,
                journalføringDato = journalføringDato,
            )
                .onLeft { feil ->
                    log.warn(
                        "Feil under journalføring av dokument med referanseId: '${dokumentKafkaDto.referanseId}'",
                        feil
                    )
                    dokumentRepository.oppdaterDokument(
                        dokumentId = dokument.dokumentId,
                        status = Dokument.Status.FEILET_JOURNALFØRING,
                    )
                }
                .onRight { journalpostResultat ->
                    log.info("Dokument med referanseId: '${dokumentKafkaDto.referanseId}' er journalført " +
                        "med journalpostId: ${journalpostResultat.journalpostId}")
                    dokumentRepository.oppdaterDokument(
                        dokumentId = dokument.dokumentId,
                        status = Dokument.Status.PUBLISERT,
                        journalpostId = journalpostResultat.journalpostId,
                        publisertDato = journalføringDato
                    )
                }
        }
    }

    fun hentDokumenter(
        orgnr: String,
        status: Dokument.Status,
    ): List<Dokument> = dokumentRepository.hentDokumenter(orgnr = orgnr, status = status)
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
        type = type.name,
        samarbeidNavn = samarbeidNavn,
        innhold = innhold,
    )
