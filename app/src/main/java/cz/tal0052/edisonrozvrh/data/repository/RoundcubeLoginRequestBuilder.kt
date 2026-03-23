package cz.tal0052.edisonrozvrh.data.repository

import cz.tal0052.edisonrozvrh.data.parser.RoundcubeLoginPage
import okhttp3.FormBody
import java.util.TimeZone

data class RoundcubeLoginRequest(
    val actionUrl: String,
    val refererUrl: String,
    val parameters: LinkedHashMap<String, String>
) {
    fun toFormBody(): FormBody {
        val builder = FormBody.Builder(Charsets.UTF_8)
        parameters.forEach { (name, value) ->
            builder.add(name, value)
        }
        return builder.build()
    }
}

object RoundcubeLoginRequestBuilder {

    fun build(
        loginPage: RoundcubeLoginPage,
        username: String,
        password: String
    ): RoundcubeLoginRequest? {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) return null

        val parameters = linkedMapOf<String, String>()
        loginPage.hiddenFields.forEach { (name, value) ->
            parameters[name] = value
        }

        if (!parameters.containsKey("_task")) {
            parameters["_task"] = "login"
        }
        if (!parameters.containsKey("_action")) {
            parameters["_action"] = "login"
        }
        val timezone = TimeZone.getDefault().id.takeIf { it.isNotBlank() } ?: "Europe/Prague"
        if (
            !parameters.containsKey("_timezone") ||
            parameters["_timezone"].isNullOrBlank() ||
            parameters["_timezone"] == "_default_"
        ) {
            parameters["_timezone"] = timezone
        }
        if (!parameters.containsKey("_url")) {
            parameters["_url"] = "_task=login"
        }

        parameters[loginPage.usernameFieldName] = normalizedUsername
        parameters[loginPage.passwordFieldName] = password

        return RoundcubeLoginRequest(
            actionUrl = loginPage.actionUrl,
            refererUrl = loginPage.actionUrl,
            parameters = LinkedHashMap(parameters)
        )
    }
}
