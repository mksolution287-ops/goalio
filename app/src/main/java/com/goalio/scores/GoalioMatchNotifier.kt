package com.goalio.scores

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object GoalioMatchNotifier {
    private const val CHANNEL_ID = "goalio_live_match_alerts"
    private const val CHANNEL_NAME = "Live match alerts"
    private const val UPCOMING_NOTIFICATION_ID = 90210

    fun scheduleUpcoming(context: Context, matches: List<ScheduleMatch>) {
        val now = System.currentTimeMillis()
        val upcoming = matches.asSequence().filter { it.state == "pre" }
            .mapNotNull { match -> match.kickoffEpochMillis()?.takeIf { it > now }?.let { match to it } }
            .sortedBy { it.second }.take(20).toList()
        if (upcoming.isEmpty()) return
        showUpcomingCountdown(context, upcoming.first().first, upcoming.first().second)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        upcoming.forEach { (match, kickoff) ->
            listOf(24 * 60 * 60 * 1000L to "Match in 24 hours", 60 * 60 * 1000L to "Match in 1 hour", 0L to "Kickoff now")
                .forEach { (before, title) ->
                    val trigger = kickoff - before
                    if (trigger <= now) return@forEach
                    val intent = Intent(context, MatchReminderReceiver::class.java).apply {
                        putExtra("title", title)
                        putExtra("message", match.compactNotificationName())
                        putExtra("kickoff", kickoff)
                    }
                    val requestCode = "${match.league}:${match.matchId}:$before".hashCode()
                    val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                }
        }
    }

    fun notifyBackgroundEvents(context: Context, events: List<MatchNotificationEvent>) {
        if (events.isEmpty() || GoalioAppVisibility.isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val appContext = context.applicationContext
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = NotificationManagerCompat.from(appContext)
        events.forEach { event ->
            val contentText = event.kickoffEpochMillis?.let { kickoff ->
                "${matchClockText(kickoff)} • ${event.message}"
            } ?: event.message
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(event.title)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            event.kickoffEpochMillis?.let { kickoff ->
                builder
                    .setWhen(kickoff)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && kickoff > System.currentTimeMillis()) {
                    builder.setChronometerCountDown(true)
                }
            }
            val notification = builder.build()
            manager.notify(event.id.hashCode(), notification)
        }
    }

    fun showScheduled(context: Context, title: String, message: String, kickoff: Long) {
        if (!canNotify(context)) return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            "$title:$message".hashCode(),
            baseBuilder(context, title, message).setWhen(kickoff).setShowWhen(true).build()
        )
    }

    private fun showUpcomingCountdown(context: Context, match: ScheduleMatch, kickoff: Long) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val text = "${match.compactNotificationName()} • ${matchClockText(kickoff)}"
        val builder = baseBuilder(context, "Next match", text)
            .setWhen(kickoff).setShowWhen(true).setUsesChronometer(true).setOngoing(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
        NotificationManagerCompat.from(context).notify(UPCOMING_NOTIFICATION_ID, builder.build())
    }

    private fun baseBuilder(context: Context, title: String, text: String): NotificationCompat.Builder {
        val launch = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(launch)
    }

    private fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when tracked football matches start or the score changes."
            }
        )
    }

    private fun matchClockText(kickoffEpochMillis: Long): String {
        val diffSeconds = ((kickoffEpochMillis - System.currentTimeMillis()) / 1000L)
        val prefix = if (diffSeconds >= 0) "Starts in" else "Live"
        val total = kotlin.math.abs(diffSeconds)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return "$prefix %02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

class MatchReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GoalioMatchNotifier.showScheduled(context, intent.getStringExtra("title") ?: "Match reminder",
            intent.getStringExtra("message") ?: "Upcoming match", intent.getLongExtra("kickoff", System.currentTimeMillis()))
    }
}
