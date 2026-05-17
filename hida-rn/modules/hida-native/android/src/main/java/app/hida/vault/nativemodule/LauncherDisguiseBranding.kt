package app.hida.vault.nativemodule

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * Mirrors [AndroidManifest.xml] launcher aliases → icons so system UI keyed off this [Activity]'s
 * branding (logo / recents task description) can match the enabled launcher tile instead of
 * relying only on [`ApplicationInfo#icon`] (the default Hida mipmap on `<application>`).
 *
 * OEM note: Biometric dialogs often use the application icon. Jetpack's [BiometricPrompt] logo APIs
 * are privileged ([`SET_BIOMETRIC_DIALOG_ADVANCED`]). Activity logo + task description are the
 * practical app-level knobs; some OEMs still ignore them.
 *
 * System Settings → App info titles use [`ApplicationInfo.loadLabel`], which reads `<application android:label>`
 * from the manifest — not the enabled `activity-alias` label. The launcher tile and [ActivityManager.TaskDescription]
 * follow the alias; App info stays on the install/application name unless the OEM uses non-standard UI.
 */
object LauncherDisguiseBranding {

    private fun normalizedAlias(alias: String): String = when {
        alias.isBlank() || alias == "MainActivity" -> "HidaDefaultAlias"
        else -> alias
    }

    private data class ResSpec(val name: String, val type: String)

    /**
     * Must match [AndroidManifest.xml] `activity-alias` android:icon names (`@mipmap/ic_disguise_*`),
     * not legacy drawable tiles — otherwise biometric logo / recents bitmap ≠ launcher tile.
     */
    private fun resSpecForAlias(aliasRaw: String): ResSpec {
        return when (normalizedAlias(aliasRaw)) {
            "HidaDefaultAlias" -> ResSpec("ic_launcher", "mipmap")
            "CalculatorAlias" -> ResSpec("ic_disguise_calculator", "mipmap")
            "CalculatorMaterialAlias" -> ResSpec("ic_disguise_calculator_material", "mipmap")
            "CalculatorIosAlias" -> ResSpec("ic_disguise_calculator_ios", "mipmap")
            "WeatherAlias" -> ResSpec("ic_disguise_weather", "mipmap")
            "NotesAlias" -> ResSpec("ic_disguise_notes", "mipmap")
            "ClockAlias" -> ResSpec("ic_disguise_clock", "mipmap")
            "MusicAlias" -> ResSpec("ic_disguise_music", "mipmap")
            "CalendarAlias" -> ResSpec("ic_disguise_calendar", "mipmap")
            "MailAlias" -> ResSpec("ic_disguise_mail", "mipmap")
            "BrowserAlias" -> ResSpec("ic_disguise_browser", "mipmap")
            "CameraAlias" -> ResSpec("ic_disguise_camera", "mipmap")
            "MapsAlias" -> ResSpec("ic_disguise_maps", "mipmap")
            "PhoneAlias" -> ResSpec("ic_disguise_phone", "mipmap")
            "ContactsAlias" -> ResSpec("ic_disguise_contacts", "mipmap")
            "MessagesAlias" -> ResSpec("ic_disguise_messages", "mipmap")
            "PlayStoreAlias" -> ResSpec("ic_disguise_playstore", "mipmap")
            "DriveAlias" -> ResSpec("ic_disguise_drive", "mipmap")
            "FilesAlias" -> ResSpec("ic_disguise_files", "mipmap")
            "WalletAlias" -> ResSpec("ic_disguise_wallet", "mipmap")
            "BankingAlias" -> ResSpec("ic_disguise_banking", "mipmap")
            "ShoppingAlias" -> ResSpec("ic_disguise_shopping", "mipmap")
            "VideosAlias" -> ResSpec("ic_disguise_videos", "mipmap")
            "ChatAlias" -> ResSpec("ic_disguise_chat", "mipmap")
            "PhotosAlias" -> ResSpec("ic_disguise_photos", "mipmap")
            "SettingsAlias" -> ResSpec("ic_disguise_settings", "mipmap")
            "NewsAlias" -> ResSpec("ic_disguise_news", "mipmap")
            "FitnessAlias" -> ResSpec("ic_disguise_fitness", "mipmap")
            "TranslateAlias" -> ResSpec("ic_disguise_translate", "mipmap")
            else -> ResSpec("ic_launcher", "mipmap")
        }
    }

    /**
     * User-visible label aligned with launcher-alias [android:label] in AndroidManifest.xml.
     * Single source of truth for task description and biometric subtitle — keep in sync.
     */
    fun displayNameForAlias(aliasRaw: String): String =
        when (normalizedAlias(aliasRaw)) {
            "HidaDefaultAlias" -> "Hida"
            "CalculatorAlias" -> "Calculator"
            "CalculatorMaterialAlias" -> "Calculator"
            "CalculatorIosAlias" -> "Calc"
            "WeatherAlias" -> "Weather"
            "NotesAlias" -> "Keep Notes"
            "ClockAlias" -> "Clock"
            "MusicAlias" -> "YT Music"
            "CalendarAlias" -> "Calendar"
            "MailAlias" -> "Gmail"
            "BrowserAlias" -> "Chrome"
            "CameraAlias" -> "Camera"
            "MapsAlias" -> "Maps"
            "PhoneAlias" -> "Phone"
            "ContactsAlias" -> "Contacts"
            "MessagesAlias" -> "Messages"
            "PlayStoreAlias" -> "Play Store"
            "DriveAlias" -> "Drive"
            "FilesAlias" -> "Files"
            "WalletAlias" -> "Google Pay"
            "BankingAlias" -> "PayPal"
            "ShoppingAlias" -> "Amazon"
            "VideosAlias" -> "YouTube"
            "ChatAlias" -> "WhatsApp"
            "PhotosAlias" -> "Photos"
            "SettingsAlias" -> "Settings"
            "NewsAlias" -> "Google News"
            "FitnessAlias" -> "Google Fit"
            "TranslateAlias" -> "Google Translate"
            else -> "Hida"
        }

    private fun resolveResId(resources: Resources, pkg: String, aliasRaw: String): Int {
        val spec = resSpecForAlias(aliasRaw)
        var id = resources.getIdentifier(spec.name, spec.type, pkg)
        if (id == 0 && spec.type == "mipmap") {
            id = resources.getIdentifier(spec.name, "drawable", pkg)
        }
        return id
    }

    /** Resolved launcher resource id for disguise artwork (matching [AndroidManifest.xml] aliases). */
    fun launcherIconResId(resources: Resources, pkg: String, prefs: PreferencesManager): Int =
        resolveResId(resources, pkg, prefs.getIconAlias())

    /** Apply disguise artwork to activity chrome that some auth / system surfaces mirror. */
    fun apply(activity: Activity, prefs: PreferencesManager) {
        // Process-local ApplicationInfo.icon swap — the field BiometricPrompt actually reads.
        applyApplicationIconOverride(activity, prefs)

        val res = activity.resources
        val pkg = activity.packageName
        val alias = prefs.getIconAlias()
        val resId = resolveResId(res, pkg, alias)
        val iconDrawable = try {
            if (resId == 0) null else ContextCompat.getDrawable(activity, resId)
        } catch (_: Throwable) {
            null
        } ?: return

        val bitmap = drawableToTrayBitmap(iconDrawable) ?: return
        val title = displayNameForAlias(alias)
        @ColorInt val colorPrimary = 0xFF16140F.toInt()

        try {
            @Suppress("DEPRECATION")
            val td =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ActivityManager.TaskDescription(title, bitmap, colorPrimary)
                } else {
                    ActivityManager.TaskDescription(title, bitmap)
                }
            activity.setTaskDescription(td)
        } catch (_: Throwable) {
        }
    }

    /**
     * Mutate the in-process [android.content.pm.ApplicationInfo.icon] field so that system UI
     * surfaces (notably [androidx.biometric.BiometricPrompt] on most OEM stacks, which reads
     * `getApplicationInfo().icon` rather than the alias-level icon) reflect the active disguise.
     * `setLogo` on PromptInfo.Builder is gated by `SET_BIOMETRIC_DIALOG_ADVANCED` and silently
     * fails on non-system apps; this field-swap is the only reliable knob.
     *
     * Caller should invoke this immediately before showing biometric / surface that reads the
     * application icon. Safe — the field is process-local and resets on app process death.
     */
    fun applyApplicationIconOverride(context: Context, prefs: PreferencesManager) {
        runCatching {
            val resId = resolveResId(context.resources, context.packageName, prefs.getIconAlias())
            if (resId == 0) return@runCatching
            context.applicationInfo.icon = resId
        }
    }

    private fun drawableToTrayBitmap(d: Drawable): Bitmap? {
        try {
            if (d is BitmapDrawable && d.bitmap != null) {
                return d.bitmap
            }
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 144
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 144
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            return bmp
        } catch (_: Throwable) {
            return null
        }
    }
}
