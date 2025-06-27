import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import no.nav.fia.dokument.publisering.db.DokumentRepository
import no.nav.fia.dokument.publisering.domene.Dokument
import no.nav.fia.dokument.publisering.kafka.dto.DokumentKafkaDto
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
}

fun DokumentKafkaDto.tilDomene(): Dokument =
    Dokument(
        dokumentId = UUID.randomUUID(),
        referanseId = UUID.fromString(referanseId),
        type = type,
        opprettetAv = opprettetAv,
        status = Dokument.Status.OPPRETTET,
        orgnr = orgnr,
        saksnummer = saksnummer,
        samarbeidId = samarbeidId,
        samarbeidNavn = samarbeidNavn,
        innhold = innhold,
        sendtTilPublisering = sendtTilPublisering,
        opprettet = LocalDateTime.now().toKotlinLocalDateTime(),
        publisert = null,
        sistEndret = null,
    )
