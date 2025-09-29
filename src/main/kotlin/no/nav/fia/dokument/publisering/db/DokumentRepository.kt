package no.nav.fia.dokument.publisering.db

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import kotliquery.Row
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
                        "innhold" to dokument.innhold.toString(),
                        "sendtTilPublisering" to dokument.sendtTilPublisering.toJavaLocalDateTime(),
                    ),
                ).asUpdate,
            )
        }

    fun hentEtPublisertDokument(dokumentId: UUID): Dokument? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                action = queryOf(
                    statement =
                        """
                        SELECT *
                        FROM dokument
                        WHERE dokument_id = :dokumentId AND status = :status
                        """.trimIndent(),
                    paramMap = mapOf(
                        "dokumentId" to dokumentId.toString(),
                        "status" to Dokument.Status.PUBLISERT.name,
                    ),
                ).map { row ->
                    row.tilDokument()
                }.asSingle,
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
                statement =
                    """
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

    private fun Row.tilDokument(): Dokument =
        Dokument(
            dokumentId = UUID.fromString(this.string("dokument_id")),
            referanseId = UUID.fromString(this.string("referanse_id")),
            type = Dokument.Type.valueOf(this.string("type")),
            opprettetAv = this.string("opprettet_av"),
            status = Dokument.Status.valueOf(this.string("status")),
            orgnr = this.string("orgnr"),
            saksnummer = this.string("saksnummer"),
            samarbeidId = this.int("samarbeid_id"),
            samarbeidNavn = this.string("samarbeid_navn"),
            innhold = Json.decodeFromString(this.string("innhold")),
            sendtTilPublisering = this.localDateTime("sendt_til_publisering").toKotlinLocalDateTime(),
            opprettet = this.localDateTime("opprettet").toKotlinLocalDateTime(),
            publisert = this.localDateTimeOrNull("publisert")?.toKotlinLocalDateTime(),
            sistEndret = this.localDateTimeOrNull("sist_endret")?.toKotlinLocalDateTime(),
        )
}
