package cz.tal0052.edisonrozvrh.ui.home

import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.app.PositionedLesson
import cz.tal0052.edisonrozvrh.app.buildPositionedLessonsForDay
import cz.tal0052.edisonrozvrh.app.normalizeWeekPatternCode
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultItem
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.ui.design.LessonTypePalette
import cz.tal0052.edisonrozvrh.ui.design.UiColorConfig

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

private enum class HomeTab(val label: String) {
    UNIVERSITY("Univerzita"),
    RESULTS("Vysledky"),
    EXAMS("Zkousky"),
    SCHEDULE("Rozvrh")
}

private enum class WeekParity(val label: String) {
    ODD("Lichy"),
    EVEN("Sudy"),
    EVERY("Kazdy")
}

private enum class ResultSemesterFilter(val label: String) {
    WINTER("Zimni"),
    SUMMER("Letni")
}

@Composable
fun ScheduleScreen(
    lessons: List<Lesson>,
    currentResults: CurrentResultsData?,
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Text("Q", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                HomeTab.SCHEDULE -> ScheduleGridTab(lessons)
                HomeTab.UNIVERSITY -> PlaceholderTab(
                    title = "Novinky",
                    subtitle = "Harmonogram a dulezite terminy"
                )

                HomeTab.RESULTS -> ResultsTab(
                    currentResults = currentResults,
                    onRefreshFromEdison = onRefreshFromEdison
                )

                HomeTab.EXAMS -> PlaceholderTab(
                    title = "Terminy",
                    subtitle = "Prihlasky na zkousky a zapocty"
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
    val tabs = listOf(HomeTab.UNIVERSITY, HomeTab.RESULTS, HomeTab.EXAMS, HomeTab.SCHEDULE)

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
private fun ScheduleGridTab(lessons: List<Lesson>) {
    val dayOrder = listOf("Pondeli", "Utery", "Streda", "Ctvrtek", "Patek")
    val dayLabels = mapOf(
        "Pondeli" to "Po",
        "Utery" to "Ut",
        "Streda" to "St",
        "Ctvrtek" to "Ct",
        "Patek" to "Pa"
    )

    val lessonsByDay = remember(lessons) {
        dayOrder.associateWith { day -> buildPositionedLessonsForDay(lessons, day) }
    }

    val timeSlots = remember(lessonsByDay) {
        lessonsByDay.values
            .flatten()
            .groupBy { it.startMinutes }
            .toSortedMap()
            .map { (start, rows) -> start to (rows.maxOfOrNull { it.endMinutes } ?: (start + 45)) }
    }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

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

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("VT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Rozvrh",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (timeSlots.isEmpty()) {
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.width(58.dp).height(52.dp))
                        timeSlots.forEach { slot ->
                            Column(modifier = Modifier.width(212.dp)) {
                                Text(
                                    text = minutesToLabel(slot.first),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = minutesToLabel(slot.second),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    dayOrder.forEach { day ->
                        val positioned = lessonsByDay[day].orEmpty()
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(58.dp)
                                    .height(146.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = dayLabels[day].orEmpty(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 24.sp
                                )
                            }

                            timeSlots.forEach { slot ->
                                val item = positioned.firstOrNull { it.startMinutes == slot.first }
                                if (item != null) {
                                    GridLessonCard(item)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(212.dp)
                                            .height(146.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
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
private fun GridLessonCard(item: PositionedLesson) {
    val palette = lessonTypePaletteForGrid(item.lesson.type)
    val weekParity = remember(item.lesson.weekPattern) {
        toWeekParity(item.lesson.weekPattern)
    }

    Card(
        modifier = Modifier.width(212.dp).height(146.dp),
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

                    Text(
                        text = weekParity.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.title,
                        modifier = Modifier
                            .background(
                                color = palette.border.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = palette.border.copy(alpha = 0.44f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CardMetaLine(
                iconRes = R.drawable.ic_teacher,
                text = item.lesson.teacher.ifBlank { "-" },
                textColor = palette.meta
            )

            Spacer(modifier = Modifier.height(6.dp))

            CardMetaLine(
                iconRes = R.drawable.ic_room,
                text = item.lesson.room.ifBlank { "-" },
                textColor = palette.meta
            )
        }
    }
}

@Composable
private fun CardMetaLine(
    iconRes: Int,
    text: String,
    textColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun lessonTypePaletteForGrid(type: String): LessonTypePalette {
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

@Composable
private fun ResultsTab(
    currentResults: CurrentResultsData?,
    onRefreshFromEdison: () -> Unit
) {
    val context = LocalContext.current
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
        Text(
            text = "Vysledky",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onRefreshFromEdison) {
            Text("Nacist z Edisonu")
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

        if (currentResults == null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
            ) {
                Text(
                    text = "Data zatim nejsou nactena. Klikni na Nacist z Edisonu.",
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
                    text = "$gradedCount z ${filteredItems.size} predmetu ma uz znamku",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Uzavreno bodove: $finishedCount",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )

                if (averageGrade != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prumerna znamka: ${String.format(Locale.ROOT, "%.2f", averageGrade)}",
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

        if (filteredItems.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            ) {
                Text(
                    text = "Pro vybrany semestr zatim nejsou dostupne zadne predmety.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        filteredItems.forEach { item ->
            ResultItemCard(
                item = item,
                onOpenDetail = {
                    if (item.detailUrl.isBlank()) return@ResultItemCard
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.detailUrl)))
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun ResultItemCard(
    item: CurrentResultItem,
    onOpenDetail: () -> Unit
) {
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

                Text(
                    text = displayGrade(item.grade),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
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
                ResultValueChip("Zapocet", item.creditPoints)
                ResultValueChip("Zkouska", item.examPoints)
                ResultValueChip("Celkem", item.totalPoints)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResultValueChip("Znamka", displayGrade(item.grade))

                Spacer(modifier = Modifier.weight(1f))

                if (item.detailUrl.isNotBlank()) {
                    Text(
                        text = "Detail",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onOpenDetail() }
                    )
                }
            }
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tenhle tab doplnime daty v dalsim kroku.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
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

private fun toWeekParity(rawPattern: String): WeekParity {
    return when (normalizeWeekPatternCode(rawPattern)) {
        "odd" -> WeekParity.ODD
        "even" -> WeekParity.EVEN
        else -> WeekParity.EVERY
    }
}




















