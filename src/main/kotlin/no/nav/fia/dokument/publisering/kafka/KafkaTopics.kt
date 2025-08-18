package no.nav.fia.dokument.publisering.kafka

import no.nav.fia.dokument.publisering.kafka.KafkaConfig.Companion.CLIENT_ID

enum class KafkaTopics(
    val navn: String,
    private val prefix: String = "pia",
) {
    DOKUMENT_PUBLISERING("dokument-publisering-v1"),
    DOKUMENT_KVITTERING("dokument-kvittering-v1"),
    ;

    val konsumentGruppe
        get() = "${navn}_$CLIENT_ID"

    val navnMedNamespace
        get() = "$prefix.$navn"
}
