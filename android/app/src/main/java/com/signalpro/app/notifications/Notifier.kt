package com.signalpro.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.signalpro.app.MainActivity
import com.signalpro.app.R

/**
 * Notifications locales. Elles portent une information utile et vérifiable
 * (« un numéro confirmé malveillant a une conversation sur votre compte »),
 * jamais un faux message de succès.
 */
class Notifier(private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notif_channel_alerts_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.notif_channel_sync),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_sync_desc) },
        )
    }

    fun notifyMaliciousContact(targetId: Int, maskedPhone: String, category: String, reportCount: Int) {
        notify(
            id = targetId,
            channel = CHANNEL_ALERTS,
            title = "Numéro malveillant confirmé dans vos conversations",
            body = "$maskedPhone ($category) · $reportCount signalements vérifiés. " +
                "Ne répondez pas, conservez les messages, bloquez ce numéro.",
        )
    }

    fun notifyReportUpdate(reportRef: String, status: String) {
        notify(
            id = reportRef.hashCode(),
            channel = CHANNEL_SYNC,
            title = "Signalement $reportRef : $status",
            body = "Ouvrez l'application pour voir le détail et les preuves associées.",
        )
    }

    fun notifySyncSummary(pending: Int, synced: Int) {
        if (pending == 0 && synced == 0) return
        notify(
            id = ID_SYNC,
            channel = CHANNEL_SYNC,
            title = "Synchronisation terminée",
            body = "$synced élément(s) mis à jour, $pending en attente d'envoi.",
        )
    }

    private fun notify(id: Int, channel: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        const val CHANNEL_ALERTS = "signalpro_alerts"
        const val CHANNEL_SYNC = "signalpro_sync"
        private const val ID_SYNC = 9001
    }
}

/** Reçoit les actions différées (ex. relance de synchronisation). */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Aucune action sensible n'est exécutée depuis une diffusion : la
        // synchronisation est relancée par WorkManager, qui applique ses
        // propres contraintes réseau.
        if (intent.action == ACTION_RESYNC) {
            (context.applicationContext as? com.signalpro.app.SignalProApplication)
                ?.container
                ?.scheduleBackgroundSync()
        }
    }

    companion object {
        const val ACTION_RESYNC = "com.signalpro.app.RESYNC"
    }
}
