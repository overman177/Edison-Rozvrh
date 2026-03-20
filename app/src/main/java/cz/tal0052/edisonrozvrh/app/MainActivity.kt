@file:Suppress("SpellCheckingInspection")

package cz.tal0052.edisonrozvrh.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultItem
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.StudyInfoData
import cz.tal0052.edisonrozvrh.data.parser.WebCreditData
import cz.tal0052.edisonrozvrh.data.repository.EdisonRepository
import cz.tal0052.edisonrozvrh.map.resolveVsbRoomMapInfo
import cz.tal0052.edisonrozvrh.ui.auth.EdisonLoginScreen
import cz.tal0052.edisonrozvrh.ui.auth.WebCreditSyncView
import cz.tal0052.edisonrozvrh.ui.design.LessonTypePalette
import cz.tal0052.edisonrozvrh.ui.design.UiColorConfig
import cz.tal0052.edisonrozvrh.ui.home.ScheduleScreen
import cz.tal0052.edisonrozvrh.widget.ScheduleWidgetProvider
import cz.tal0052.edisonrozvrh.widget.ScheduleWidgetSnapshotProvider
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
    val type: String,
    val weekPattern: String = "every"
)

data class StoredLesson(
    val subject: String? = null,
    val teacher: String? = null,
    val room: String? = null,
    val day: String? = null,
    val time: String? = null,
    val type: String? = null,
    val weekPattern: String? = null
)

data class StoredCurrentResultItem(
    val semesterCode: String? = null,
    val subjectNumber: String? = null,
    val subjectShortcut: String? = null,
    val subjectName: String? = null,
    val creditPoints: String? = null,
    val examPoints: String? = null,
    val totalPoints: String? = null,
    val grade: String? = null,
    val ectsGrade: String? = null,
    val detailUrl: String? = null
)

data class StoredCurrentResultsData(
    val academicYear: String? = null,
    val warningNote: String? = null,
    val items: List<StoredCurrentResultItem>? = null
)

private const val SCHEDULE_CACHE_VERSION = 9
private const val RESULTS_CACHE_VERSION = 3
private const val STUDY_INFO_CACHE_VERSION = 2
private const val WEB_CREDIT_CACHE_VERSION = 2
private const val WEB_CREDIT_MENU_URL = "https://stravovani.vsb.cz/webkredit/Ordering/Menu"
private const val WEB_CREDIT_SILENT_SYNC_INTERVAL_MS = 15 * 60 * 1000L
private const val WEB_CREDIT_LAST_ATTEMPT_KEY = "web_credit_last_attempt_ms"
private const val WEB_CREDIT_DATA_KEY = "web_credit_data"
private const val WEB_CREDIT_VERSION_KEY = "web_credit_version"
private const val SCHEDULE_PREFS_NAME = "schedule"
private const val VSB_MAP_PAGE_URL = "https://mapy.vsb.cz/maps/"
private const val VSB_MAP_LANG = "cs"
private val roomMapUrlCache = mutableMapOf<String, String>()
@Volatile private var webCreditSilentSyncRunning = false
private val webCreditSilentSyncLock = Any()

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
        title = colorResource(R.color.app_card_title),
        meta = colorResource(R.color.app_card_meta)
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
                    primary = colorResource(R.color.app_brand_primary),
                    secondary = colorResource(R.color.app_dark_secondary),
                    tertiary = colorResource(R.color.app_dark_tertiary),
                    background = colorResource(R.color.app_dark_background),
                    surface = colorResource(R.color.app_dark_surface),
                    surfaceVariant = colorResource(R.color.app_dark_surface_variant),
                    onPrimary = colorResource(R.color.app_dark_on_primary),
                    onSurface = colorResource(R.color.app_dark_on_surface),
                    onSurfaceVariant = colorResource(R.color.app_dark_on_surface_variant),
                    outline = colorResource(R.color.app_dark_outline),
                    error = colorResource(R.color.app_dark_error)
                )
            } else {
                lightColorScheme(
                    primary = colorResource(R.color.app_brand_primary),
                    secondary = colorResource(R.color.app_light_secondary),
                    tertiary = colorResource(R.color.app_light_tertiary),
                    background = colorResource(R.color.app_light_background),
                    surface = colorResource(R.color.app_light_surface),
                    surfaceVariant = colorResource(R.color.app_light_surface_variant),
                    onPrimary = colorResource(R.color.app_dark_on_primary),
                    onSurface = colorResource(R.color.app_light_on_surface),
                    onSurfaceVariant = colorResource(R.color.app_light_on_surface_variant),
                    outline = colorResource(R.color.app_light_outline),
                    error = colorResource(R.color.app_light_error)
                )
            }

            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val lessonsState = remember { mutableStateOf<List<Lesson>?>(null) }
                    val resultsState = remember { mutableStateOf<CurrentResultsData?>(null) }

                    val studyInfoState = remember { mutableStateOf<StudyInfoData?>(null) }
                    val forceEdisonLoginState = remember { mutableStateOf(false) }
                    val webCreditSyncAttemptedState = remember { mutableStateOf(false) }
                    val webCreditAuthVisibleState = remember { mutableStateOf(false) }

                    val cachedLessons = loadSchedule(this)
                    if (cachedLessons != null) {
                        lessonsState.value = cachedLessons
                    }

                    val cachedResults = loadCurrentResults(this)
                    if (cachedResults != null) {
                        resultsState.value = cachedResults
                    }

                    val cachedStudyInfo = loadStudyInfo(this)
                    if (cachedStudyInfo != null) {
                        studyInfoState.value = cachedStudyInfo
                    }

                    val cachedWebCredit = loadWebCredit(this)
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner, lessonsState.value, forceEdisonLoginState.value) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (
                                event == Lifecycle.Event.ON_START &&
                                lessonsState.value != null &&
                                !forceEdisonLoginState.value
                            ) {
                                webCreditAuthVisibleState.value = false
                                webCreditSyncAttemptedState.value = false
                            }
                        }

                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    if (lessonsState.value == null || forceEdisonLoginState.value) {
                        EdisonLoginScreen { lessons, currentResults, studyInfo ->
                            lessonsState.value = lessons
                            if (currentResults != null) {
                                resultsState.value = currentResults
                            }
                            if (studyInfo != null) {
                                studyInfoState.value = studyInfo
                            }
                            webCreditSyncAttemptedState.value = false
                            webCreditAuthVisibleState.value = false
                            forceEdisonLoginState.value = false
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScheduleScreen(
                                lessons = lessonsState.value!!,
                                currentResults = resultsState.value,
                                studyInfo = studyInfoState.value,
                                onRefreshFromEdison = {
                                    webCreditSyncAttemptedState.value = false
                                    webCreditAuthVisibleState.value = false
                                    forceEdisonLoginState.value = true
                                }
                            )

                            if (!webCreditSyncAttemptedState.value) {
                                key(webCreditAuthVisibleState.value) {
                                    WebCreditSyncView(
                                        modifier = if (webCreditAuthVisibleState.value) {
                                            Modifier.fillMaxSize()
                                        } else {
                                            Modifier.size(1.dp)
                                        },
                                        allowInteractiveAuth = true,
                                        onAuthRequired = {
                                            webCreditAuthVisibleState.value = true
                                        },
                                        onFinished = {
                                            webCreditAuthVisibleState.value = false
                                            webCreditSyncAttemptedState.value = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyScheduleScreen(lessons: List<Lesson>) {

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
    val scope = remember { kotlinx.coroutines.CoroutineScope(Dispatchers.Main) }
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
            containerColor = colorResource(R.color.app_legacy_tab_container),
            contentColor = Color.White,
            divider = {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colorResource(R.color.app_legacy_tab_divider)
                )
            }
        ) {
            days.forEachIndexed { index, day ->
                val selected = selectedDayIndex.intValue == index
                Tab(
                    selected = selected,
                    selectedContentColor = Color.White,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        colorResource(R.color.app_header_gradient_start),
                        colorResource(R.color.app_header_gradient_end)
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
                        color = colorResource(R.color.app_header_text_muted),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = dayLabel.uppercase(Locale.ROOT),
                    color = colorResource(R.color.app_header_text_muted),
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
            .background(colorResource(R.color.app_logo_glyph_background))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val heights = listOf(18, 12, 8, 12, 18)
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
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
                            .background(MaterialTheme.colorScheme.error)
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
    val scope = remember { kotlinx.coroutines.CoroutineScope(Dispatchers.Main) }
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

fun normalizeWeekPatternCode(raw: String?): String {
    val token = raw?.trim().orEmpty().lowercase(Locale.ROOT)
    return when {
        token.startsWith("odd") || token.startsWith("lich") || token.contains("nepar") -> "odd"
        token.startsWith("even") || token.startsWith("sud") || token.contains("par") -> "even"
        else -> "every"
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
    ScheduleWidgetSnapshotProvider.refreshAll(context)
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
                type = item.type?.trim().orEmpty().ifBlank { "other" },
                weekPattern = normalizeWeekPatternCode(item.weekPattern)
            )
        }

        lessons.ifEmpty { null }

    } catch (_: Exception) {
        null
    }
}



fun saveCurrentResults(context: Context, currentResults: CurrentResultsData) {
    val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    prefs.edit {
        putInt("results_version", RESULTS_CACHE_VERSION)
        putString("results_data", Gson().toJson(currentResults))
    }
}

fun loadCurrentResults(context: Context): CurrentResultsData? {
    val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    val version = prefs.getInt("results_version", 0)
    if (version != RESULTS_CACHE_VERSION) {
        prefs.edit {
            remove("results_data")
        }
        return null
    }

    val json = prefs.getString("results_data", null) ?: return null

    return try {
        val parsed = Gson().fromJson(json, CurrentResultsData::class.java) ?: return null
        if (parsed.items.isEmpty()) return null
        parsed
    } catch (_: Exception) {
        null
    }
}

fun saveStudyInfo(context: Context, studyInfo: StudyInfoData) {
    val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    prefs.edit {
        putInt("study_info_version", STUDY_INFO_CACHE_VERSION)
        putString("study_info_data", Gson().toJson(studyInfo))
    }
}

fun loadStudyInfo(context: Context): StudyInfoData? {
    val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    val version = prefs.getInt("study_info_version", 0)
    if (version != STUDY_INFO_CACHE_VERSION) {
        prefs.edit {
            remove("study_info_data")
        }
        return null
    }

    val json = prefs.getString("study_info_data", null) ?: return null

    return try {
        val parsed = Gson().fromJson(json, StudyInfoData::class.java) ?: return null
        if (parsed.personal == null && parsed.matriculation == null && parsed.admission == null) {
            null
        } else {
            parsed
        }
    } catch (_: Exception) {
        null
    }
}
fun saveWebCredit(context: Context, webCredit: WebCreditData) {
    val prefs = context.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()

    prefs.edit {
        putInt(WEB_CREDIT_VERSION_KEY, WEB_CREDIT_CACHE_VERSION)
        putString(WEB_CREDIT_DATA_KEY, Gson().toJson(webCredit))
        putLong(WEB_CREDIT_LAST_ATTEMPT_KEY, now)
    }

    ScheduleWidgetProvider.refreshAll(context)
    ScheduleWidgetSnapshotProvider.refreshAll(context)
}

private fun markWebCreditSilentSyncAttempt(context: Context, timestamp: Long = System.currentTimeMillis()) {
    context.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE).edit {
        putLong(WEB_CREDIT_LAST_ATTEMPT_KEY, timestamp)
    }
}

private fun shouldRunWebCreditSilentSync(context: Context, now: Long = System.currentTimeMillis()): Boolean {
    val lastAttempt = context.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(WEB_CREDIT_LAST_ATTEMPT_KEY, 0L)
    return now - lastAttempt >= WEB_CREDIT_SILENT_SYNC_INTERVAL_MS
}

fun maybeSyncWebCreditInBackground(context: Context) {
    val appContext = context.applicationContext
    val cookies = CookieManager.getInstance().getCookie(WEB_CREDIT_MENU_URL).orEmpty()
    var shouldStart = false

    synchronized(webCreditSilentSyncLock) {
        if (
            !webCreditSilentSyncRunning &&
            cookies.isNotBlank() &&
            shouldRunWebCreditSilentSync(appContext)
        ) {
            markWebCreditSilentSyncAttempt(appContext)
            webCreditSilentSyncRunning = true
            shouldStart = true
        }
    }

    if (!shouldStart) return

    Thread {
        try {
            val webCredit = EdisonRepository.downloadWebCredit(cookies)
            if (webCredit?.balance != null) {
                saveWebCredit(appContext, webCredit)
            }
        } finally {
            synchronized(webCreditSilentSyncLock) {
                webCreditSilentSyncRunning = false
            }
        }
    }.start()
}

fun loadWebCredit(context: Context): WebCreditData? {
    val prefs = context.getSharedPreferences(SCHEDULE_PREFS_NAME, Context.MODE_PRIVATE)
    val version = prefs.getInt(WEB_CREDIT_VERSION_KEY, 0)
    if (version != WEB_CREDIT_CACHE_VERSION) {
        prefs.edit {
            remove(WEB_CREDIT_DATA_KEY)
            remove(WEB_CREDIT_LAST_ATTEMPT_KEY)
        }
        return null
    }

    val json = prefs.getString(WEB_CREDIT_DATA_KEY, null) ?: return null

    return try {
        Gson().fromJson(json, WebCreditData::class.java)?.takeIf { it.balance != null }
    } catch (_: Exception) {
        null
    }
}
