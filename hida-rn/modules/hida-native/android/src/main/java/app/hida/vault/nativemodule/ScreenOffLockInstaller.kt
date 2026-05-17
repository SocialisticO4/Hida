package app.hida.vault.nativemodule

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Registers a single process-private [Intent.ACTION_SCREEN_OFF] receiver ([RECEIVER_NOT_EXPORTED]),
 * avoiding polling-only lifecycle heuristics while staying compatible with OEM power keys.
 *
 * Idle timeout is keyed off persisted session timeout preference; RN keeps an accurate vault-route
 * flag so the calculator/welcome disguise does not get spurious locks.
 */
object ScreenOffLockInstaller {

    private val lock = Any()
    private var receiver: BroadcastReceiver? = null

    fun install(application: Application) {
        synchronized(lock) {
            if (receiver != null) return
            val mgr = PreferencesManager(application.applicationContext)
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                    mgr.markScreenOffLockPendingIfApplicable()
                }
            }
            ContextCompat.registerReceiver(
                application,
                r,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiver = r
        }
    }
}
