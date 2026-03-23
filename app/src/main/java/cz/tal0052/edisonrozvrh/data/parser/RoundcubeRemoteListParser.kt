package cz.tal0052.edisonrozvrh.data.parser

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.jsoup.Jsoup

object RoundcubeRemoteListParser {

    fun parse(
        jsonText: String,
        fallbackFolders: List<RoundcubeFolder>,
        mailbox: String
    ): RoundcubeInboxPage? {
        val response = JsonParser.parseString(jsonText).asJsonObject
        val exec = response.get("exec")?.asString?.trim().orEmpty()
        if (exec.isBlank()) return null

        val countText = extractCallArguments(exec, "set_rowcount")
            .firstOrNull()
            ?.getOrNull(0)
            ?.trim()
            ?.trim('"', '\'')
            ?.let { Jsoup.parse(it).text().trim() }
            .orEmpty()

        val messages = extractCallArguments(exec, "add_message_row").mapNotNull { args ->
            if (args.size < 3) return@mapNotNull null

            val uid = args[0].trim().trim('"', '\'')
            val colsJson = args[1].trim()
            val flagsJson = args[2].trim()
            if (uid.isBlank() || !colsJson.startsWith("{") || !flagsJson.startsWith("{")) {
                return@mapNotNull null
            }

            val cols = JsonParser.parseString(colsJson).asJsonObject
            val flags = JsonParser.parseString(flagsJson).asJsonObject
            val rowMailbox = flags.get("mbox")?.asString?.trim().orEmpty().ifBlank { mailbox }

            RoundcubeInboxMessage(
                uid = uid,
                sender = Jsoup.parse(cols.get("fromto")?.asString.orEmpty()).text().trim(),
                subject = Jsoup.parse(cols.get("subject")?.asString.orEmpty()).text().trim(),
                date = Jsoup.parse(cols.get("date")?.asString.orEmpty()).text().trim(),
                size = Jsoup.parse(cols.get("size")?.asString.orEmpty()).text().trim(),
                unread = !parseRoundcubeFlag(flags.get("seen")),
                flagged = parseRoundcubeFlag(flags.get("flagged")),
                hasAttachment = parseRoundcubeFlag(flags.get("hasattachment")) ||
                    !flags.get("attachmentClass")?.asString.orEmpty().isBlank(),
                detailUrl = "https://posta.vsb.cz/roundcube/?_task=mail&_mbox=$rowMailbox&_uid=$uid&_action=show"
            )
        }

        return RoundcubeInboxPage(
            title = "Roundcube :: Inbox",
            countText = countText,
            folders = fallbackFolders,
            messages = messages
        )
    }

    private fun extractCallArguments(script: String, functionName: String): List<List<String>> {
        val results = mutableListOf<List<String>>()
        var searchIndex = 0

        while (true) {
            val functionIndex = script.indexOf("$functionName(", searchIndex)
            if (functionIndex < 0) break

            val openParenIndex = functionIndex + functionName.length
            val closeParenIndex = findMatchingParen(script, openParenIndex) ?: break
            val argumentString = script.substring(openParenIndex + 1, closeParenIndex)
            results += splitTopLevelArguments(argumentString)
            searchIndex = closeParenIndex + 1
        }

        return results
    }

    private fun findMatchingParen(text: String, openParenIndex: Int): Int? {
        var parenDepth = 0
        var braceDepth = 0
        var bracketDepth = 0
        var inString = false
        var escaping = false
        var stringChar = '"'

        for (index in openParenIndex until text.length) {
            val char = text[index]
            when {
                escaping -> escaping = false
                inString && char == '\\' -> escaping = true
                inString && char == stringChar -> inString = false
                !inString && (char == '"' || char == '\'') -> {
                    inString = true
                    stringChar = char
                }
                !inString && char == '(' -> parenDepth++
                !inString && char == ')' -> {
                    parenDepth--
                    if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                        return index
                    }
                }
                !inString && char == '{' -> braceDepth++
                !inString && char == '}' -> braceDepth--
                !inString && char == '[' -> bracketDepth++
                !inString && char == ']' -> bracketDepth--
            }
        }

        return null
    }

    private fun splitTopLevelArguments(argumentString: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var braceDepth = 0
        var bracketDepth = 0
        var parenDepth = 0
        var inString = false
        var escaping = false
        var stringChar = '"'

        argumentString.forEachIndexed { index, char ->
            when {
                escaping -> escaping = false
                inString && char == '\\' -> escaping = true
                inString && char == stringChar -> inString = false
                !inString && (char == '"' || char == '\'') -> {
                    inString = true
                    stringChar = char
                }
                !inString && char == '{' -> braceDepth++
                !inString && char == '}' -> braceDepth--
                !inString && char == '[' -> bracketDepth++
                !inString && char == ']' -> bracketDepth--
                !inString && char == '(' -> parenDepth++
                !inString && char == ')' -> parenDepth--
                !inString && char == ',' && braceDepth == 0 && bracketDepth == 0 && parenDepth == 0 -> {
                    parts += argumentString.substring(start, index).trim()
                    start = index + 1
                }
            }
        }

        if (start < argumentString.length) {
            parts += argumentString.substring(start).trim()
        }

        return parts.filter { it.isNotBlank() }
    }

    private fun parseRoundcubeFlag(value: JsonElement?): Boolean {
        if (value == null || value.isJsonNull) return false

        val primitive = value as? JsonPrimitive ?: return false

        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> runCatching { primitive.asInt != 0 }.getOrDefault(false)
            primitive.isString -> {
                val normalized = primitive.asString.trim().lowercase()
                normalized == "1" ||
                    normalized == "true" ||
                    normalized == "flagged" ||
                    normalized == "seen" ||
                    normalized == "read" ||
                    normalized == "yes"
            }
            else -> false
        }
    }
}
