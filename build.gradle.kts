plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

val ktorVersion = "3.1.3"
val kotlinVersion = "2.1.20"
val logbackVersion = "1.5.18"

dependencies {
    // -- ktor
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")

    // -- logs
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // ----------- test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

tasks {
    shadowJar {
        manifest {
            attributes("Main-Class" to "no.nav.fia.dokument.publisering.ApplicationKt")
        }
    }
}