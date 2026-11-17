package app.vestari.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityTest {
    private fun acc(smtpPort: Int, smtpSecurity: String = "") = Account(
        "1", "a@b.c", "", "custom", "imap", 993, "smtp", smtpPort, smtpSecurity = smtpSecurity,
    )

    @Test
    fun oldAccountsKeepPortRule() {
        assertEquals("ssl", acc(465).smtpMode)
        assertEquals("starttls", acc(587).smtpMode)
    }

    @Test
    fun explicitChoiceWins() {
        assertEquals("ssl", acc(2087, "ssl").smtpMode)
        assertEquals("none", acc(25, "none").smtpMode)
    }

    @Test
    fun portHints() {
        assertEquals("ssl", securityForPort(465))
        assertEquals("starttls", securityForPort(587))
        assertEquals(null, securityForPort(2087))
    }
}
