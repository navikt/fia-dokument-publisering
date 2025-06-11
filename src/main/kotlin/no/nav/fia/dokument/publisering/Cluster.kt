package no.nav.fia.dokument.publisering

enum class Cluster {
    @Suppress("ktlint:standard:enum-entry-name-case")
    `prod-gcp`,

    @Suppress("ktlint:standard:enum-entry-name-case")
    `dev-gcp`,

    @Suppress("ktlint:standard:enum-entry-name-case")
    lokal,
}
