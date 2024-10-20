package dev.nordix.zsi.scanner.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask

class TriggerHandler(
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val context: Context,
) {
    private val timer: Timer = Timer()
    private var triggerDown = false
    private var stopDecodeTask: TimerTask? = null

    private val triggerDownReceiver = TriggerDownReceiver()
    private val triggerUpReceiver = TriggerUpReceiver()

    inner class TriggerDownReceiver : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent) {
            Timber.tag(TAG).v("trigger is DOWN")
            if (triggerDown) return
            triggerDown = true
            onStart()
        }
    }

    inner class TriggerUpReceiver : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent) {
            if (triggerDown) {
                Timber.tag(TAG).v("trigger is UP")
                stopDecodeTask?.cancel()
                stopDecodeTask = object : TimerTask() {
                    override fun run() {
                        onStop()
                    }
                }
                timer.schedule(stopDecodeTask, 200)
                triggerDown = false
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(triggerDownReceiver, IntentFilter(ACTION_TRIGGER_DOWN),
                    Context.RECEIVER_NOT_EXPORTED)
                context.registerReceiver(triggerUpReceiver, IntentFilter(ACTION_TRIGGER_UP),
                    Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(triggerDownReceiver, IntentFilter(ACTION_TRIGGER_DOWN))
                context.registerReceiver(triggerUpReceiver, IntentFilter(ACTION_TRIGGER_UP))
            }
        } catch (e: Exception) {
            Timber.tag(TAG).v(e, "register")
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(triggerDownReceiver)
            context.unregisterReceiver(triggerUpReceiver)
        } catch (e: Exception) {
            Timber.tag(TAG).v(e, "unregister")
        }
    }

    companion object {
        private const val TAG = "TriggerHandler"
        const val ACTION_TRIGGER_DOWN = "TRIGGERBUTTON_DOWN"
        const val ACTION_TRIGGER_UP = "TRIGGERBUTTON_UP"

    }

}
