plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "no.nav"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

val ktorVersion = "3.1.3"
val kotlinVersion = "2.1.20"
val logbackVersion = "1.5.18"
val logstashLogbackEncoderVersion = "8.1"
val kotestVersion = "5.9.1"
val testcontainersVersion = "1.21.0"
val mockServerVersion = "1.0.19"

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

    // Funksjonelle operatorer
    implementation("io.arrow-kt:arrow-core:2.1.2")

    // -- logs
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    // -- DB
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.8.0")
    implementation("com.github.seratch:kotliquery:1.9.1")

    // -- div
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.apache.kafka:kafka-clients:3.9.1")

    // Logg requests (kan sløyfes i vanlig drift)
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Funksjonelle operatorer
    implementation("io.arrow-kt:arrow-core:2.1.2")

    // ----------- test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:2.2.1")
    // Mockserver neolight
    testImplementation("software.xdev.mockserver:testcontainers:$mockServerVersion")
    testImplementation("software.xdev.mockserver:client:$mockServerVersion")
    // JWT utilities
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.3.1")
    // -- validere pdfa
    testImplementation("org.verapdf:validation-model:1.28.2")

    constraints {
        testImplementation("org.apache.commons:commons-compress") {
            version {
                require("1.27.1")
            }
            because("testcontainers har sårbar versjon")
        }
        testImplementation("commons-io:commons-io") {
            version {
                require("2.19.0")
            }
            because("testcontainers har sårbar versjon")
        }
    }
}

tasks {
    shadowJar {
        mergeServiceFiles()
        manifest {
            attributes("Main-Class" to "no.nav.fia.dokument.publisering.ApplicationKt")
        }
    }
    test {
        dependsOn(shadowJar)
    }
}
