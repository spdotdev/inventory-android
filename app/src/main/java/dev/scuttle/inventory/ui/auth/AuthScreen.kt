package dev.scuttle.inventory.ui.auth

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onForgotPassword: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRegister = state.mode == AuthMode.REGISTER
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // Google sign-in via Jetpack Credential Manager (per CLAUDE.md) — replaces the
    // deprecated GMS GoogleSignIn API. Credential Manager renders its own account
    // picker; on success we pull the same Google **ID token** and hand it to the
    // unchanged loginWithGoogle path (server verifies it via GoogleIdTokenVerifier).
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    fun launchGoogleSignIn() {
        viewModel.onGoogleLoading()
        val googleIdOption =
            GetGoogleIdOption
                .Builder()
                .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                // Show all accounts, not just previously-authorized ones, so first-time
                // sign-in works without a prior grant.
                .setFilterByAuthorizedAccounts(false)
                .build()
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(googleIdOption)
                .build()

        scope.launch {
            try {
                val response = credentialManager.getCredential(context, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.loginWithGoogle(googleCredential.idToken)
                } else {
                    viewModel.onGoogleError("Unexpected credential type from Google.")
                }
            } catch (e: GetCredentialCancellationException) {
                viewModel.onGoogleError(null) // user dismissed the picker — not an error
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("GoogleSignIn", "Failed to parse Google ID token", e)
                viewModel.onGoogleError("Google sign-in failed. Please try again.")
            } catch (e: GetCredentialException) {
                Log.e("GoogleSignIn", "getCredential failed", e)
                viewModel.onGoogleError("Google sign-in failed. Please try again.")
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // GAP5-M5: a mode-specific headline distinct from the submit button's own
        // label ("Sign in"/"Create account" below) — states which mode is active
        // ("Welcome back" vs "Create your account") at a glance, on first load.
        Text(
            text =
                if (isRegister) {
                    stringResource(R.string.auth_heading_register)
                } else {
                    stringResource(R.string.auth_heading_login)
                },
            style = MaterialTheme.typography.headlineSmall,
        )

        if (isRegister) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.auth_field_name)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrect = false,
                        imeAction = ImeAction.Next,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.auth_field_email)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrect = false,
                    imeAction = ImeAction.Next,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.auth_field_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            // Shown always in REGISTER mode (not only after a 422) so the real
            // server-side rule (RegisterRequest: 'password' => min:8) is visible
            // before the user submits and gets rejected (GAP-5 H4).
            supportingText =
                if (isRegister) {
                    { Text(stringResource(R.string.auth_password_requirements)) }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        viewModel.submit()
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        (state.errorRes?.let { stringResource(it) } ?: state.error)?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.submit()
            },
            enabled = !state.loading && !state.googleLoading && state.email.isNotBlank() && state.password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text =
                        if (isRegister) {
                            stringResource(
                                R.string.auth_create_account,
                            )
                        } else {
                            stringResource(R.string.auth_sign_in)
                        },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_divider_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        GoogleSignInButton(
            onClick = {
                keyboardController?.hide()
                launchGoogleSignIn()
            },
            enabled = !state.loading && !state.googleLoading,
            loading = state.googleLoading,
        )

        // GAP5-M5 + GAP6-L1: the mode switch is the more consequential of the two
        // links below (it changes what the whole screen does), so it gets more
        // visual weight — titleSmall in the PRIMARY color, keeping the tappable-link
        // affordance a plain onSurface tint stripped away (GAP-6's audit found it
        // read as a caption, not a button) — while forgot-password stays small and
        // muted, and a spacer separates them.
        TextButton(onClick = viewModel::toggleMode, modifier = Modifier.fillMaxWidth()) {
            Text(
                text =
                    if (isRegister) {
                        stringResource(
                            R.string.auth_toggle_to_login,
                        )
                    } else {
                        stringResource(R.string.auth_toggle_to_register)
                    },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!isRegister) {
            Spacer(Modifier.height(Spacing.xs))
            TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.auth_forgot_password),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
