package cz.tal0052.edisonrozvrh.data.parser

import org.jsoup.Jsoup

data class RoundcubeLoginPage(
    val title: String,
    val bodyClass: String,
    val actionUrl: String,
    val usernameFieldName: String,
    val passwordFieldName: String,
    val submitLabel: String,
    val hiddenFields: Map<String, String>
)

object RoundcubeLoginParser {

    fun parse(html: String): RoundcubeLoginPage? {
        val doc = Jsoup.parse(html, "https://posta.vsb.cz")
        val form = doc.selectFirst("form#login-form, form:has(input[type=password])") ?: return null
        val usernameField = form.selectFirst(
            "input[name=_user], input[id=rcmloginuser], input[type=text], input[type=email]"
        ) ?: return null
        val passwordField = form.selectFirst(
            "input[name=_pass], input[id=rcmloginpwd], input[type=password]"
        ) ?: return null
        val submitButton = form.selectFirst("button[type=submit], input[type=submit]")

        val hiddenFields = linkedMapOf<String, String>()
        form.select("input[type=hidden][name]").forEach { input ->
            val name = input.attr("name").trim()
            if (name.isNotBlank()) {
                hiddenFields[name] = input.attr("value")
            }
        }

        return RoundcubeLoginPage(
            title = doc.title().trim(),
            bodyClass = doc.body().className().trim(),
            actionUrl = form.absUrl("action").ifBlank { "https://posta.vsb.cz/roundcube/?_task=login" },
            usernameFieldName = usernameField.attr("name").trim(),
            passwordFieldName = passwordField.attr("name").trim(),
            submitLabel = submitButton?.text()?.trim().orEmpty(),
            hiddenFields = hiddenFields
        )
    }
}
