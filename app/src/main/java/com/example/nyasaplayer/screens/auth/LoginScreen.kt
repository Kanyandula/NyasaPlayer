package com.example.nyasaplayer.screens.auth

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyasaplayer.BuildConfig
import com.example.nyasaplayer.R
import com.example.nyasaplayer.ui.icons.EmailIcon
import com.example.nyasaplayer.ui.icons.LockIcon
import com.example.nyasaplayer.ui.icons.VisibilityIcon
import com.example.nyasaplayer.ui.icons.VisibilityOffIcon
import com.example.nyasaplayer.ui.theme.AppTheme
import com.example.nyasaplayer.ui.theme.NyasaBackground
import com.example.nyasaplayer.ui.theme.NyasaError
import com.example.nyasaplayer.ui.theme.NyasaPrimary
import com.example.nyasaplayer.ui.theme.NyasaSurface2
import com.example.nyasaplayer.ui.theme.NyasaSurface4
import com.example.nyasaplayer.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.ui.theme.NyasaTextTertiary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) currentOnLoginSuccess()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        LoginHeader()
        Spacer(modifier = Modifier.height(32.dp))
        LoginFormFields(
            uiState = uiState,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onForgotPassword = viewModel::forgotPassword,
        )
        Spacer(modifier = Modifier.height(24.dp))
        LoginMessages(uiState = uiState)
        PrimaryGradientButton(
            text = stringResource(R.string.sign_in),
            isLoading = uiState.isLoading,
            onClick = viewModel::signInWithEmail,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OrDivider()
        Spacer(modifier = Modifier.height(24.dp))
        LoginGoogleSignIn(
            onGoogleToken = viewModel::signInWithGoogleToken,
            onGoogleError = viewModel::onGoogleSignInError,
        )
        Spacer(modifier = Modifier.weight(1f))
        LoginFooter(onNavigateToSignUp = onNavigateToSignUp)
    }
}

@Composable
private fun LoginHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconWithGlow()
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.welcome_back),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.sign_in_to_continue),
            fontSize = 16.sp,
            color = NyasaTextSecondary,
        )
    }
}

@Composable
private fun LoginFormFields(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FieldLabel(text = stringResource(R.string.email_label))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            placeholder = { Text(stringResource(R.string.email_placeholder), color = NyasaTextTertiary) },
            leadingIcon = {
                Icon(
                    imageVector = EmailIcon,
                    contentDescription = null,
                    tint = NyasaTextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = authTextFieldColors(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        LoginPasswordField(
            value = uiState.password,
            onValueChange = onPasswordChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.forgot_password),
            fontSize = 14.sp,
            color = NyasaPrimary,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onForgotPassword() },
        )
    }
}

@Composable
private fun LoginPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        FieldLabel(text = stringResource(R.string.password_label))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(PasswordPlaceholder, color = NyasaTextTertiary) },
            leadingIcon = {
                Icon(
                    imageVector = LockIcon,
                    contentDescription = null,
                    tint = NyasaTextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) VisibilityIcon else VisibilityOffIcon,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.hide_password else R.string.show_password,
                        ),
                        tint = NyasaTextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = authTextFieldColors(),
        )
    }
}

@Composable
private fun LoginMessages(uiState: LoginUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = NyasaError,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (uiState.successMessage != null) {
            Text(
                text = uiState.successMessage.orEmpty(),
                color = NyasaPrimary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LoginGoogleSignIn(
    onGoogleToken: (String) -> Unit,
    onGoogleError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current ?: return
    val coroutineScope = rememberCoroutineScope()

    SocialButton(
        text = stringResource(R.string.continue_with_google),
        onClick = {
            coroutineScope.launch {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    val credentialManager = CredentialManager.create(activity)
                    val result = credentialManager.getCredential(activity, request)
                    val googleIdToken = GoogleIdTokenCredential
                        .createFrom(result.credential.data)
                        .idToken
                    onGoogleToken(googleIdToken)
                } catch (_: GetCredentialCancellationException) {
                    // User cancelled — no action needed
                } catch (e: GetCredentialException) {
                    Log.e("LoginScreen", "Google sign-in failed", e)
                    onGoogleError(e.message ?: "Google sign-in failed")
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LoginFooter(
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.no_account_prompt),
            color = NyasaTextSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = stringResource(R.string.sign_up),
            color = NyasaPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onNavigateToSignUp() },
        )
    }
}

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = NyasaSurface4)
        Text(
            text = stringResource(R.string.or_divider),
            color = NyasaTextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = NyasaSurface4)
    }
}

@Composable
private fun SocialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = NyasaSurface2),
        border = BorderStroke(1.dp, NyasaSurface4),
    ) {
        Text(text = text, color = Color.White, fontSize = 16.sp)
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    val previewState = LoginUiState()
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NyasaBackground)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            LoginHeader()
            Spacer(modifier = Modifier.height(32.dp))
            LoginFormFields(
                uiState = previewState,
                onEmailChange = {},
                onPasswordChange = {},
                onForgotPassword = {},
            )
            Spacer(modifier = Modifier.height(24.dp))
            LoginMessages(uiState = previewState)
            PrimaryGradientButton(
                text = "Sign In",
                isLoading = false,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(24.dp))
            OrDivider()
            Spacer(modifier = Modifier.height(24.dp))
            SocialButton(text = "Continue with Google", onClick = {})
            Spacer(modifier = Modifier.weight(1f))
            LoginFooter(onNavigateToSignUp = {})
        }
    }
}
