package cz.tal0052.edisonrozvrh

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class VsbRoomMapInfo(
    val roomCode: String,
    val buildingCode: String,
    val roomUrl: String,
    val buildingUrl: String
)

private const val VSB_MAP_BASE_URL = "https://mapy.vsb.cz/maps/?lang=cs"

// Optional explicit IDs (preferred by the map app when known).
private val roomIdOverrides: Map<String, String> = emptyMap()
private val buildingIdOverrides: Map<String, String> = emptyMap()

internal fun resolveVsbRoomMapInfo(rawRoom: String): VsbRoomMapInfo? {
    val roomCode = extractPrimaryRoomCode(rawRoom) ?: return null
    val buildingCode = roomCode.takeWhile { it.isLetter() }
        .ifBlank { roomCode }

    return VsbRoomMapInfo(
        roomCode = roomCode,
        buildingCode = buildingCode,
        roomUrl = buildRoomUrl(roomCode),
        buildingUrl = buildBuildingUrl(buildingCode)
    )
}

private fun buildRoomUrl(roomCode: String): String {
    val roomId = roomIdOverrides[roomCode]
    if (!roomId.isNullOrBlank()) {
        return "$VSB_MAP_BASE_URL&id=${urlEncode(roomId)}&type=rooms"
    }

    return buildSearchUrl(roomCode)
}

private fun buildBuildingUrl(buildingCode: String): String {
    val buildingId = buildingIdOverrides[buildingCode]
    if (!buildingId.isNullOrBlank()) {
        return "$VSB_MAP_BASE_URL&id=${urlEncode(buildingId)}&type=buildings"
    }

    return buildSearchUrl(buildingCode)
}


private fun buildSearchUrl(query: String): String {
    val encoded = urlEncode(query)
    return "$VSB_MAP_BASE_URL&search=$encoded"
}

private fun extractPrimaryRoomCode(rawRoom: String): String? {
    val candidates = rawRoom
        .split(',', ';', '/')
        .asSequence()
        .map { part -> part.trim() }
        .filter { it.isNotBlank() }

    for (candidate in candidates) {
        val compact = candidate
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]"), "")

        if (compact.isBlank()) continue

        val hasLetter = compact.any { it.isLetter() }
        val hasDigit = compact.any { it.isDigit() }

        if (hasLetter && hasDigit) {
            return compact
        }

        if (hasLetter && compact.length >= 4) {
            return compact
        }
    }

    return null
}

private fun urlEncode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
