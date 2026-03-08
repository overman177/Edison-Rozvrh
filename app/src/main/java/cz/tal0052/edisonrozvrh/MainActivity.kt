@file:Suppress("SpellCheckingInspection")

package cz.tal0052.edisonrozvrh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class Lesson(
    val subject: String,
    val teacher: String,
    val room: String,
    val day: String,
    val time: String,
    val type: String
)

data class StoredLesson(
    val subject: String? = null,
    val teacher: String? = null,
    val room: String? = null,
    val day: String? = null,
    val time: String? = null,
    val type: String? = null
)

private const val SCHEDULE_CACHE_VERSION = 7
private const val VSB_MAP_PAGE_URL = "https://mapy.vsb.cz/maps/"
private const val VSB_MAP_LANG = "cs"
private val VSB_BRAND = UiColorConfig.VsbBrand
private val VSB_BRAND_DARK = UiColorConfig.VsbBrandDark
private val VSB_BRAND_DEEP = UiColorConfig.VsbBrandDeep
private val VSB_BG = UiColorConfig.VsbBackground
private val roomMapUrlCache = mutableMapOf<String, String>()

@Composable
private fun lessonTypePalette(type: String): LessonTypePalette {
    val accent = when (type.trim().lowercase(Locale.ROOT)) {
        "cviko" -> colorResource(R.color.lesson_accent_cviko)
        "prednaska" -> colorResource(R.color.lesson_accent_prednaska)
        "lab" -> colorResource(R.color.lesson_accent_lab)
        "sport" -> colorResource(R.color.lesson_accent_sport)
        else -> colorResource(R.color.lesson_accent_default)
    }

    return LessonTypePalette(
        container = accent.copy(alpha = UiColorConfig.CardFillAlpha),
        border = accent.copy(alpha = UiColorConfig.CardBorderAlpha),
        title = UiColorConfig.CardTitle,
        meta = UiColorConfig.CardMeta
    )
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false

        setContent {
            val darkTheme = isSystemInDarkTheme()

            val colors = if (darkTheme) {
                darkColorScheme(
                    primary = VSB_BRAND,
                    secondary = UiColorConfig.DarkSecondary,
                    tertiary = UiColorConfig.DarkTertiary,
                    background = VSB_BG,
                    surface = UiColorConfig.DarkSurface,
                    surfaceVariant = UiColorConfig.DarkSurfaceVariant,
                    onPrimary = UiColorConfig.DarkOnPrimary,
                    onSurface = UiColorConfig.DarkOnSurface,
                    onSurfaceVariant = UiColorConfig.DarkOnSurfaceVariant,
                    outline = UiColorConfig.DarkOutline
                )
            } else {
                lightColorScheme(
                    primary = VSB_BRAND,
                    secondary = UiColorConfig.LightSecondary,
                    tertiary = UiColorConfig.LightTertiary,
                    background = UiColorConfig.LightBackground,
                    surface = UiColorConfig.LightSurface,
                    surfaceVariant = UiColorConfig.LightSurfaceVariant,
                    onPrimary = UiColorConfig.DarkOnPrimary,
                    onSurface = UiColorConfig.LightOnSurface,
                    onSurfaceVariant = UiColorConfig.LightOnSurfaceVariant,
                    outline = UiColorConfig.LightOutline
                )
            }

            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val lessonsState = remember { mutableStateOf<List<Lesson>?>(null) }
                    val cached = loadSchedule(this)

                    if (cached != null) {
                        lessonsState.value = cached
                    }

                    if (lessonsState.value == null) {
                        EdisonLoginScreen { lessons ->
                            lessonsState.value = lessons
                        }
                    } else {
                        ScheduleScreen(lessonsState.value!!)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(lessons: List<Lesson>) {

    val days = listOf("Pondeli", "Utery", "Streda", "Ctvrtek", "Patek")

    val todayIndex = when (LocalDate.now().dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        else -> 0
    }

    val pagerState = rememberPagerState(
        initialPage = todayIndex,
        pageCount = { days.size }
    )
    val selectedDayIndex = remember { mutableIntStateOf(todayIndex) }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(LocalTime.now()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(5_000)
        }
    }

    DisposableEffect(context) {
        val timeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = LocalTime.now()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }

        ContextCompat.registerReceiver(
            context,
            timeChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(timeChangeReceiver)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedDayIndex.intValue = pagerState.currentPage
    }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        VsbBrandHeader(
            now = now,
            dayLabel = days[selectedDayIndex.intValue]
        )

        TabRow(
            selectedTabIndex = selectedDayIndex.intValue,
            containerColor = VSB_BRAND_DEEP.copy(alpha = 0.82f),
            contentColor = Color.White,
            divider = {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = VSB_BRAND.copy(alpha = 0.28f)
                )
            }
        ) {
            days.forEachIndexed { index, day ->
                val selected = selectedDayIndex.intValue == index
                Tab(
                    selected = selected,
                    selectedContentColor = Color.White,
                    unselectedContentColor = UiColorConfig.DarkOnSurfaceVariant,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = day.take(2),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            DayTimeline(
                lessons = lessons,
                day = days[page],
                showCurrentTime = page == todayIndex,
                now = now
            )
        }
    }
}

@Composable
private fun VsbBrandHeader(
    now: LocalTime,
    dayLabel: String
) {
    val timeText = remember(now) {
        String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        VSB_BRAND_DARK.copy(alpha = 0.95f),
                        VSB_BRAND_DEEP.copy(alpha = 0.92f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VsbLogoGlyph()
                Column {
                    Text(
                        text = "VSB TECHNICKA",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "UNIVERZITA OSTRAVA",
                        color = UiColorConfig.HeaderTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = dayLabel.uppercase(Locale.ROOT),
                    color = UiColorConfig.HeaderTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VsbLogoGlyph() {
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val heights = listOf(10, 16, 8, 18, 12)
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(CircleShape)
                        .background(VSB_BRAND)
                )
            }
        }
    }
}
@Composable
private fun DayTimeline(
    lessons: List<Lesson>,
    day: String,
    showCurrentTime: Boolean,
    now: LocalTime
) {
    val positionedLessons = buildPositionedLessonsForDay(
        lessons = lessons,
        selectedDay = day
    )

    val scheduleStart = minOf(
        7 * 60,
        ((positionedLessons.minOfOrNull { it.startMinutes } ?: (7 * 60)))
    )
    val scheduleEndBase = maxOf(
        19 * 60 + 15,
        ((positionedLessons.maxOfOrNull { it.endMinutes } ?: (19 * 60 + 15)))
    )
    val scheduleEnd = if (showCurrentTime) {
        maxOf(scheduleEndBase, ((now.hour * 60) + now.minute + 30))
    } else {
        scheduleEndBase
    }

    val minuteHeight = 1.6.dp
    val timelineHeight = minuteHeight * (scheduleEnd - scheduleStart).toFloat()
    val axisWidth = 64.dp
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        val lessonAreaWidth = this@BoxWithConstraints.maxWidth - axisWidth - 8.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineHeight + 24.dp)
        ) {
            val firstHour = scheduleStart / 60
            val lastHour = (scheduleEnd + 59) / 60

            for (hour in firstHour..lastHour) {
                val y = minuteHeight * ((hour * 60 - scheduleStart).toFloat())

                HorizontalDivider(
                    modifier = Modifier
                        .offset(y = y)
                        .fillMaxWidth(),
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Text(
                    text = String.format(Locale.getDefault(), "%02d:00", hour),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(axisWidth)
                        .offset(y = y - 8.dp)
                )
            }


            positionedLessons.forEach { item ->
                val top = minuteHeight * (item.startMinutes - scheduleStart).toFloat()
                val duration = (item.endMinutes - item.startMinutes).coerceAtLeast(30)
                val blockHeight = (minuteHeight * duration.toFloat()).coerceAtLeast(58.dp)
                val palette = lessonTypePalette(item.lesson.type)

                Card(
                    modifier = Modifier
                        .offset(x = axisWidth + 8.dp, y = top)
                        .width(lessonAreaWidth)
                        .height(blockHeight),
                    colors = CardDefaults.cardColors(
                        containerColor = palette.container
                    ),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = item.lesson.subject,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.title,
                            maxLines = 1
                        )
                        LessonMetaLine(
                            iconRes = R.drawable.ic_time,
                            text = item.shownTime,
                            textColor = palette.meta,
                            actionColor = palette.border
                        )

                        if (item.lesson.teacher.isNotBlank()) {
                            LessonMetaLine(
                                iconRes = R.drawable.ic_teacher,
                                text = item.lesson.teacher,
                                textColor = palette.meta,
                                actionColor = palette.border
                            )
                        }

                        if (item.lesson.room.isNotBlank()) {
                            RoomMetaLine(
                                roomText = item.lesson.room,
                                textColor = palette.meta,
                                actionColor = palette.border
                            )
                        }
                    }
                }
            }

            if (showCurrentTime) {
                val nowMinutes = now.hour * 60 + now.minute
                if (nowMinutes in scheduleStart..scheduleEnd) {
                    val nowY = minuteHeight * (nowMinutes - scheduleStart).toFloat()

                    Box(
                        modifier = Modifier
                            .offset(y = nowY)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(VSB_BRAND)
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonMetaLine(
    @DrawableRes iconRes: Int,
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionColor: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .padding(end = 6.dp)
                .width(14.dp)
                .height(14.dp)
        )

        Text(
            text = text,
            fontSize = 12.sp,
            color = textColor,
            maxLines = 1
        )

        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = actionColor,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .border(
                        width = 1.dp,
                        color = actionColor.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        color = actionColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun RoomMetaLine(
    roomText: String,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleanedRoomText = remember(roomText) { normalizeRoomTextForDisplay(roomText) }
    val mapInfo = remember(cleanedRoomText) { resolveVsbRoomMapInfo(cleanedRoomText) }
    val mapQuery = remember(cleanedRoomText, mapInfo) {
        mapSearchCandidates(mapInfo?.roomCode.orEmpty())
            .ifEmpty { mapSearchCandidates(cleanedRoomText) }
            .lastOrNull()
            .orEmpty()
            .ifBlank { cleanedRoomText }
    }

    LessonMetaLine(
        iconRes = R.drawable.ic_room,
        text = cleanedRoomText,
        textColor = textColor,
        actionColor = actionColor,
        actionLabel = "Mapa",
        onActionClick = {
            scope.launch {
                val mapUrl = resolveExternalMapUrl(mapQuery)
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl)))
                }
            }
        }
    )
}

private fun buildExternalMapSearchUrl(query: String): String {
    val q = Uri.encode(query)
    return "$VSB_MAP_PAGE_URL?lang=$VSB_MAP_LANG&search=$q&q=$q&query=$q#search=$q"
}

private fun buildExternalMapItemUrl(id: String, type: String): String {
    return "$VSB_MAP_PAGE_URL?id=${Uri.encode(id)}&type=${Uri.encode(type)}&lang=$VSB_MAP_LANG"
}

private suspend fun resolveExternalMapUrl(query: String): String {
    val normalized = query.trim().uppercase(Locale.ROOT)
    roomMapUrlCache[normalized]?.let { return it }

    val candidates = mapSearchCandidates(query).ifEmpty {
        listOf(query.trim()).filter { it.isNotBlank() }
    }

    for (candidate in candidates) {
        val roomId = fetchRoomIdFromAutocomplete(candidate) ?: continue
        val url = buildExternalMapItemUrl(id = roomId, type = "rooms")
        roomMapUrlCache[normalized] = url
        return url
    }

    return buildExternalMapSearchUrl(query)
}

private suspend fun fetchRoomIdFromAutocomplete(roomQuery: String): String? = withContext(Dispatchers.IO) {
    val cleanQuery = roomQuery.trim()
    if (cleanQuery.isBlank()) return@withContext null

    val endpoint = "https://mapy.vsb.cz/maps/api/v0/rooms/autocomplete?query=${Uri.encode(cleanQuery)}&language=$VSB_MAP_LANG"
    val connection = (URL(endpoint).openConnection() as? HttpURLConnection) ?: return@withContext null

    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = true

        val code = connection.responseCode
        if (code !in 200..299) return@withContext null

        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(body)
        if (array.length() == 0) return@withContext null

        val normalizedQuery = cleanQuery.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val codeText = item.optString("code").orEmpty()
            val normalizedCode = codeText.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
            if (normalizedCode == normalizedQuery) {
                return@withContext item.opt("id")?.toString()?.takeIf { it.isNotBlank() }
            }
        }

        return@withContext array.optJSONObject(0)?.opt("id")?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        return@withContext null
    } finally {
        connection.disconnect()
    }
}
private fun mapSearchCandidates(roomQuery: String): List<String> {
    val raw = roomQuery.trim().uppercase(Locale.ROOT)
    if (raw.isBlank()) return emptyList()

    val normalized = raw.replace(Regex("[^A-Z0-9]"), "")
    if (normalized.isBlank()) return listOf(raw)

    val list = mutableListOf(normalized)
    if (normalized.startsWith("POR") && normalized.length > 3) {
        list += normalized.removePrefix("POR")
    }
    return list.distinct()
}
private fun normalizeRoomTextForDisplay(rawRoom: String): String {
    val withoutParentheses = rawRoom.replace(Regex("\\s*\\([^)]*\\)"), " ").trim()
    val parts = withoutParentheses
        .split(',', ';', '/')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    if (parts.isEmpty()) return withoutParentheses
    return parts.joinToString(", ")
}
internal data class PositionedLesson(
    val lesson: Lesson,
    val shownTime: String,
    val startMinutes: Int,
    val endMinutes: Int
)

internal fun buildPositionedLessonsForDay(
    lessons: List<Lesson>,
    selectedDay: String
): List<PositionedLesson> {
    return lessons
        .filter { normalizeDayForUi(it.day) == selectedDay }
        .mapNotNull { lesson ->
            val shownTime = lesson.time
            val range = parseTimeRangeMinutes(shownTime) ?: return@mapNotNull null
            PositionedLesson(
                lesson = lesson,
                shownTime = shownTime,
                startMinutes = range.first,
                endMinutes = range.second
            )
        }
        .sortedBy { it.startMinutes }
}

private fun parseTimeRangeMinutes(timeRange: String): Pair<Int, Int>? {
    val match = Regex("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})")
        .find(timeRange)
        ?: return null

    val start = parseMinutes(match.groupValues[1]) ?: return null
    val end = parseMinutes(match.groupValues[2]) ?: return null
    val normalizedEnd = if (end <= start) start + 45 else end

    return start to normalizedEnd
}

private fun parseMinutes(time: String): Int? {
    val match = Regex("(\\d{1,2})\\s*:\\s*(\\d{2})").find(time.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null

    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun normalizeDayForUi(day: String): String {

    val token = Normalizer.normalize(day, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    val compact = token.replace(" ", "")

    return when {
        compact.contains("pond") -> "Pondeli"
        compact.contains("uter") || compact.contains("ter") -> "Utery"
        compact.contains("stred") || compact.contains("str") -> "Streda"
        compact.contains("ctvrt") || compact.contains("ctv") -> "Ctvrtek"
        compact.contains("pat") -> "Patek"
        else -> day
    }
}

fun saveSchedule(context: Context, lessons: List<Lesson>) {

    val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    val json = Gson().toJson(lessons)

    prefs.edit {
        putInt("version", SCHEDULE_CACHE_VERSION)
        putString("data", json)
    }

    ScheduleWidgetProvider.refreshAll(context)
}

fun loadSchedule(context: Context): List<Lesson>? {

    val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    val version = prefs.getInt("version", 0)
    if (version != SCHEDULE_CACHE_VERSION) {
        prefs.edit {
            remove("data")
        }
        return null
    }

    val json = prefs.getString("data", null) ?: return null

    return try {
        val stored = Gson().fromJson(json, Array<StoredLesson>::class.java)?.toList().orEmpty()

        val lessons = stored.mapNotNull { item ->
            val subject = item.subject?.trim().orEmpty()
            val day = item.day?.trim().orEmpty()
            val time = item.time?.trim().orEmpty()

            if (subject.isBlank() || day.isBlank() || time.isBlank()) {
                return@mapNotNull null
            }

            Lesson(
                subject = subject,
                teacher = item.teacher?.trim().orEmpty(),
                room = item.room?.trim().orEmpty(),
                day = day,
                time = time,
                type = item.type?.trim().orEmpty().ifBlank { "other" }
            )
        }

        lessons.ifEmpty { null }

    } catch (_: Exception) {
        null
    }
}
























































