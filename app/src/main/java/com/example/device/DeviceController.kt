package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val health: String = "Good",
    val temperature: Float = 25.0f,
    val chargePlug: String = "Battery"
)

class DeviceController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    init {
        updateBatteryStatus()
    }

    fun toggleFlashlight(forceState: Boolean? = null): Boolean {
        val manager = cameraManager ?: return false
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: manager.cameraIdList.firstOrNull() ?: return false

            val newState = forceState ?: !_isFlashlightOn.value
            manager.setTorchMode(cameraId, newState)
            _isFlashlightOn.value = newState
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun makeCall(contactOrNumber: String): String {
        val trimmed = contactOrNumber.trim()
        val phoneNumber = if (trimmed.matches(Regex("^[+0-9\\-\\s]+$"))) {
            trimmed
        } else {
            // Attempt to search contacts for name
            findContactNumber(trimmed) ?: trimmed
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        try {
            context.startActivity(intent)
            return "Dialing $phoneNumber"
        } catch (e: Exception) {
            return "Could not place call: ${e.message}"
        }
    }

    private fun findContactNumber(name: String): String? {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) return null

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex >= 0) {
                        return it.getString(numberIndex)
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    fun updateBatteryStatus(): BatteryInfo {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level = batteryStatus?.let { intent ->
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) (rawLevel * 100 / scale) else 75
        } ?: 75

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.let { it / 10f } ?: 28.5f

        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "On Battery"
        }

        val info = BatteryInfo(
            level = level,
            isCharging = isCharging,
            health = "Optimal (98%)",
            temperature = temp,
            chargePlug = plugType
        )
        _batteryInfo.value = info
        return info
    }

    fun adjustVolume(direction: String) {
        val am = audioManager ?: return
        val dir = when (direction.uppercase()) {
            "UP" -> AudioManager.ADJUST_RAISE
            "DOWN" -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_SAME
        }
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    fun setMediaVolumePercent(percent: Float) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent.coerceIn(0f, 1f)).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    fun getMediaVolumePercent(): Float {
        val am = audioManager ?: return 0.5f
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return current.toFloat() / max.toFloat()
    }

    fun openWifiSettings() {
        openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun openBluetoothSettings() {
        openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openDisplaySettings() {
        openIntent(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    fun openSoundSettings() {
        openIntent(Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    fun openCamera() {
        openIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    fun launchAppByName(name: String): Boolean {
        val pm = context.packageManager
        val packageName = when (name.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "youtube" -> "com.google.android.youtube"
            "chrome" -> "com.android.chrome"
            "camera" -> {
                openCamera()
                return true
            }
            "settings" -> {
                openIntent(Intent(Settings.ACTION_SETTINGS))
                return true
            }
            else -> null
        }

        if (packageName != null) {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                openIntent(launchIntent)
                return true
            }
        }

        // Search installed apps for match
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.contains(name, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    openIntent(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    private fun openIntent(intent: Intent) {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(intent)
        } catch (e: Exception) {}
    }
}
