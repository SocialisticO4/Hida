package app.hida.vault.nativemodule

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Ensures exactly one MAIN/LAUNCHER activity-alias is enabled. Disabling only the "previous"
 * alias from prefs can leave multiple aliases enabled if PackageManager state drifts — launchers
 * then keep showing the wrong tile art (often the default Hida lock).
 */
object IconAliasSwitcher {

    private val LAUNCHER_ALIASES = listOf(
        "HidaDefaultAlias",
        "CalculatorAlias",
        "CalculatorMaterialAlias",
        "CalculatorIosAlias",
        "WeatherAlias",
        "NotesAlias",
        "ClockAlias",
        "MusicAlias",
        "CalendarAlias",
        "MailAlias",
        "BrowserAlias",
        "CameraAlias",
        "MapsAlias",
        "PhoneAlias",
        "ContactsAlias",
        "MessagesAlias",
        "PlayStoreAlias",
        "DriveAlias",
        "FilesAlias",
        "WalletAlias",
        "BankingAlias",
        "ShoppingAlias",
        "VideosAlias",
        "ChatAlias",
        "PhotosAlias",
        "SettingsAlias",
        "NewsAlias",
        "FitnessAlias",
        "TranslateAlias",
    )

    fun normalizeAlias(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t == "MainActivity") return "HidaDefaultAlias"
        return t
    }

    fun applyIconAlias(context: Context, prefs: PreferencesManager, requestedAlias: String) {
        val normalized = normalizeAlias(requestedAlias)
        val target =
            if (normalized in LAUNCHER_ALIASES) normalized else "HidaDefaultAlias"

        val pm = context.packageManager
        val pkg = context.packageName

        pm.setComponentEnabledSetting(
            ComponentName(pkg, "$pkg.MainActivity"),
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )

        for (name in LAUNCHER_ALIASES) {
            val state =
                if (name == target) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg.$name"),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
        prefs.saveIconAlias(target)
    }

    fun reconcileFromPrefs(context: Context) {
        val prefs = PreferencesManager(context)
        applyIconAlias(context, prefs, prefs.getIconAlias())
    }
}
