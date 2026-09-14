package com.halo.yunquevoice.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.halo.yunquevoice.voice.Store
import com.halo.yunquevoice.voice.VoiceMvpLog

/**
 * 蓝牙设备连接自动切换：设备连上时按该设备的档案执行（聆听动作 + 麦克风路由）。
 * 受全局开关管辖（关=直接忽略）；未配置档案的设备不动作。
 */
class BtDeviceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (!Store.btAutoSwitchEnabled(context)) return
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val address = device.address
        val profile = Store.btProfiles(context)[address] ?: return
        val name = runCatching { device.name }.getOrDefault(address.takeLast(5))
        VoiceMvpLog.i("BTSWITCH", "设备已连接：$name（$address）→ 应用档案 listen=${profile.listenAction} mic=${profile.mic}")

        // 麦克风路由（先路由，聆听启动时即生效）
        when (profile.mic) {
            "device" -> {
                // 蓝牙 SCO 输入设备：t<type>:a<address>，TYPE_BLUETOOTH_SCO=7
                Store.saveAudioInputDevice(context, "t7:a$address")
                Store.saveAudioInput(context, Store.AUDIO_EARPHONE)
            }
            "phone" -> {
                Store.saveAudioInputDevice(context, "builtin")
                Store.saveAudioInput(context, Store.AUDIO_PHONE)
            }
        }

        // 聆听动作
        val svc = Intent(context, AlwaysOnListeningService::class.java)
        when (profile.listenAction) {
            "start_normal" -> {
                Store.saveListenOnly(context, false)
                svc.setAction(AlwaysOnListeningService.ACTION_START)
                context.startForegroundService(svc)
            }
            "start_listen_only" -> {
                Store.saveListenOnly(context, true)
                svc.setAction(AlwaysOnListeningService.ACTION_START)
                context.startForegroundService(svc)
            }
            "stop" -> {
                svc.setAction(AlwaysOnListeningService.ACTION_STOP)
                context.startService(svc)
            }
        }
    }
}
