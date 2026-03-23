package cz.tal0052.edisonrozvrh.ui.home

import android.content.Intent
import android.net.Uri
import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.app.PositionedLesson
import cz.tal0052.edisonrozvrh.app.buildPositionedLessonsForDay
import cz.tal0052.edisonrozvrh.app.normalizeDayForUi
import cz.tal0052.edisonrozvrh.app.normalizeWeekPatternCode
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultItem
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.StudyField
import cz.tal0052.edisonrozvrh.data.parser.StudyInfoData
import cz.tal0052.edisonrozvrh.data.parser.StudyPageData
import cz.tal0052.edisonrozvrh.map.resolveVsbRoomMapInfo
import cz.tal0052.edisonrozvrh.ui.design.LessonTypePalette
import cz.tal0052.edisonrozvrh.ui.design.UiColorConfig


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.text.Normalizer
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.IsoFields
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private enum class HomeTab(val label: String) {
    UNIVERSITY("Studium"),
    RESULTS("V\u00fdsledky"),
    EXAMS("Zkou\u0161ky"),
    SCHEDULE("Rozvrh"),
    EMAIL("Email")
}

private enum class WeekParity(val label: String) {
    ODD("Lich\u00fd"),
    EVEN("Sud\u00fd"),
    EVERY("Ka\u017ed\u00fd")
}

private enum class ResultSemesterFilter(val label: String) {
    WINTER("Zimn\u00ed"),
    SUMMER("Letn\u00ed")
}

private const val VSB_MAP_PAGE_URL = "https://mapy.vsb.cz/maps/"
private const val VSB_MAP_LANG = "cs"
private val roomMapUrlCache = mutableMapOf<String, String>()

@Composable
fun ScheduleScreen(
    lessons: List<Lesson>,
    currentResults: CurrentResultsData?,
    studyInfo: StudyInfoData?,
    onAddCustomLesson: (Lesson) -> Unit,
    onDeleteCustomLesson: (String) -> Unit,
    onRefreshFromEdison: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.SCHEDULE) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            EdisonBottomNavigation(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
            )
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                HomeTab.SCHEDULE -> ScheduleGridTab(
                    lessons = lessons,
                    onAddCustomLesson = onAddCustomLesson,
                    onDeleteCustomLesson = onDeleteCustomLesson
                )
                HomeTab.UNIVERSITY -> StudyInfoTab(
                    studyInfo = studyInfo,
                    onRefreshFromEdison = onRefreshFromEdison
                )

                HomeTab.RESULTS -> ResultsTab(
                    currentResults = currentResults,
                    onRefreshFromEdison = onRefreshFromEdison
                )

                HomeTab.EXAMS -> PlaceholderTab(
                    title = "Term\u00edny",
                    subtitle = "Work in progress, dod\u011bl\u00e1m a\u017e budou zkou\u0161ky dostupn\u00e9."
                )

                HomeTab.EMAIL -> EmailVerificationTab()
            }

            if (selectedTab != HomeTab.EMAIL) {
                Image(
                    painter = painterResource(id = R.drawable.logo_full),
                    contentDescription = "Logo Edison Rozvrh",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .offset(y = (-47).dp)
                        .width(118.dp)
                        .zIndex(5f),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

@Composable
private fun EdisonBottomNavigation(
    selectedTab: HomeTab,
    onSelect: (HomeTab) -> Unit
) {
    val tabs = listOf(HomeTab.UNIVERSITY, HomeTab.RESULTS, HomeTab.EXAMS, HomeTab.SCHEDULE, HomeTab.EMAIL)

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleGridTab(
    lessons: List<Lesson>,
    onAddCustomLesson: (Lesson) -> Unit,
    onDeleteCustomLesson: (String) -> Unit
) {
    val dayOrder = listOf("Pondeli", "Utery", "Streda", "Ctvrtek", "Patek")
    val dayLabels = mapOf(
        "Pondeli" to "Po",
        "Utery" to "Ut",
        "Streda" to "St",
        "Ctvrtek" to "Ct",
        "Patek" to "Pa"
    )
    var isAddDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<Lesson?>(null) }

    val lessonsByDay = remember(lessons) {
        dayOrder.associateWith { day -> buildPositionedLessonsForDay(lessons, day) }
    }
    val allPositionedLessons = remember(lessonsByDay) { lessonsByDay.values.flatten() }
    val teachingSlots = remember(allPositionedLessons) {
        buildTeachingSlots(allPositionedLessons)
    }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val nowMinutes = rememberCurrentTimeMinutes()
    val isWeekday = remember(nowMinutes) {
        val dayOfWeek = LocalDate.now().dayOfWeek
        dayOfWeek >= DayOfWeek.MONDAY && dayOfWeek <= DayOfWeek.FRIDAY
    }
    val currentWeekParity = remember(nowMinutes) {
        currentCalendarWeekParity(LocalDate.now())
    }
    val dayLabelWidth = 58.dp
    val slotSpacing = 8.dp
    val slotWidth = 172.dp
    val lessonRowHeight = 134.dp
    val dayRowSpacing = 10.dp

    val nowLineOffset = remember(teachingSlots, nowMinutes, isWeekday, dayLabelWidth, slotSpacing, slotWidth) {
        if (!isWeekday) {
            null
        } else {
            calculateCurrentTimeLineOffset(
                nowMinutes = nowMinutes,
                teachingSlots = teachingSlots,
                dayLabelWidth = dayLabelWidth,
                slotSpacing = slotSpacing,
                slotWidth = slotWidth
            )
        }
    }

    val currentYear = LocalDate.now().year
    val currentMonth = LocalDate.now().monthValue
    val academicLabel = if (currentMonth >= 8) "$currentYear/${currentYear + 1}" else "${currentYear - 1}/$currentYear"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Text(
                    text = academicLabel,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { isAddDialogVisible = true },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Pridat")
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("VT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Rozvrh",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
        ) {
            Text(
                text = "Aktualni tyden: ${currentWeekParity.label}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (teachingSlots.isEmpty()) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
            ) {
                Text(
                    text = "V tomhle tydnu nejsou v rozvrhu zadne hodiny.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                    .padding(10.dp)
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
            ) {
                Column(
                    modifier = Modifier.padding(top = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(dayRowSpacing)
                ) {
                    dayOrder.forEach {
                        Row(horizontalArrangement = Arrangement.spacedBy(slotSpacing)) {
                            Box(
                                modifier = Modifier
                                    .width(dayLabelWidth)
                                    .height(lessonRowHeight)
                            )
                            teachingSlots.forEach {
                                Box(
                                    modifier = Modifier
                                        .width(slotWidth)
                                        .height(lessonRowHeight)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                val nowLineHeight = (lessonRowHeight * dayOrder.size) + (dayRowSpacing * (dayOrder.size - 1))
                if (nowLineOffset != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = nowLineOffset, top = 64.dp)
                            .width(2.dp)
                            .height(nowLineHeight)
                            .zIndex(2f)
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(dayRowSpacing)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(slotSpacing)) {
                        Box(modifier = Modifier.width(dayLabelWidth).height(52.dp))
                        teachingSlots.forEach { slot ->
                            Column(modifier = Modifier.width(slotWidth)) {
                                Text(
                                    text = minutesToLabel(slot.first),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = minutesToLabel(slot.second),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    dayOrder.forEach { day ->
                        val positioned = lessonsByDay[day].orEmpty().sortedBy { it.startMinutes }
                        val byStart = positioned.associateBy { it.startMinutes }

                        Row(horizontalArrangement = Arrangement.spacedBy(slotSpacing)) {
                            Box(
                                modifier = Modifier
                                    .width(dayLabelWidth)
                                    .height(lessonRowHeight),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = dayLabels[day].orEmpty(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 22.sp
                                )
                            }

                            var slotIndex = 0
                            while (slotIndex < teachingSlots.size) {
                                val slotStart = teachingSlots[slotIndex].first
                                val item = byStart[slotStart]

                                if (item != null) {
                                    var span = 0
                                    var scanIndex = slotIndex
                                    while (scanIndex < teachingSlots.size && teachingSlots[scanIndex].first < item.endMinutes) {
                                        span++
                                        scanIndex++
                                    }
                                    val widthValue = if (span <= 1) {
                                        slotWidth
                                    } else {
                                        slotWidth * span + slotSpacing * (span - 1)
                                    }

                                    GridLessonCard(
                                        item = item,
                                        cardWidth = widthValue,
                                        cardHeight = lessonRowHeight,
                                        currentWeekParity = currentWeekParity,
                                        onRequestDeleteCustomLesson = { lesson -> pendingDeletion = lesson }
                                    )
                                    slotIndex += span.coerceAtLeast(1)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(slotWidth)
                                            .height(lessonRowHeight)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                    )
                                    slotIndex++
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddDialogVisible) {
        AddCustomActivityDialog(
            onDismiss = { isAddDialogVisible = false },
            onConfirm = { lesson ->
                isAddDialogVisible = false
                onAddCustomLesson(lesson)
            }
        )
    }

    pendingDeletion?.let { lesson ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = {
                Text("Smazat aktivitu")
            },
            text = {
                Text("Odebrat aktivitu ${lesson.subject} z rozvrhu?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomLesson(lesson.id)
                        pendingDeletion = null
                    }
                ) {
                    Text("Smazat")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text("Zrusit")
                }
            }
        )
    }

}

@Composable
private fun GridLessonCard(
    item: PositionedLesson,
    cardWidth: Dp,
    cardHeight: Dp,
    currentWeekParity: WeekParity,
    onRequestDeleteCustomLesson: (Lesson) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lessonTypePaletteForGrid(item.lesson.type)
    val weekParity = remember(item.lesson.weekPattern) {
        toWeekParity(item.lesson.weekPattern)
    }
    val isOutOfCurrentWeek = weekParity != WeekParity.EVERY && weekParity != currentWeekParity
    val cleanedRoomText = remember(item.lesson.room) { normalizeRoomTextForDisplay(item.lesson.room) }
    val mapInfo = remember(cleanedRoomText) { resolveVsbRoomMapInfo(cleanedRoomText) }
    val mapQuery = remember(cleanedRoomText, mapInfo) {
        mapSearchCandidates(mapInfo?.roomCode.orEmpty())
            .ifEmpty { mapSearchCandidates(cleanedRoomText) }
            .lastOrNull()
            .orEmpty()
            .ifBlank { cleanedRoomText }
    }
    val hasMapTarget = remember(mapQuery) { mapQuery.any { it.isLetterOrDigit() } }
    val teacherLineText = remember(item.lesson.teacher, item.lesson.isCustom) {
        if (item.lesson.isCustom) {
            item.lesson.teacher.ifBlank { "Vlastni aktivita" }
        } else {
            item.lesson.teacher.ifBlank { "-" }
        }
    }
    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .alpha(if (isOutOfCurrentWeek) 0.42f else 1f),
        colors = CardDefaults.cardColors(containerColor = palette.container),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(38.dp)
                            .background(palette.border, RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.lesson.subject,
                        color = palette.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        lineHeight = 19.sp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.lesson.isCustom) {
                        SmallLessonBadge(
                            label = "Vlastni",
                            background = palette.border.copy(alpha = 0.18f),
                            border = palette.border.copy(alpha = 0.44f),
                            textColor = palette.title
                        )
                    }
                    if (weekParity != WeekParity.EVERY) {
                        SmallLessonBadge(
                            label = weekParity.label,
                            background = palette.border.copy(alpha = 0.18f),
                            border = palette.border.copy(alpha = 0.44f),
                            textColor = palette.title
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            CardMetaLine(
                iconRes = R.drawable.ic_teacher,
                text = teacherLineText,
                textColor = palette.meta,
                actionLabel = if (item.lesson.isCustom) "Smazat" else null,
                onActionClick = if (!item.lesson.isCustom) null else {
                    { onRequestDeleteCustomLesson(item.lesson) }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            CardMetaLine(
                iconRes = R.drawable.ic_room,
                text = item.lesson.room.ifBlank { "-" },
                textColor = palette.meta,
                actionLabel = if (hasMapTarget) "Mapa" else null,
                onActionClick = if (!hasMapTarget) null else {
                    {
                        scope.launch {
                            val mapUrl = resolveExternalMapUrl(mapQuery)
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl)))
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CardMetaLine(
    iconRes: Int,
    text: String,
    textColor: Color,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SmallLessonBadge(
    label: String,
    background: Color,
    border: Color,
    textColor: Color
) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .background(
                color = background,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun AddCustomActivityDialog(
    onDismiss: () -> Unit,
    onConfirm: (Lesson) -> Unit
) {
    val timeSlots = remember { predefinedCustomTimeSlots() }
    val startOptions = remember(timeSlots) { timeSlots.map { slot -> slot.first }.distinct() }
    val endOptions = remember(timeSlots) { timeSlots.map { slot -> slot.second }.distinct() }
    var subject by rememberSaveable { mutableStateOf("") }
    var selectedDay by rememberSaveable { mutableStateOf("Pondeli") }
    var selectedStartMinutes by rememberSaveable { mutableStateOf(startOptions.first()) }
    var selectedEndMinutes by rememberSaveable {
        mutableStateOf(endOptions.first { minutes -> minutes > startOptions.first() })
    }
    var room by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf("") }
    var selectedColorType by rememberSaveable { mutableStateOf("other") }
    var weekPattern by rememberSaveable { mutableStateOf("every") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val dayOptions = listOf(
        "Pondeli" to "Pondeli",
        "Utery" to "Utery",
        "Streda" to "Streda",
        "Ctvrtek" to "Ctvrtek",
        "Patek" to "Patek"
    )
    val colorOptions = listOf(
        CustomColorChoice("other", "Sedosmodra", lessonTypePaletteForGrid("other").border),
        CustomColorChoice("cviko", "Modra", lessonTypePaletteForGrid("cviko").border),
        CustomColorChoice("prednaska", "Cervena", lessonTypePaletteForGrid("prednaska").border),
        CustomColorChoice("lab", "Zelena", lessonTypePaletteForGrid("lab").border),
        CustomColorChoice("sport", "Zluta", lessonTypePaletteForGrid("sport").border),
        CustomColorChoice("custom_orange", "Oranzova", lessonTypePaletteForGrid("custom_orange").border),
        CustomColorChoice("custom_pink", "Ruzova", lessonTypePaletteForGrid("custom_pink").border),
        CustomColorChoice("custom_indigo", "Indigova", lessonTypePaletteForGrid("custom_indigo").border),
        CustomColorChoice("custom_mint", "Mentolova", lessonTypePaletteForGrid("custom_mint").border)
    )
    val weekOptions = listOf(
        "every" to "Kazdy tyden",
        "odd" to "Lichy tyden",
        "even" to "Sudy tyden"
    )
    val endBoundaryOptions = remember(selectedStartMinutes, endOptions) {
        endOptions.filter { minutes -> minutes > selectedStartMinutes }
    }
    val safeSelectedEndMinutes = if (selectedEndMinutes in endBoundaryOptions) {
        selectedEndMinutes
    } else {
        endBoundaryOptions.first()
    }
    val selectedDayLabel = dayOptions.first { it.first == selectedDay }.second
    val selectedWeekLabel = weekOptions.first { it.first == weekPattern }.second
    val previewPalette = lessonTypePaletteForGrid(selectedColorType)
    val previewTitle = subject.trim().ifBlank { "Nova aktivita" }
    val previewTeacher = detail.trim().ifBlank { "Vlastni aktivita" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pridat aktivitu",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Rucne pridana polozka se ulozi do rozvrhu i widgetu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    SmallLessonBadge(
                        label = "Vlastni",
                        background = previewPalette.border.copy(alpha = 0.14f),
                        border = previewPalette.border.copy(alpha = 0.34f),
                        textColor = previewPalette.title
                    )
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = previewPalette.container),
                    border = BorderStroke(1.dp, previewPalette.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = previewTitle,
                                modifier = Modifier.weight(1f),
                                color = previewPalette.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 2,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$selectedDayLabel  ${minutesToEditorLabel(selectedStartMinutes)}-${minutesToEditorLabel(safeSelectedEndMinutes)}",
                                color = previewPalette.meta,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            text = previewTeacher,
                            color = previewPalette.meta,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = room.trim().ifBlank { "Bez mistnosti" },
                                color = previewPalette.meta,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SmallLessonBadge(
                                label = selectedWeekLabel,
                                background = previewPalette.border.copy(alpha = 0.14f),
                                border = previewPalette.border.copy(alpha = 0.30f),
                                textColor = previewPalette.title
                            )
                        }
                    }
                }

                DialogSectionCard(title = "Nazev a detail") {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {
                            subject = it
                            errorMessage = null
                        },
                        label = { Text("Nazev") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = room,
                        onValueChange = {
                            room = it
                            errorMessage = null
                        },
                        label = { Text("Mistnost (volitelne)") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = detail,
                        onValueChange = {
                            detail = it
                            errorMessage = null
                        },
                        label = { Text("Detail (volitelne)") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DialogSectionCard(title = "Kdy") {
                    Text(
                        text = "Den",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionPillRow(
                        options = dayOptions,
                        selectedKey = selectedDay,
                        onSelect = { value ->
                            selectedDay = value
                            errorMessage = null
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownSelectionField(
                            label = "Od",
                            value = minutesToEditorLabel(selectedStartMinutes),
                            options = startOptions.map { minutes ->
                                DropdownOption(
                                    key = minutes.toString(),
                                    label = minutesToEditorLabel(minutes)
                                )
                            },
                            onSelect = { value ->
                                val newStartMinutes = value.toInt()
                                selectedStartMinutes = newStartMinutes
                                if (safeSelectedEndMinutes <= newStartMinutes) {
                                    selectedEndMinutes = endOptions.first { it > newStartMinutes }
                                }
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DropdownSelectionField(
                            label = "Do",
                            value = minutesToEditorLabel(safeSelectedEndMinutes),
                            options = endBoundaryOptions.map { minutes ->
                                DropdownOption(
                                    key = minutes.toString(),
                                    label = minutesToEditorLabel(minutes)
                                )
                            },
                            onSelect = { value ->
                                selectedEndMinutes = value.toInt()
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                DialogSectionCard(title = "Vzhled") {
                    Text(
                        text = "Barva aktivity",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorSelectionGrid(
                        options = colorOptions,
                        selectedKey = selectedColorType,
                        onSelect = { value ->
                            selectedColorType = value
                            errorMessage = null
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Opakovani",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionPillRow(
                        options = weekOptions,
                        selectedKey = weekPattern,
                        onSelect = { value ->
                            weekPattern = value
                            errorMessage = null
                        }
                    )
                }

                Text(
                    text = "Cas vybiras z pripravenych slotu rozvrhu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                errorMessage?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f))
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrusit")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val validation = validateCustomLesson(
                                subject = subject,
                                day = selectedDay,
                                startMinutes = selectedStartMinutes,
                                endMinutes = safeSelectedEndMinutes,
                                room = room,
                                detail = detail,
                                colorType = selectedColorType,
                                weekPattern = weekPattern
                            )
                            val lesson = validation.lesson
                            if (lesson != null) {
                                onConfirm(lesson)
                            } else {
                                errorMessage = validation.errorMessage
                            }
                        },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Ulozit")
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SelectionPillRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SelectionPill(
                label = option.second,
                selected = option.first == selectedKey,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { onSelect(option.first) }
            )
        }
    }
}

private data class CustomColorChoice(
    val key: String,
    val label: String,
    val accent: Color
)

@Composable
private fun ColorSelectionGrid(
    options: List<CustomColorChoice>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    ColorSelectionTile(
                        option = option,
                        selected = option.key == selectedKey,
                        onClick = { onSelect(option.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorSelectionTile(
    option: CustomColorChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = if (selected) option.accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) option.accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(option.accent, CircleShape)
                .border(1.dp, option.accent.copy(alpha = 0.36f), CircleShape)
        )
        Text(
            text = option.label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp
        )
        Text(
            text = if (selected) "Vybrano" else "Vybrat",
            color = if (selected) option.accent else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SelectionPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    leadingColor: Color? = null
) {
    Row(
        modifier = Modifier
            .background(
                color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(leadingColor, CircleShape)
                    .border(1.dp, leadingColor.copy(alpha = 0.38f), CircleShape)
            )
        }
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

private data class DropdownOption(
    val key: String,
    val label: String
)

@Composable
private fun DropdownSelectionField(
    label: String,
    value: String,
    options: List<DropdownOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Zmenit",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSelect(option.key)
                        }
                    )
                }
            }
        }
    }
}

private data class CustomLessonValidationResult(
    val lesson: Lesson? = null,
    val errorMessage: String? = null
)

private fun validateCustomLesson(
    subject: String,
    day: String,
    startMinutes: Int,
    endMinutes: Int,
    room: String,
    detail: String,
    colorType: String,
    weekPattern: String
): CustomLessonValidationResult {
    val normalizedSubject = subject.trim()
    if (normalizedSubject.isBlank()) {
        return CustomLessonValidationResult(errorMessage = "Nazev aktivity je povinny.")
    }

    if (endMinutes <= startMinutes) {
        return CustomLessonValidationResult(errorMessage = "Cas konce musi byt pozdeji nez zacatek.")
    }

    return CustomLessonValidationResult(
        lesson = Lesson(
            id = UUID.randomUUID().toString(),
            subject = normalizedSubject,
            teacher = detail.trim(),
            room = room.trim(),
            day = normalizeDayForUi(day),
            time = "${minutesToEditorLabel(startMinutes)} - ${minutesToEditorLabel(endMinutes)}",
            type = normalizeCustomColorType(colorType),
            weekPattern = normalizeWeekPatternCode(weekPattern),
            isCustom = true
        )
    )
}

private fun predefinedCustomTimeSlots(): List<Pair<Int, Int>> {
    return buildTeachingSlots(emptyList())
}

private fun normalizeCustomColorType(rawType: String): String {
    return when (rawType.trim().lowercase(Locale.ROOT)) {
        "cviko", "prednaska", "lab", "sport", "custom_orange", "custom_pink", "custom_indigo", "custom_mint" -> rawType.trim().lowercase(Locale.ROOT)
        else -> "other"
    }
}

private fun minutesToEditorLabel(totalMinutes: Int): String {
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}
@Composable
private fun lessonTypePaletteForGrid(type: String): LessonTypePalette {
    val accent = when (type.trim().lowercase(Locale.ROOT)) {
        "cviko" -> colorResource(R.color.lesson_accent_cviko)
        "prednaska" -> colorResource(R.color.lesson_accent_prednaska)
        "lab" -> colorResource(R.color.lesson_accent_lab)
        "sport" -> colorResource(R.color.lesson_accent_sport)
        "custom_orange" -> colorResource(R.color.lesson_accent_custom_orange)
        "custom_pink" -> colorResource(R.color.lesson_accent_custom_pink)
        "custom_indigo" -> colorResource(R.color.lesson_accent_custom_indigo)
        "custom_mint" -> colorResource(R.color.lesson_accent_custom_mint)
        else -> colorResource(R.color.lesson_accent_default)
    }

    return LessonTypePalette(
        container = accent.copy(alpha = UiColorConfig.CardFillAlpha),
        border = accent.copy(alpha = UiColorConfig.CardBorderAlpha),
        title = colorResource(R.color.app_card_title),
        meta = colorResource(R.color.app_card_meta)
    )
}


private data class StudySectionBlock(
    val title: String,
    val items: List<StudyField>
)

@Composable
private fun StudyInfoTab(
    studyInfo: StudyInfoData?,
    onRefreshFromEdison: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "Studium",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onRefreshFromEdison,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Nacist z Edisonu")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (studyInfo == null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
            ) {
                Text(
                    text = "Data o studiu zatim nejsou nactena. Klikni na Nacist z Edisonu.",
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
            return
        }

        val personalSections = remember(studyInfo.personal) { buildPersonalSections(studyInfo.personal) }
        val matricSections = remember(studyInfo.matriculation) { buildMatriculationSections(studyInfo.matriculation) }
        val admissionSections = remember(studyInfo.admission) { buildAdmissionSections(studyInfo.admission) }
        val matricProgramTitle = remember(studyInfo.matriculation) {
            studyInfo.matriculation
                ?.sections
                ?.firstOrNull { section -> section.title.contains("-") }
                ?.title
                .orEmpty()
        }

        if (personalSections.isNotEmpty()) {
            StudyPageCard(
                cardTitle = "Osobni udaje",
                accent = MaterialTheme.colorScheme.primary,
                sections = personalSections
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (matricSections.isNotEmpty()) {
            StudyPageCard(
                cardTitle = "Matricni udaje",
                subtitle = matricProgramTitle,
                accent = MaterialTheme.colorScheme.tertiary,
                sections = matricSections
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (admissionSections.isNotEmpty()) {
            StudyPageCard(
                cardTitle = "Prijimaci rizeni",
                accent = MaterialTheme.colorScheme.secondary,
                sections = admissionSections
            )
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun StudyPageCard(
    cardTitle: String,
    subtitle: String = "",
    accent: Color,
    sections: List<StudySectionBlock>
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = cardTitle,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            sections.forEachIndexed { index, section ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    )
                }

                Text(
                    text = section.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                section.items.forEachIndexed { itemIndex, field ->
                    StudyFieldLine(field)
                    if (itemIndex < section.items.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyFieldLine(field: StudyField) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = field.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.52f)
        )
        Text(
            text = field.value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.48f)
        )
    }
}

private fun buildPersonalSections(page: StudyPageData?): List<StudySectionBlock> {
    if (page == null) return emptyList()

    return listOfNotNull(
        sectionBlock(
            title = "Kontakt",
            selectedField("Jmeno", page.findFieldValue("jmeno")),
            selectedField("Skolni email", page.findFieldValue("email")),
            selectedField("Kontaktni email", page.findFieldValue("kontaktni email")),
            selectedField("Telefon", page.findFieldValue("telefon"))
        ),
        sectionBlock(
            title = "Zakladni udaje",
            selectedField("Datum narozeni", page.findFieldValue("datum narozeni")),
            selectedField("Pohlavi", page.findFieldValue("pohlavi")),
            selectedField("Statni prislusnost", page.findFieldValue("statni prislusnost")),
            selectedField("Misto narozeni", page.findFieldValue("misto narozeni"))
        ),
        sectionBlock(
            title = "Adresa pobytu",
            selectedField("Ulice", page.findFieldValue("ulice")),
            selectedField("Obec", page.findFieldValue("obec")),
            selectedField("PSC", page.findFieldValue("psc")),
            selectedField("Stat", page.findFieldValue("stat"))
        )
    )
}

private fun buildMatriculationSections(page: StudyPageData?): List<StudySectionBlock> {
    if (page == null) return emptyList()

    return listOfNotNull(
        sectionBlock(
            title = "Stav studia",
            selectedField("Datum overeni", page.findFieldValue("datum overeni")),
            selectedField("Pocet soubeznych studii", page.findFieldValue("pocet soubeznych studii")),
            selectedField("Odstudovana cast", page.findFieldValue("odstudovana cast studia")),
            selectedField("Delka studia", page.findFieldValue("delka studia"))
        ),
        sectionBlock(
            title = "Aktualni etapa",
            selectedField("Udalost", page.findTableValue("udalost")),
            selectedField("Platnost od", page.findTableValue("platnost od")),
            selectedField("Forma studia", page.findTableValue("forma studia")),
            selectedField("Stav", page.findTableValue("stav"))
        ),
        sectionBlock(
            title = "Finance",
            selectedField("Financovano CR", page.findFieldValue("financovano cr")),
            selectedField("Platba za dalsi studium", page.findFieldValue("platba za dalsi studium od data")),
            selectedField("Platba za delsi studium", page.findFieldValue("platba za delsi studium od data"))
        )
    )
}

private fun buildAdmissionSections(page: StudyPageData?): List<StudySectionBlock> {
    if (page == null) return emptyList()

    return listOfNotNull(
        sectionBlock(
            title = "Prihlaska",
            selectedField("Stav prihlasky", page.findFieldValue("stav prihlasky")),
            selectedField("Rozhodnuti", page.findFieldValue("rozhodnuti")),
            selectedField("Body za prijimaci rizeni", page.findFieldValue("body za prijimaci rizeni")),
            selectedField("Body navic", page.findFieldValue("body navic"))
        ),
        sectionBlock(
            title = "Program",
            selectedField("Program", page.findFieldValue("program")),
            selectedField("Forma studia", page.findFieldValue("forma studia")),
            selectedField("Typ studia", page.findFieldValue("typ studia")),
            selectedField("Misto vyuky", page.findFieldValue("misto vyuky"))
        ),
        sectionBlock(
            title = "Predchozi stredni skola",
            selectedField("Nazev skoly", page.findFieldValue("nazev stredni skoly")),
            selectedField("Obor", page.findFieldValue("nazev oboru")),
            selectedField("Rok maturity", page.findFieldValue("rok mat")),
            selectedField("Studijni prumer", page.findTableValue("celkovy"))
        )
    )
}

private fun sectionBlock(title: String, vararg fields: StudyField?): StudySectionBlock? {
    val items = fields.filterNotNull()
    if (items.isEmpty()) return null
    return StudySectionBlock(title = title, items = items)
}

private fun selectedField(label: String, value: String): StudyField? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    return StudyField(label = label, value = normalized)
}

private fun StudyPageData.findFieldValue(vararg hints: String): String {
    if (hints.isEmpty()) return ""
    val normalizedHints = hints.map { normalizeStudyToken(it) }

    return sections.asSequence()
        .flatMap { section -> section.fields.asSequence() }
        .firstOrNull { field ->
            field.value.isNotBlank() && normalizedHints.any { hint ->
                normalizeStudyToken(field.label).contains(hint)
            }
        }
        ?.value
        .orEmpty()
}

private fun StudyPageData.findTableValue(columnHint: String): String {
    val normalizedHint = normalizeStudyToken(columnHint)

    tables.forEach { table ->
        if (table.rows.isEmpty()) return@forEach
        val columnIndex = table.columns.indexOfFirst { column ->
            normalizeStudyToken(column).contains(normalizedHint)
        }
        if (columnIndex >= 0 && table.rows.first().size > columnIndex) {
            val value = table.rows.first()[columnIndex].trim()
            if (value.isNotBlank()) {
                return value
            }
        }
    }

    return ""
}

private fun normalizeStudyToken(raw: String): String {
    val noDiacritics = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    return noDiacritics
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
@Composable
private fun ResultsTab(
    currentResults: CurrentResultsData?,
    onRefreshFromEdison: () -> Unit
) {
    val scrollState = rememberScrollState()
    val items = currentResults?.items.orEmpty()
    var semesterFilter by rememberSaveable { mutableStateOf(ResultSemesterFilter.WINTER) }
    val filteredItems = remember(items, semesterFilter) {
        items.filter { matchesSemesterFilter(it.semesterCode, semesterFilter) }
    }

    val gradedCount = remember(filteredItems) {
        filteredItems.count { hasUsableGrade(it.grade) }
    }

    val finishedCount = remember(filteredItems) {
        filteredItems.count { it.totalPoints.isNotBlank() && it.totalPoints != "..." }
    }

    val averageGrade = remember(filteredItems) {
        val grades = filteredItems.mapNotNull { it.grade.toDoubleOrNull() }
        if (grades.isEmpty()) null else grades.average()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "V\u00fdsledky",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onRefreshFromEdison,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Na\u010d\u00edst z Edisonu")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (currentResults == null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
            ) {
                Text(
                    text = "Data zat\u00edm nejsou na\u010dtena. Klikni na Na\u010d\u00edst z Edisonu.",
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
            return
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                if (currentResults.academicYear.isNotBlank()) {
                    Text(
                        text = currentResults.academicYear,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = "$gradedCount z ${filteredItems.size} p\u0159edm\u011bt\u016f m\u00e1 u\u017e zn\u00e1mku",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Uzav\u0159eno bodov\u011b: $finishedCount",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )

                if (averageGrade != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pr\u016fm\u011brn\u00e1 zn\u00e1mka: ${String.format(Locale.ROOT, "%.2f", averageGrade)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (currentResults.warningNote.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = currentResults.warningNote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResultSemesterFilter.entries.forEach { option ->
                val selected = option == semesterFilter
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { semesterFilter = option }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = option.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredItems.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            ) {
                Text(
                    text = "Pro vybran\u00fd semestr zat\u00edm nejsou dostupn\u00e9 \u017e\u00e1dn\u00e9 p\u0159edm\u011bty.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        filteredItems.forEach { item ->
            ResultItemCard(item = item)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun ResultItemCard(
    item: CurrentResultItem
) {
    var isDetailExpanded by rememberSaveable(
        item.semesterCode,
        item.subjectNumber,
        item.subjectShortcut
    ) { mutableStateOf(false) }
    val detail = item.detail

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.subjectShortcut.ifBlank { item.subjectNumber },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Zn\u00e1mka",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = displayGrade(item.grade),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.subjectName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 19.sp
            )

            if (item.subjectNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subjectNumber,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultValueChip("Z\u00e1po\u010det", item.creditPoints)
                ResultValueChip("Zkou\u0161ka", item.examPoints)
                ResultValueChip("Celkem", item.totalPoints)
            }

            if (detail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isDetailExpanded) "Skr\u00fdt detail p\u0159edm\u011btu" else "Zobrazit detail p\u0159edm\u011btu",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { isDetailExpanded = !isDetailExpanded }
                )

                if (isDetailExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultDetailBlock(detail = detail)
                }
            }
        }
    }
}

@Composable
private fun ResultDetailBlock(detail: cz.tal0052.edisonrozvrh.data.parser.CurrentResultDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (detail.semester.isNotBlank()) {
            ResultDetailLine(label = "Semestr", value = detail.semester)
        }
        if (detail.program.isNotBlank()) {
            ResultDetailLine(label = "Program", value = detail.program)
        }
        if (detail.form.isNotBlank()) {
            ResultDetailLine(label = "Forma", value = detail.form)
        }
        if (detail.studyType.isNotBlank()) {
            ResultDetailLine(label = "Typ", value = detail.studyType)
        }

        if (detail.totalRows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Souhrn",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            detail.totalRows.forEach { row ->
                ResultDetailLine(
                    label = row.label.ifBlank { "Polo\u017eka" },
                    value = listOf(row.points, row.rating)
                        .filter { it.isNotBlank() }
                        .joinToString(" | ")
                        .ifBlank { "-" }
                )
            }
        }

        if (detail.checkpoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "D\u00edl\u010d\u00ed podm\u00ednky",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            detail.checkpoints.forEachIndexed { index, checkpoint ->
                val checkpointLabel = checkpoint.label.ifBlank { "Polo\u017eka" }
                val checkpointPoints = checkpoint.points.takeIf { it.isNotBlank() } ?: "..."
                val checkpointStatus = checkpoint.status.takeIf { it.isNotBlank() } ?: "-"

                ResultCheckpointLine(
                    label = checkpointLabel,
                    requirement = checkpoint.requirement,
                    points = checkpointPoints,
                    status = checkpointStatus,
                    showDivider = index < detail.checkpoints.lastIndex
                )
            }
        }
    }
}

@Composable
private fun ResultDetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.52f)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.48f)
        )
    }
}

@Composable
private fun ResultCheckpointLine(
    label: String,
    requirement: String,
    points: String,
    status: String,
    showDivider: Boolean
) {
    val statusNormalized = status.trim().lowercase(Locale.ROOT)
    val statusColor = when {
        statusNormalized.contains("nespl") || statusNormalized == "no" -> MaterialTheme.colorScheme.error
        statusNormalized.contains("spln") || statusNormalized == "ok" || statusNormalized == "ano" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bodyText = if (requirement.isNotBlank()) "Body: $points / $requirement" else "Body: $points"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.62f)
            )
            Column(
                modifier = Modifier.weight(0.38f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = bodyText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Stav: $status",
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
        }

        if (showDivider) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
private fun matchesSemesterFilter(
    semesterCode: String,
    filter: ResultSemesterFilter
): Boolean {
    val trimmed = semesterCode.trim()
    val first = trimmed.firstOrNull()?.uppercaseChar()
    if (first == 'Z') return filter == ResultSemesterFilter.WINTER
    if (first == 'L') return filter == ResultSemesterFilter.SUMMER

    val normalized = trimmed
        .lowercase(Locale.ROOT)
        .replace(" ", "")

    return when (filter) {
        ResultSemesterFilter.WINTER -> normalized.contains("zim")
        ResultSemesterFilter.SUMMER -> normalized.contains("let")
    }
}

private fun hasUsableGrade(grade: String): Boolean {
    val g = grade.trim()
    if (g.isBlank()) return false
    if (g == "." || g == "..." || g.equals("ok", ignoreCase = true)) return false
    return true
}

private fun displayGrade(grade: String): String {
    val g = grade.trim()
    if (!hasUsableGrade(g)) return "-"
    return g
}

@Composable
private fun ResultValueChip(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$label:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value.ifBlank { "-" },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
private fun PlaceholderTab(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 21.sp
                )

            }
        }
    }
}

private fun minutesToLabel(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return String.format(Locale.ROOT, "%d:%02d", h, m)
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

@Composable
private fun rememberCurrentTimeMinutes(): Int {
    val currentMinutes by produceState(initialValue = LocalTime.now().hour * 60 + LocalTime.now().minute) {
        while (true) {
            val now = LocalTime.now()
            value = now.hour * 60 + now.minute
            val millisUntilNextMinute =
                ((60 - now.second) * 1000L - (now.nano / 1_000_000L)).coerceIn(400L, 60_000L)
            delay(millisUntilNextMinute)
        }
    }
    return currentMinutes
}

private fun calculateCurrentTimeLineOffset(
    nowMinutes: Int,
    teachingSlots: List<Pair<Int, Int>>,
    dayLabelWidth: Dp,
    slotSpacing: Dp,
    slotWidth: Dp
): Dp? {
    if (teachingSlots.isEmpty()) return null
    if (nowMinutes < teachingSlots.first().first || nowMinutes > teachingSlots.last().second) return null

    val labelWidth = dayLabelWidth.value
    val spacing = slotSpacing.value
    val slotWidthValue = slotWidth.value
    var cursorX = labelWidth + spacing

    for (index in teachingSlots.indices) {
        val (startMinutes, endMinutes) = teachingSlots[index]

        if (nowMinutes in startMinutes..endMinutes) {
            val duration = (endMinutes - startMinutes).coerceAtLeast(1)
            val fraction = ((nowMinutes - startMinutes).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            return (cursorX + slotWidthValue * fraction).dp
        }

        cursorX += slotWidthValue

        if (index < teachingSlots.lastIndex) {
            val nextStart = teachingSlots[index + 1].first
            if (nowMinutes > endMinutes && nowMinutes < nextStart) {
                val gapDuration = (nextStart - endMinutes).coerceAtLeast(1)
                val gapFraction = ((nowMinutes - endMinutes).toFloat() / gapDuration.toFloat()).coerceIn(0f, 1f)
                return (cursorX + spacing * gapFraction).dp
            }
            cursorX += spacing
        }
    }

    return null
}

private fun buildTeachingSlots(positionedLessons: List<PositionedLesson>): List<Pair<Int, Int>> {
    val defaultStart = (7 * 60) + 15
    val defaultEnd = (19 * 60) + 15
    val maxEnd = maxOf(defaultEnd, positionedLessons.maxOfOrNull { it.endMinutes } ?: defaultEnd)

    val slots = mutableListOf<Pair<Int, Int>>()
    val pattern = intArrayOf(45, 45, 15)
    var current = defaultStart
    var patternIndex = 0

    while (current < maxEnd) {
        val duration = pattern[patternIndex]
        val next = current + duration
        if (patternIndex != 2) {
            slots += current to next
        }
        current = next
        patternIndex = (patternIndex + 1) % pattern.size
    }

    return slots
}
private fun currentCalendarWeekParity(date: LocalDate): WeekParity {
    val weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    return if (weekOfYear % 2 == 0) WeekParity.EVEN else WeekParity.ODD
}

private fun toWeekParity(rawPattern: String): WeekParity {
    return when (normalizeWeekPatternCode(rawPattern)) {
        "odd" -> WeekParity.ODD
        "even" -> WeekParity.EVEN
        else -> WeekParity.EVERY
    }
}












