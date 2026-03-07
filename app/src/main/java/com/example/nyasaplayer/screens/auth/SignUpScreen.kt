package com.example.nyasaplayer.screens.auth

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.ui.icons.EmailIcon
import com.example.nyasaplayer.core.common.ui.icons.LockIcon
import com.example.nyasaplayer.core.common.ui.icons.VisibilityIcon
import com.example.nyasaplayer.core.common.ui.icons.VisibilityOffIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaError
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnSignUpSuccess by rememberUpdatedState(onSignUpSuccess)

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) currentOnSignUpSuccess()
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
        SignUpHeader()
        Spacer(modifier = Modifier.height(32.dp))
        SignUpFormFields(
            uiState = uiState,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = NyasaError,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        PrimaryGradientButton(
            text = stringResource(R.string.create_account),
            isLoading = uiState.isLoading,
            onClick = viewModel::signUp,
        )
        Spacer(modifier = Modifier.weight(1f))
        SignUpFooter(onNavigateToLogin = onNavigateToLogin)
    }
}

@Composable
private fun SignUpHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconWithGlow()
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.create_account),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.sign_up_subtitle),
            fontSize = 16.sp,
            color = NyasaTextSecondary,
        )
    }
}

@Composable
private fun SignUpFormFields(
    uiState: SignUpUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
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
        PasswordFieldWithLabel(
            label = stringResource(R.string.password_label),
            value = uiState.password,
            onValueChange = onPasswordChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
        PasswordFieldWithLabel(
            label = stringResource(R.string.confirm_password_label),
            value = uiState.confirmPassword,
            onValueChange = onConfirmPasswordChange,
        )
    }
}

@Composable
private fun PasswordFieldWithLabel(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        FieldLabel(text = label)
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
private fun SignUpFooter(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.already_have_account),
            color = NyasaTextSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = stringResource(R.string.sign_in),
            color = NyasaPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onNavigateToLogin() },
        )
    }
}
