package cz.tal0052.edisonrozvrh.ui.home

import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.Lesson
import cz.tal0052.edisonrozvrh.app.PositionedLesson
import cz.tal0052.edisonrozvrh.app.buildPositionedLessonsForDay
import cz.tal0052.edisonrozvrh.app.normalizeWeekPatternCode
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultItem
import cz.tal0052.edisonrozvrh.data.parser.CurrentResultsData
import cz.tal0052.edisonrozvrh.data.parser.StudyField
import cz.tal0052.edisonrozvrh.data.parser.StudyInfoData
import cz.tal0052.edisonrozvrh.data.parser.StudyPageData
import cz.tal0052.edisonrozvrh.ui.design.LessonTypePalette
import cz.tal0052.edisonrozvrh.ui.design.UiColorConfig


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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay

private enum class HomeTab(val label: String) {
    UNIVERSITY("Studium"),
    RESULTS("V\u00fdsledky"),
    EXAMS("Zkou\u0161ky"),
    SCHEDULE("Rozvrh")
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

@Composable
fun ScheduleScreen(
    lessons: List<Lesson>,
    currentResults: CurrentResultsData?,
    studyInfo: StudyInfoData?,
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
                HomeTab.SCHEDULE -> ScheduleGridTab(lessons)
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
                    subtitle = "P\u0159ihl\u00e1\u0161ky na zkou\u0161ky a z\u00e1po\u010dty"
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

        if (teachingSlots.isEmpty()) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
            ) {
                Text(
                    text = "V tomhle t\u00fddnu nejsou v rozvrhu \u017e\u00e1dn\u00e9 hodiny.",
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
                                        cardHeight = lessonRowHeight
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
}

@Composable
private fun GridLessonCard(
    item: PositionedLesson,
    cardWidth: Dp,
    cardHeight: Dp
) {
    val palette = lessonTypePaletteForGrid(item.lesson.type)
    val weekParity = remember(item.lesson.weekPattern) {
        toWeekParity(item.lesson.weekPattern)
    }

    Card(
        modifier = Modifier.width(cardWidth).height(cardHeight),
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

                if (weekParity != WeekParity.EVERY) {
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tenhle tab dopln\u00edme daty v dal\u0161\u00edm kroku.",
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
private fun toWeekParity(rawPattern: String): WeekParity {
    return when (normalizeWeekPatternCode(rawPattern)) {
        "odd" -> WeekParity.ODD
        "even" -> WeekParity.EVEN
        else -> WeekParity.EVERY
    }
}



