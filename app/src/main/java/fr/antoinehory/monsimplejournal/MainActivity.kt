package fr.antoinehory.monsimplejournal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.monsimplejournal.data.local.AppDatabase
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferences
import fr.antoinehory.monsimplejournal.data.preferences.UserPreferencesRepository
import fr.antoinehory.monsimplejournal.data.repository.JournalRepository
import fr.antoinehory.monsimplejournal.ui.navigation.JournalNavHost
import fr.antoinehory.monsimplejournal.ui.theme.MonSimpleJournalTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val journalRepository = JournalRepository(database.journalDao())
        val userPreferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            val userPreferences by remember(userPreferencesRepository) {
                userPreferencesRepository.userPreferencesFlow
            }.collectAsStateWithLifecycle(initialValue = UserPreferences())

            var isUnlocked by rememberSaveable { mutableStateOf(false) }

            val promptTitle = stringResource(R.string.biometric_prompt_title)
            val promptSubtitle = stringResource(R.string.biometric_prompt_subtitle)

            val triggerBiometricPrompt = remember(promptTitle, promptSubtitle) {
                {
                    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    val canAuth = BiometricManager.from(this@MainActivity)
                        .canAuthenticate(authenticators)

                    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                        isUnlocked = true
                    } else {
                        val executor = ContextCompat.getMainExecutor(this@MainActivity)
                        val prompt = BiometricPrompt(
                            this@MainActivity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult,
                                ) {
                                    isUnlocked = true
                                }
                            },
                        )
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(promptTitle)
                            .setSubtitle(promptSubtitle)
                            .setAllowedAuthenticators(authenticators)
                            .build()
                        prompt.authenticate(promptInfo)
                    }
                }
            }

            LaunchedEffect(userPreferences.biometricLockEnabled) {
                if (userPreferences.biometricLockEnabled && !isUnlocked) {
                    triggerBiometricPrompt()
                }
            }

            MonSimpleJournalTheme(userPreferences = userPreferences) {
                if (userPreferences.biometricLockEnabled && !isUnlocked) {
                    BiometricLockScreen(onUnlockClicked = triggerBiometricPrompt)
                } else {
                    JournalNavHost(
                        journalRepository = journalRepository,
                        userPreferencesRepository = userPreferencesRepository,
                    )
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(
    onUnlockClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.biometric_locked_screen_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onUnlockClicked) {
                Text(stringResource(R.string.biometric_unlock_button))
            }
        }
    }
}
