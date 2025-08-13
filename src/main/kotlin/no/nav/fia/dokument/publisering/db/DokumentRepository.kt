package no.nav.fia.dokument.publisering.db

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.fia.dokument.publisering.domene.Dokument
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

    fun hentPubliserteDokumenter(orgnr: String): List<Dokument> =
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
                        "status" to Dokument.Status.PUBLISERT.name,
                    ),
                ).map { row ->
                    row.tilDokument()
                }.asList,
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
            innhold = this.string("innhold"),
            sendtTilPublisering = this.localDateTime("sendt_til_publisering").toKotlinLocalDateTime(),
            opprettet = this.localDateTime("opprettet").toKotlinLocalDateTime(),
            publisert = this.localDateTimeOrNull("publisert")?.toKotlinLocalDateTime(),
            sistEndret = this.localDateTimeOrNull("sist_endret")?.toKotlinLocalDateTime(),
        )
}
