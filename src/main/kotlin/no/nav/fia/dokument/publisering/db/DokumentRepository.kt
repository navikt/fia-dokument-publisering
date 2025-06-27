package no.nav.fia.dokument.publisering.db

import kotlinx.datetime.toJavaLocalDateTime
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.fia.dokument.publisering.domene.Dokument
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
}
