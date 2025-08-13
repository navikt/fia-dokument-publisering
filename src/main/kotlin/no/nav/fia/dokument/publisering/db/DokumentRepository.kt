package no.nav.fia.dokument.publisering.db

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.fia.dokument.publisering.domene.Dokument
import java.time.LocalDateTime.now
import java.util.UUID
import javax.sql.DataSource

class DokumentRepository(
    val dataSource: DataSource,
) {
    fun lagreDokument(dokument: Dokument) =
        using(sessionOf(dataSource)) { session ->
            session.run(
                action = queryOf(
                    statement =
                        """
                        INSERT INTO dokument (
                         dokument_id,
                         referanse_id,
                         type,
                         opprettet_av,
                         status,
                         orgnr,
                         saksnummer,
                         samarbeid_id,
                         samarbeid_navn,
                         innhold,
                         sendt_til_publisering
                        )
                        VALUES (
                         :dokumentId,
                         :referanseId,
                         :type,
                         :opprettetAv,
                         :status,
                         :orgnr,
                         :saksnummer,
                         :samarbeidId,
                         :samarbeidNavn,
                         :innhold::jsonb,
                         :sendtTilPublisering
                        )
                        ON CONFLICT (dokument_id) DO NOTHING
                        """.trimIndent(),
                    paramMap = mapOf(
                        "dokumentId" to dokument.dokumentId.toString(),
                        "referanseId" to dokument.referanseId.toString(),
                        "type" to dokument.type.name,
                        "opprettetAv" to dokument.opprettetAv,
                        "status" to dokument.status.name,
                        "orgnr" to dokument.orgnr,
                        "saksnummer" to dokument.saksnummer,
                        "samarbeidId" to dokument.samarbeidId,
                        "samarbeidNavn" to dokument.samarbeidNavn,
                        "innhold" to dokument.innhold,
                        "sendtTilPublisering" to dokument.sendtTilPublisering.toJavaLocalDateTime(),
                    ),
                ).asUpdate,
            )
        }

    fun hentDokumenter(
        orgnr: String,
        status: Dokument.Status,
    ): List<Dokument> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                action = queryOf(
                    statement =
                        """
                        SELECT *
                        FROM dokument
                        WHERE orgnr = :orgnr AND status = :status
                        """.trimIndent(),
                    paramMap = mapOf(
                        "orgnr" to orgnr,
                        "status" to status.name,
                    ),
                ).map { row ->
                    Dokument(
                        dokumentId = UUID.fromString(row.string("dokument_id")),
                        referanseId = UUID.fromString(row.string("referanse_id")),
                        type = Dokument.Type.valueOf(row.string("type")),
                        opprettetAv = row.string("opprettet_av"),
                        status = Dokument.Status.valueOf(row.string("status")),
                        orgnr = row.string("orgnr"),
                        saksnummer = row.string("saksnummer"),
                        samarbeidId = row.int("samarbeid_id"),
                        samarbeidNavn = row.string("samarbeid_navn"),
                        innhold = row.string("innhold"),
                        sendtTilPublisering = row.localDateTime("sendt_til_publisering").toKotlinLocalDateTime(),
                        opprettet = row.localDateTime("opprettet").toKotlinLocalDateTime(),
                        publisert = row.localDateTimeOrNull("publisert")?.toKotlinLocalDateTime(),
                        sistEndret = row.localDateTimeOrNull("sist_endret")?.toKotlinLocalDateTime(),
                    )
                }.asList,
            )
        }

    fun oppdaterDokument(
        dokumentId: UUID,
        status: Dokument.Status,
        journalpostId: String? = null,
        publisertDato: LocalDateTime? = null,
    ) = using(sessionOf(dataSource)) { session ->
        session.run(
            action = queryOf(
                statement = """
                   UPDATE dokument SET 
                     status = :status,
                     journalpost_id = :journalpostId,             
                     publisert = :publisert,
                     sist_endret = :sist_endret
                   WHERE dokument_id = :dokumentId
                """.trimIndent(),
                paramMap = mapOf(
                    "status" to status.name,
                    "dokumentId" to dokumentId.toString(),
                    "journalpostId" to journalpostId,
                    "publisert" to publisertDato?.toJavaLocalDateTime(),
                    "sist_endret" to now(),
                ),
            ).asUpdate,
        )
    }
}
