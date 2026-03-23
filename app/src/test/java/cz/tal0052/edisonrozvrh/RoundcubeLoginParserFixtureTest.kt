package cz.tal0052.edisonrozvrh

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeLoginParser
import cz.tal0052.edisonrozvrh.data.repository.RoundcubeLoginRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class RoundcubeLoginParserFixtureTest {
    private fun loadFixture(name: String): String {
        val file = sequenceOf(
            File("fixtures/$name"),
            File("../fixtures/$name")
        ).firstOrNull { it.exists() } ?: error("Fixture not found: $name")

        return file.readText()
    }

    @Test
    fun parseMailLoginFixture_parsesLoginForm() {
        val html = loadFixture("mail_login.html")

        val loginPage = RoundcubeLoginParser.parse(html)

        assertNotNull("Login page should be parsed", loginPage)
        assertEquals("task-login action-none", loginPage!!.bodyClass)
        assertEquals("https://posta.vsb.cz/roundcube/?_task=login", loginPage.actionUrl)
        assertEquals("_user", loginPage.usernameFieldName)
        assertEquals("_pass", loginPage.passwordFieldName)
        assertEquals("Login", loginPage.submitLabel)
        assertEquals("login", loginPage.hiddenFields["_task"])
        assertEquals("login", loginPage.hiddenFields["_action"])
        assertEquals("_task=login", loginPage.hiddenFields["_url"])
    }

    @Test
    fun buildMailLoginRequest_fixture_preservesTokenAndAddsCredentials() {
        val html = loadFixture("mail_login.html")
        val loginPage = RoundcubeLoginParser.parse(html) ?: error("Login page should be parsed")

        val request = RoundcubeLoginRequestBuilder.build(
            loginPage = loginPage,
            username = " TAL0052 ",
            password = "secret-pass"
        )

        assertNotNull("Login request should be built", request)
        assertEquals("https://posta.vsb.cz/roundcube/?_task=login", request!!.actionUrl)
        assertEquals("https://posta.vsb.cz/roundcube/?_task=login", request.refererUrl)
        assertEquals("ESCuumfwZkqKdsffHBOopHP5PLv2zpHY", request.parameters["_token"])
        assertEquals("login", request.parameters["_task"])
        assertEquals("login", request.parameters["_action"])
        assertNotEquals("_default_", request.parameters["_timezone"])
        assertEquals("_task=login", request.parameters["_url"])
        assertEquals("TAL0052", request.parameters["_user"])
        assertEquals("secret-pass", request.parameters["_pass"])
    }

    @Test
    fun buildMailLoginRequest_blankCredentials_returnsNull() {
        val html = loadFixture("mail_login.html")
        val loginPage = RoundcubeLoginParser.parse(html) ?: error("Login page should be parsed")

        val request = RoundcubeLoginRequestBuilder.build(
            loginPage = loginPage,
            username = "   ",
            password = ""
        )

        assertNull("Blank credentials should not produce a request", request)
    }
}
