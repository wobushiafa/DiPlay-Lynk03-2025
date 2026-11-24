package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb

/**
 * Identifies the Geely / Lynk & Co head unit (`geely_fx11`, model `CS11_A4`, DesaySV, Android 11)
 * that the wheel-key support was written and tested on.
 *
 * Confirmed on the car: it maps the steering-wheel keys to vendor-private key codes and routes them
 * only through its own input service, so no standard Android `KeyEvent` reaches a media session or
 * an accessibility service. [com.shilapi.xcertplay.GeelyWheelKeys] subscribes to that service, and
 * [com.shilapi.xcertplay.media.MicrophoneUplink] uses the voice-communication audio source for Siri,
 * which is the only source this firmware opens the microphone for.
 *
 * Every use is gated by [detected], so other head units keep the stock behaviour, and unit tests
 * (where `Build` reports Robolectric) never take this path.
 */
object GeelyHeadUnit {

    private val identifiers: List<String>
        get() = runCatching {
            listOf(Build.PRODUCT, Build.DEVICE, Build.MODEL, Build.MANUFACTURER, Build.BRAND)
                .map { it.lowercase() }
        }.getOrDefault(emptyList())

    /** Product code of the head unit. */
    private const val PRODUCT = "geely_fx11"

    /** Model name the head unit reports. */
    private const val MODEL = "cs11_a4"

    val detected: Boolean = identifiers.any { id ->
        id == PRODUCT || id == MODEL || id.contains("geely") || id.contains("lynk")
    }

    /**
     * Toggles Android's own NXP Wi-Fi radio (`wlan0`/`p2p0`) on Geely/Lynk dual-Wi-Fi head units.
     * Tries the legacy WifiManager setter first, then falls back to local ADB (`svc wifi enable/disable`).
     */
    @Suppress("DEPRECATION")
    fun setAndroidWifiEnabled(
        context: Context,
        enabled: Boolean,
        log: (String) -> Unit = {},
    ): Boolean {
        if (!detected) return false
        val app = context.applicationContext
        val wifi = app.getSystemService(WifiManager::class.java) ?: return false
        if (runCatching { wifi.isWifiEnabled }.getOrNull() == enabled) return true
        val direct = runCatching { wifi.setWifiEnabled(enabled) }.getOrDefault(false)
        if (!direct) {
            runCatching {
                LocalAdb(AdbKeys.load(app)).use { adb ->
                    val access = adb.connect(mayAsk = false)
                    if (access == LocalAdb.Access.READY) {
                        adb.shell(if (enabled) "svc wifi enable" else "svc wifi disable", 4_000)
                        log("geely wifi: local ADB svc wifi ${if (enabled) "enable" else "disable"} sent")
                    } else {
                        log("geely wifi: local ADB unavailable ($access)")
                    }
                }
            }.onFailure { log("geely wifi: local ADB error ${it.javaClass.simpleName}") }
        } else {
            log("geely wifi: WifiManager.setWifiEnabled($enabled) accepted")
        }
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            if (runCatching { wifi.isWifiEnabled }.getOrNull() == enabled) return true
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return runCatching { wifi.isWifiEnabled }.getOrNull() == enabled
    }
}
