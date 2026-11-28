/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.box.gallery

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.gallery.security.AppLockManager
import com.google.ai.edge.gallery.security.BiometricEncryptionManager
import com.google.ai.edge.gallery.security.BiometricHelper
import com.google.ai.edge.gallery.security.PassphraseHolder
import com.google.ai.edge.gallery.security.SecurityAuditLog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import com.google.ai.edge.gallery.GalleryApp
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

  private val modelManagerViewModel: ModelManagerViewModel by viewModels()
  private var contentSet: Boolean = false
  private var isAuthenticated: Boolean = false
  private lateinit var biometricHelper: BiometricHelper

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Box: Initialize biometric authentication, app lock, and DB encryption
    biometricHelper = BiometricHelper(this)
    AppLockManager.init(this)
    BiometricEncryptionManager.init(this)

    // Box: Apply FLAG_SECURE based on user preference; observe for runtime changes.No new features.
    lifecycleScope.launch {
      AppLockManager.screenshotsEnabled.collect { enabled ->
        if (enabled) {
          window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
          window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
          )
        }
      }
    }

    SecurityAuditLog.log(this, "APP_LAUNCHED")

    // Debug: Dump all intent extras to see what FCM unloads
    intent.extras?.let { extras ->
      for (key in extras.keySet()) {
        Log.d(TAG, "onCreate Extra -> Key: $key, Value: ${extras.get(key)}")
      }
    }

    // Convert FCM Console data extras to intent data for GalleryNavGraph to pick up
    intent.getStringExtra("deeplink")?.let { link ->
      Log.d(TAG, "onCreate: Found deeplink extra: $link")
      if (link.startsWith("http://") || link.startsWith("https://")) {
        val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
        startActivity(browserIntent)
      } else {
        intent.data = link.toUri()
      }
    }

    fun setContent() {
      if (contentSet) {
        return
      }

      setContent {
        GalleryTheme {
          Surface(modifier = Modifier.fillMaxSize()) {
            val dbEncEnabled by BiometricEncryptionManager.isEnabledFlow.collectAsState()
            val dbDecrypted by PassphraseHolder.isSet.collectAsState()

            if (dbEncEnabled && !dbDecrypted) {
              DbLockedScreen(activity = this@MainActivity)
            } else {
              GalleryApp(modelManagerViewModel = modelManagerViewModel)
            }
          }
        }
      }

      @OptIn(ExperimentalApi::class)
      ExperimentalFlags.enableBenchmark = false

      contentSet = true
    }

    modelManagerViewModel.loadModelAllowlist()

    // Disable the system splash screen's custom exit animation and just show content.
    val splashScreen = installSplashScreen()
    setContent()

    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Fix for three-button nav not properly going edge-to-edge.
      // See: https://issuetracker.google.com/issues/298296168
      window.isNavigationBarContrastEnforced = false
    }
    // Keep the screen on while the app is running for a better demo experience.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    // Debug: Dump all intent extras to see what FCM unloads
    intent.extras?.let { extras ->
      for (key in extras.keySet()) {
        Log.d(TAG, "onNewIntent Extra -> Key: $key, Value: ${extras.get(key)}")
      }
    }

    intent.getStringExtra("deeplink")?.let { link ->
      Log.d(TAG, "onNewIntent: Found deeplink extra: $link")
      if (link.startsWith("http://") || link.startsWith("https://")) {
        val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
        startActivity(browserIntent)
      } else {
        intent.data = link.toUri()
      }
    }
  }

  override fun onResume() {
    super.onResume()

    // Box: Biometric app lock — prompt once per session if opted in.
    if (AppLockManager.isBiometricLockEnabled() && !isAuthenticated &&
        biometricHelper.canAuthenticate() == BiometricHelper.BiometricStatus.AVAILABLE) {
      biometricHelper.authenticate(
        onSuccess = {
          isAuthenticated = true
          AppLockManager.unlock()
          SecurityAuditLog.log(this, "APP_RESUME_AUTH_SUCCESS")
        },
        onFailure = { _, _ -> /* silent, user can retry */ },
        onError = { errorCode, _ ->
          if (errorCode != 10 && errorCode != 13) {
            SecurityAuditLog.log(this, "APP_RESUME_AUTH_ERROR: $errorCode")
          }
          isAuthenticated = true
          AppLockManager.unlock()
        }
      )
    }

  }

  companion object {
    private const val TAG = "AGMainActivity"
  }
}

@Composable
private fun DbLockedScreen(activity: FragmentActivity) {
  var error by remember { mutableStateOf<String?>(null) }
  var isPrompting by remember { mutableStateOf(false) }

  fun prompt() {
    if (isPrompting) return
    isPrompting = true
    error = null
    BiometricEncryptionManager.promptDecrypt(
      activity = activity,
      context = activity,
      onSuccess = { cipher ->
        try {
          val passphrase = BiometricEncryptionManager.decryptPassphrase(activity, cipher)
          PassphraseHolder.set(passphrase)
        } catch (e: Exception) {
          error = "Decryption failed: ${e.message}"
        }
        isPrompting = false
      },
      onFailure = { _, msg ->
        error = msg?.toString() ?: "Authentication failed"
        isPrompting = false
      },
      onError = { code, msg ->
        // 5=ERROR_CANCELED, 10=ERROR_USER_CANCELED, 13=ERROR_NEGATIVE_BUTTON
        if (code != 5 && code != 10 && code != 13) error = msg.toString()
        isPrompting = false
      },
    )
  }

  LaunchedEffect(Unit) { prompt() }

  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Rounded.Lock,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text("Box", style = MaterialTheme.typography.headlineMedium)
      Text(
        "Authenticate to decrypt your chats",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      error?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Button(onClick = ::prompt, enabled = !isPrompting) {
        Text(if (isPrompting) "Authenticating…" else "Authenticate")
      }
    }
  }
}
