package cz.tal0052.edisonrozvrh

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_TICK -> {
                refreshAll(context)
            }
        }

        super.onReceive(context, intent)
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            if (ids.isEmpty()) return

            ids.forEach { appWidgetId ->
                updateWidget(context, manager, appWidgetId)
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            val today = LocalDate.now()
            val dayName = today.toCzechShortDay()
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_title_format, dayName))
            views.setTextViewText(
                R.id.widgetNow,
                context.getString(
                    R.string.widget_now_format,
                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
                )
            )

            val launchIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                1000 + appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openAppPending)
            views.setOnClickPendingIntent(R.id.widgetNow, openAppPending)

            val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setRemoteAdapter(R.id.widgetList, serviceIntent)
            views.setEmptyView(R.id.widgetList, R.id.widgetEmpty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal fun LocalDate.toUiDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Pondeli"
        DayOfWeek.TUESDAY -> "Utery"
        DayOfWeek.WEDNESDAY -> "Streda"
        DayOfWeek.THURSDAY -> "Ctvrtek"
        DayOfWeek.FRIDAY -> "Patek"
        DayOfWeek.SATURDAY -> "Patek"
        DayOfWeek.SUNDAY -> "Pondeli"
    }
}

private fun LocalDate.toCzechShortDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Po"
        DayOfWeek.TUESDAY -> "Ut"
        DayOfWeek.WEDNESDAY -> "St"
        DayOfWeek.THURSDAY -> "Ct"
        DayOfWeek.FRIDAY -> "Pa"
        DayOfWeek.SATURDAY -> "So"
        DayOfWeek.SUNDAY -> "Ne"
    }
}
