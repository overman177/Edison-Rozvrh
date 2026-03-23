package cz.tal0052.edisonrozvrh.data.parser

import com.google.gson.JsonParser
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

        val countText = SET_ROWCOUNT_REGEX.find(exec)
            ?.groupValues
            ?.getOrNull(2)
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
                unread = !(flags.get("seen")?.asBoolean ?: false),
                flagged = flags.get("flagged")?.asBoolean ?: false,
                hasAttachment = (flags.get("hasattachment")?.asBoolean ?: false) ||
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

    private val SET_ROWCOUNT_REGEX = Regex("""set_rowcount\((['"])(.*?)\1""")
}
