package com.example.flashlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class FlashlightActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "FLASHLIGHT_OFF") {
            Handler(Looper.getMainLooper()).post {
                FlashlightServiceHolder.service?.turnOffFromNotification()
            }
        }
    }
}
