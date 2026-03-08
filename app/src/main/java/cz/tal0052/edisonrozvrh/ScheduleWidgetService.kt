package cz.tal0052.edisonrozvrh

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetFactory(applicationContext)
    }
}

private data class WidgetLessonItem(
    val positioned: PositionedLesson,
    val roomText: String,
    val gapBeforeMinutes: Int,
    val rowStartMinutes: Int
)

private class ScheduleWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetLessonItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        try {
            val lessons = loadSchedule(context).orEmpty()
            val day = LocalDate.now().toUiDay()
            val positioned = buildPositionedLessonsForDay(lessons, day).take(8)

            if (positioned.isEmpty()) {
                items = emptyList()
                return
            }

            var previousEnd = minOf(7 * 60, positioned.first().startMinutes)
            items = positioned.map { item ->
                val gapBefore = (item.startMinutes - previousEnd).coerceAtLeast(0)
                val rowStart = item.startMinutes - gapBefore
                previousEnd = max(previousEnd, item.endMinutes)

                WidgetLessonItem(
                    positioned = item,
                    roomText = item.lesson.room.trim(),
                    gapBeforeMinutes = gapBefore,
                    rowStartMinutes = rowStart
                )
            }
        } catch (_: Throwable) {
            items = emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        return try {
            if (position !in items.indices) {
                return RemoteViews(context.packageName, R.layout.widget_schedule_item)
            }

            val item = items[position]
            val lesson = item.positioned.lesson
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_item)

            val pxPerMinute = context.resources.displayMetrics.density * 0.72f
            val gapPx = (item.gapBeforeMinutes * pxPerMinute).roundToInt().coerceAtLeast(0)
            val durationMinutes = (item.positioned.endMinutes - item.positioned.startMinutes).coerceAtLeast(30)
            val minBubblePx = dpToPx(context, 58)
            val durationPx = max((durationMinutes * pxPerMinute).roundToInt(), minBubblePx)
            val rowHeightPx = gapPx + durationPx

            views.setViewPadding(R.id.itemContentRow, 0, gapPx, 0, 0)
            views.setInt(R.id.widgetBubble, "setMinimumHeight", durationPx)

            views.setTextViewText(R.id.itemTime, item.positioned.shownTime)
            views.setTextViewText(R.id.itemSubject, lesson.subject)
            views.setTextViewText(R.id.itemRoom, item.roomText.ifBlank { "-" })

            val bubbleBg = when (lesson.type.trim().lowercase(Locale.ROOT)) {
                "cviko" -> R.drawable.widget_card_bg_cviko
                "prednaska" -> R.drawable.widget_card_bg_prednaska
                "lab" -> R.drawable.widget_card_bg_lab
                "sport" -> R.drawable.widget_card_bg_sport
                else -> R.drawable.widget_card_bg_default
            }
            views.setInt(R.id.widgetBubble, "setBackgroundResource", bubbleBg)

            val nowMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute
            val rowStart = item.rowStartMinutes
            val rowEnd = item.positioned.endMinutes

            if (nowMinutes in rowStart..rowEnd) {
                val lineHeightPx = dpToPx(context, 3)
                val rawLineY = ((nowMinutes - rowStart) * pxPerMinute).roundToInt()
                val lineY = rawLineY.coerceIn(0, max(0, rowHeightPx - lineHeightPx))

                views.setViewVisibility(R.id.itemNowLine, View.VISIBLE)
                views.setFloat(R.id.itemNowLine, "setTranslationY", lineY.toFloat())
            } else {
                views.setViewVisibility(R.id.itemNowLine, View.GONE)
                views.setFloat(R.id.itemNowLine, "setTranslationY", 0f)
            }

            views
        } catch (_: Throwable) {
            RemoteViews(context.packageName, R.layout.widget_schedule_item)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

private fun parseWidgetTimeRange(value: String): Pair<LocalTime, LocalTime>? {
    val match = Regex("(\\d{1,2})\\s*:\\s*(\\d{2})\\s*[-–]\\s*(\\d{1,2})\\s*:\\s*(\\d{2})")
        .find(value)
        ?: return null

    val startHour = match.groupValues[1].toIntOrNull() ?: return null
    val startMinute = match.groupValues[2].toIntOrNull() ?: return null
    val endHour = match.groupValues[3].toIntOrNull() ?: return null
    val endMinute = match.groupValues[4].toIntOrNull() ?: return null

    if (startHour !in 0..23 || endHour !in 0..23 || startMinute !in 0..59 || endMinute !in 0..59) {
        return null
    }

    val start = LocalTime.of(startHour, startMinute)
    val end = LocalTime.of(endHour, endMinute)
    return start to if (end <= start) start.plusMinutes(45) else end
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).roundToInt()
}
