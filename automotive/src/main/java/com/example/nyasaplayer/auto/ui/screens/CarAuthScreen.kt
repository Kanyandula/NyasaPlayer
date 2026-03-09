package com.example.nyasaplayer.auto.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.BuildConfig
import com.example.nyasaplayer.auto.viewmodel.CarAuthUiState
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private val SignInButtonHeight = 76.dp
private val IconBackgroundSize = 120.dp
private val IconSize = 56.dp
private val ErrorColor = Color(0xFFEF4444)
private const val TAG = "CarAuthScreen"

@Suppress("LongMethod")
@Composable
fun CarAuthScreen(
    uiState: CarAuthUiState,
    onGoogleToken: (String) -> Unit,
    onGoogleError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.5f)
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(IconBackgroundSize)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicNoteIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(IconSize),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "NyasaPlayer",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sign in to access your music library",
                color = NyasaTextSecondary,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = NyasaPrimary,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                GoogleSignInButton(
                    onGoogleToken = onGoogleToken,
                    onGoogleError = onGoogleError,
                )
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = uiState.errorMessage,
                    color = ErrorColor,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught", "Deprecation")
@Composable
private fun GoogleSignInButton(
    onGoogleToken: (String) -> Unit,
    onGoogleError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    onGoogleToken(idToken)
                } else {
                    onGoogleError("No ID token returned from Google")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign-in failed: status=${e.statusCode}", e)
                onGoogleError("Google sign-in failed (code ${e.statusCode})")
            }
        }
    }

    Button(
        onClick = {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            launcher.launch(client.signInIntent)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(SignInButtonHeight),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NyasaSurface2),
    ) {
        Text(
            text = "Sign in with Google",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
