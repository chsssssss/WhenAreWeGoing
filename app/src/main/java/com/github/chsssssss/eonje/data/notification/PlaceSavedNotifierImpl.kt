package com.github.chsssssss.eonje.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.chsssssss.eonje.R
import com.github.chsssssss.eonje.domain.notification.PlaceSavedNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val CHANNEL_ID = "place_resolved"

class PlaceSavedNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaceSavedNotifier {

    override fun notifyResolved(placeName: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map_pin_highlighted)
            .setContentTitle("$placeName 저장됨")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(placeName.hashCode(), notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "장소 저장 알림", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
