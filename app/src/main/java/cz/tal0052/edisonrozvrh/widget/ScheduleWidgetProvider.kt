package cz.tal0052.edisonrozvrh.widget

import cz.tal0052.edisonrozvrh.R
import cz.tal0052.edisonrozvrh.app.MainActivity

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
                ScheduleWidgetSnapshotProvider.refreshAll(context)
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

            views.setImageViewResource(R.id.widgetOverlayLogo, R.drawable.logo_emblem)

            val openAppPending = buildOpenAppPendingIntent(context, 1000 + appWidgetId)
            views.setOnClickPendingIntent(R.id.widgetTitle, openAppPending)
            views.setOnClickPendingIntent(R.id.widgetNow, openAppPending)
            views.setOnClickPendingIntent(R.id.widgetHeader, openAppPending)

            val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetList, serviceIntent)
            views.setEmptyView(R.id.widgetList, R.id.widgetEmpty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal fun buildOpenAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val launchIntent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context,
        requestCode,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun LocalDate.toUiDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Pondeli"
        DayOfWeek.TUESDAY -> "Utery"
        DayOfWeek.WEDNESDAY -> "Streda"
        DayOfWeek.THURSDAY -> "Ctvrtek"
        DayOfWeek.FRIDAY -> "Patek"
        DayOfWeek.SATURDAY -> "Sobota"
        DayOfWeek.SUNDAY -> "Nedele"
    }
}

internal fun LocalDate.toCzechShortDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Po"
        DayOfWeek.TUESDAY -> "\u00dat"
        DayOfWeek.WEDNESDAY -> "St"
        DayOfWeek.THURSDAY -> "\u010ct"
        DayOfWeek.FRIDAY -> "P\u00e1"
        DayOfWeek.SATURDAY -> "So"
        DayOfWeek.SUNDAY -> "Ne"
    }
}

internal fun LocalDate.toCzechLongDay(): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Pond\u011bl\u00ed"
        DayOfWeek.TUESDAY -> "\u00dater\u00fd"
        DayOfWeek.WEDNESDAY -> "St\u0159eda"
        DayOfWeek.THURSDAY -> "\u010ctvrtek"
        DayOfWeek.FRIDAY -> "P\u00e1tek"
        DayOfWeek.SATURDAY -> "Sobota"
        DayOfWeek.SUNDAY -> "Ned\u011ble"
    }
}


