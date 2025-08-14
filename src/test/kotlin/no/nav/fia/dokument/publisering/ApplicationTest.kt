package no.nav.fia.dokument.publisering

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.fia.dokument.publisering.helper.TestContainerHelper
import kotlin.test.Test

class ApplicationTest {
    val client = HttpClient()

    @Test
    fun `appen svarer på readiness`() {
        runBlocking {
            val response = client.get(
                "http://localhost:${
                    TestContainerHelper.fiaDokumentPubliseringContainer.getMappedPort(8080)
                }/internal/isready",
            )
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `appen svarer på aliveness`() {
        runBlocking {
            val response = client.get(
                "http://localhost:${
                    TestContainerHelper.fiaDokumentPubliseringContainer.getMappedPort(8080)
                }/internal/isalive",
            )
            response.status shouldBe HttpStatusCode.OK
        }
    }
}
