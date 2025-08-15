package no.nav.fia.dokument.publisering.journalpost

import kotlinx.serialization.Serializable

enum class JournalpostType {
    UTGAAENDE,
}

enum class IdType {
    ORGNR,
}

enum class Sakstype {
    FAGSAK,
}

enum class FilType {
    PDFA,
    JSON,
}

enum class Variantformat {
    ARKIV,
    ORIGINAL,
}

enum class Kanal {
    NAV_NO_UTEN_VARSLING,
}

enum class JournalpostTema {
    IAR,
}

enum class FagsakSystem {
    FIA,
}

@Serializable
data class JournalpostDto(
    val eksternReferanseId: String,
    val tittel: String,
    val tema: JournalpostTema,
    val journalposttype: JournalpostType,
    val journalfoerendeEnhet: String,
    val kanal: Kanal,
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val sak: Sak,
    val dokumenter: List<JournalpostDokument>,
)

@Serializable
data class AvsenderMottaker(
    val idType: IdType,
    val id: String,
    val navn: String,
)

@Serializable
data class Bruker(
    val idType: IdType,
    val id: String,
)

@Serializable
data class Sak(
    val sakstype: Sakstype,
    val fagsakId: String,
    val fagsaksystem: FagsakSystem,
)

@Serializable
data class JournalpostDokument(
    val tittel: String,
    val dokumentvarianter: List<DokumentVariant>,
)

@Serializable
data class DokumentVariant(
    val filtype: FilType,
    val variantformat: Variantformat,
    val fysiskDokument: String,
)
