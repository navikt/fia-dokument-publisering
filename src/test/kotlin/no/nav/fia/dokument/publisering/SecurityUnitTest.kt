package no.nav.fia.dokument.publisering

import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.matchers.shouldBe
import no.nav.fia.dokument.publisering.Security.Companion.orgnrFraTilgangClaim
import kotlin.test.Test

class SecurityUnitTest {

    @Test
    fun `skal kunne parse custom claim`() {
        "read:dokument:311111111".orgnrFraTilgangClaim() shouldBe "311111111"
        "read:dokument:?".orgnrFraTilgangClaim() shouldBe "?"
    }

    @Test
    fun `skal kunne håndtere feil ved parsing av custom claim`() {
        shouldThrowMessage("Ugyldig tilgang claim: 'read:dokument:'. Mangler orgnr") {
            "read:dokument:".orgnrFraTilgangClaim()
        }

        shouldThrowMessage("Ugyldig tilgang claim: 'read:dokument'. Mangler orgnr") {
            "read:dokument".orgnrFraTilgangClaim()
        }

        shouldThrowMessage("Ugyldig tilgang claim: 'null'") {
            null.orgnrFraTilgangClaim()
        }

        shouldThrowMessage("Ugyldig tilgang claim: ''") {
            "".orgnrFraTilgangClaim()
        }
    }
}
