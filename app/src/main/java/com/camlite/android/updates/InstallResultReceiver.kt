package com.camlite.android.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) { UpdateManager.get(context).installResult(intent) }
}
