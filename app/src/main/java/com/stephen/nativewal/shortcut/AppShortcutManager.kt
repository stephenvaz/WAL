package com.stephen.nativewal.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Icon
import android.os.Build
import com.stephen.nativewal.MainActivity
import com.stephen.nativewal.R
import com.stephen.nativewal.data.repository.WifiConfigRepository

class AppShortcutManager(private val context: Context) {

    private val shortcutManager: ShortcutManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            context.getSystemService(ShortcutManager::class.java)
        else null

    suspend fun updateShortcuts() {
        val sm = shortcutManager ?: return

        val repository = WifiConfigRepository(context)
        val configs = repository.getAllConfigs()

        val roundIcon = createRoundIcon()

        val shortcuts = configs
            .filter { it.isEnabled }
            .sortedByDescending { it.updatedAt }
            .take(4)
            .map { config ->
                val shortLabel = if (config.ssid.length > 10)
                    config.ssid.take(9) + "\u2026" else config.ssid
                val longLabel = if ("Login: ${config.ssid}".length > 25)
                    "Login: ${config.ssid}".take(24) + "\u2026"
                else "Login: ${config.ssid}"

                ShortcutInfo.Builder(context, "shortcut_${config.ssid.hashCode()}")
                    .setShortLabel(shortLabel)
                    .setLongLabel(longLabel)
                    .setIcon(roundIcon)
                    .setIntent(Intent().apply {
                        component = ComponentName(context, MainActivity::class.java)
                        action = MainActivity.ACTION_START_AUTO_LOGIN
                        putExtra(MainActivity.EXTRA_AUTO_LOGIN_SSID, config.ssid)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                    .build()
            }

        sm.dynamicShortcuts = shortcuts
    }

    private fun createRoundIcon(): Icon {
        val size = 320
        val padding = 24

        val bgBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bgCanvas = Canvas(bgBitmap)
        val bgDrawable = context.getDrawable(R.mipmap.ic_launcher_background)
        bgDrawable?.setBounds(0, 0, size, size)
        bgDrawable?.draw(bgCanvas)

        val fgBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fgCanvas = Canvas(fgBitmap)
        val fgDrawable = context.getDrawable(R.drawable.ic_launcher_foreground)
        fgDrawable?.setBounds(padding, padding, size - padding, size - padding)
        fgDrawable?.draw(fgCanvas)

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)

        resultCanvas.drawBitmap(bgBitmap, 0f, 0f, null)
        resultCanvas.drawBitmap(fgBitmap, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        maskCanvas.drawCircle(
            size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1 }
        )
        resultCanvas.drawBitmap(mask, 0f, 0f, maskPaint)

        return Icon.createWithBitmap(result)
    }
}
