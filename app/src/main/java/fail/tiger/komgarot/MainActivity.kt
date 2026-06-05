package fail.tiger.komgarot

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import fail.tiger.komgarot.data.local.PreferencesManager
import fail.tiger.komgarot.ui.navigation.AppNavGraph
import fail.tiger.komgarot.ui.theme.KomgarotTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var backgroundedAt = 0L
    private val locked = mutableStateOf(false)
    private var promptShowing = false
    private var lockCheckJob: Job? = null
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KomgarotApp
        preferencesManager = PreferencesManager(applicationContext)
        enableEdgeToEdge()

        setContent {
            KomgarotTheme {
                // 读取离线模式状态
                val offlineMode by preferencesManager.getOfflineModeFlow().collectAsState(initial = false)
                // 模糊层（应用锁）
                Box(Modifier.fillMaxSize().then(if (locked.value) Modifier.blur(20.dp) else Modifier)) {
                    // 将 offlineMode 传递给 AppNavGraph，让它决定起始页
                    AppNavGraph(
                        app = app,
                        startDestination = if (offlineMode) "home" else "login"
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (promptShowing || lockCheckJob?.isActive == true) return
        val prefs = (application as KomgarotApp).authPreferences

        lockCheckJob = lifecycleScope.launch {
            val appLockEnabled = prefs.appLockEnabled.first()
            val appLockTimeout = prefs.appLockTimeout.first()
            if (!appLockEnabled || promptShowing) return@launch

            val timeoutMs = appLockTimeout * 60_000L
            val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
            if (backgroundedAt > 0 && timeoutMs > 0 && elapsed < timeoutMs) return@launch

            locked.value = true
            promptShowing = true
            showBiometricPrompt()
        }
    }

    override fun onPause() {
        super.onPause()
        lockCheckJob?.cancel()
        lockCheckJob = null
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptShowing = false
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish()
                } else {
                    locked.value = false
                }
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptShowing = false
                backgroundedAt = 0L
                locked.value = false
            }
            override fun onAuthenticationFailed() {}
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}
