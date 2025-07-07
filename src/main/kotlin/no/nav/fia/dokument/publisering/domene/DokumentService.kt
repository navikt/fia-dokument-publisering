import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.api.DokumentDto
import no.nav.fia.dokument.publisering.db.DokumentRepository
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
import no.nav.fia.dokument.publisering.kafka.dto.SpørreundersøkelseInnholdIDokumentDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DokumentService(
    val dokumentRepository: DokumentRepository,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun håndterKafkaMelding(kafkamelding: String) {
        val dokumentKafkaDto = json.decodeFromString<DokumentKafkaDto>(kafkamelding)

        log.info("Skal lagre et nytt dokument med referanseId: '${dokumentKafkaDto.referanseId}'")
        dokumentRepository.lagreDokument(dokument = dokumentKafkaDto.tilDomene())
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
        sendtTilPublisering = LocalDateTime.now().toKotlinLocalDateTime(),
        opprettet = LocalDateTime.now().toKotlinLocalDateTime(),
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
