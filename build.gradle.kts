plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("application")
}

group = "no.nav"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

val arrowCoreVersion = "2.2.3"
val kafkaClientsVersion = "4.3.1"
val kotestVersion = "6.2.1"
val kotlinVersion = "2.4.0"
val ktorVersion = "3.5.1"
val flywayVersion = "12.10.0"
val hikariCPVersion = "7.1.0"
val logbackVersion = "1.5.37"
val logstashLogbackEncoderVersion = "9.0"
val mockOAuth2ServerVersion = "5.0.2"
val mockServerVersion = "2.51.0"
val postgresqlVersion = "42.7.12"
val testcontainersVersion = "2.0.5"
val veraPdfVersion = "1.30.2"

dependencies {
    // -- ktor
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // -- logs
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    // -- DB
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.github.seratch:kotliquery:1.9.1")

    // -- div
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")

    // Kafka
    implementation("at.yawk.lz4:lz4-java:1.11.0")
    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion") {
        // "Fikser CVE-2025-12183 - lz4-java >1.8.1 har sårbar versjon (transitive dependency fra kafka-clients:4.1.0)"
        exclude("org.lz4", "lz4-java")
    }

    // Logg requests (kan sløyfes i vanlig drift)
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Funksjonelle operatorer
    implementation("io.arrow-kt:arrow-core:$arrowCoreVersion")

    // ----------- test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-kafka:$testcontainersVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    // Mockserver neolight
    testImplementation("software.xdev.mockserver:testcontainers:$mockServerVersion")
    testImplementation("software.xdev.mockserver:client:$mockServerVersion")
    // JWT utilities
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.9.1")
    // -- validere pdfa
    testImplementation("org.verapdf:validation-model:$veraPdfVersion")

    constraints {
        implementation("org.mozilla:rhino") {
            version { require("1.9.1") }
            because("versjonser < 1.8.1 har sårbarhet. inkludert i verapdf 1.28.2")
        }
        implementation("com.fasterxml.jackson.core:jackson-core") {
            version { require("2.22.0") }
            because("versjoner < 2.21.1 har sårbarhet. inkludert i ktor-server-auth:3.4.0")
        }
        implementation("tools.jackson.core:jackson-core") {
            version { require("3.2.0") }
            because("versjoner <= 3.1.0 har sårbarhet. inkludert i logstash-logback-encoder:9.0")
        }
        implementation("io.netty:netty-codec-http2") {
            version {
                require("4.2.15.Final")
            }
            because(
                "versjoner < 4.2.10.Final har sårbarhet. inkludert i ktor-server-netty-jvm:3.4.2",
            )
        }
        testImplementation("org.bouncycastle:bcprov-jdk18on") {
            version {
                require("1.84")
            }
            because(
                "versjoner < 1.84 har sårbarhet. inkludert i no.nav.security:mock-oauth2-server:3.0.3",
            )
        }
        testImplementation("org.bouncycastle:bcpkix-jdk18on") {
            version {
                require("1.84")
            }
            because(
                "versjoner < 1.84 har sårbarhet. inkludert i no.nav.security:mock-oauth2-server:3.0.3",
            )
        }
    }
}

tasks {
    test {
        dependsOn(installDist)
    }
}
