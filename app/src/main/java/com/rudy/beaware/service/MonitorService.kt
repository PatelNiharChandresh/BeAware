package com.rudy.beaware.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

// TODO: Step 29 — implement foreground service with polling, overlay, and session recording.
@AndroidEntryPoint
class MonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
