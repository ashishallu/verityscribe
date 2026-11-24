package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VerityGlassInputField
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.GlassAlertRed
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText

@Composable
fun LoginScreen(
    isObsidianMode: Boolean,
    authError: String?,
    onClearError: () -> Unit,
    onLogin: (email: String, password: String, rememberMe: Boolean) -> Unit,
    onRequestPasswordReset: (email: String, onResult: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetSentMessage by remember { mutableStateOf<String?>(null) }

    val submitAction = {
        onClearError()
        localError = null
        val trimmedEmail = email.trim()
        when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() -> localError = "Enter a valid email address."
            password.isBlank() -> localError = "Enter your password."
            else -> onLogin(trimmedEmail, password, rememberMe)
        }
    }

    LoginAtmosphericBackdrop(
        isObsidianMode = isObsidianMode,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(34.dp))

                // Italic wordmark matching reference mockup
                Text(
                    text = "Verity",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Your Personalized\nHealthcare Assistant",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(34.dp))

                // Email field
                Text(
                    text = "Email",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, bottom = 8.dp)
                )
                VerityGlassInputField(
                    value = email,
                    onValueChange = {
                        email = it
                        localError = null
                        onClearError()
                    },
                    placeholder = "Enter your email",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    testTag = "login_email_input"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password field
                Text(
                    text = "Password",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, bottom = 8.dp)
                )
                VerityGlassInputField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                        onClearError()
                    },
                    placeholder = "Enter your password",
                    leadingIcon = Icons.Outlined.Lock,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitAction() }),
                    trailingContent = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.testTag("login_password_visibility_toggle")
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.Visibility
                                } else {
                                    Icons.Outlined.VisibilityOff
                                },
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    },
                    testTag = "login_password_input"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Remember me & Forgot Password?
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { rememberMe = !rememberMe }
                            .padding(end = 8.dp)
                            .testTag("login_remember_me"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (rememberMe) Color(0x38FFFFFF) else Color.Transparent
                                )
                                .border(
                                    width = 1.2.dp,
                                    color = if (rememberMe) Color.White else Color.White.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rememberMe) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Remember me checked",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Remember me",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Text(
                        text = "Forgot Password?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceBlueText.copy(alpha = 0.92f),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                resetEmail = email.trim()
                                resetSentMessage = null
                                showForgotDialog = true
                            }
                            .padding(horizontal = 6.dp, vertical = 12.dp)
                            .testTag("login_forgot_password")
                    )
                }

                val errorToShow = localError ?: authError
                if (errorToShow != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorToShow,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassAlertRed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Signature glowing metallic-glass Login pill button
                VerityGlassPillButton(
                    text = "Login",
                    onClick = submitAction,
                    darkCore = true,
                    modifier = Modifier.testTag("login_submit_button")
                )

                Spacer(modifier = Modifier.weight(1f, fill = true))
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            containerColor = CharcoalBase,
            title = { Text("Reset Password", color = PlatinumWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your account email to request a password reset link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverText
                    )
                    VerityGlassInputField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        placeholder = "Enter your email",
                        leadingIcon = Icons.Outlined.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        testTag = "forgot_password_email_input"
                    )
                    if (resetSentMessage != null) {
                        Text(
                            text = resetSentMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = PlatinumWhite
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = resetEmail.trim()
                        if (android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
                            resetSentMessage = "Sending reset instructions…"
                            onRequestPasswordReset(trimmed) { error ->
                                resetSentMessage = error ?: "If an account matches, reset instructions have been sent."
                            }
                        } else {
                            resetSentMessage = "Please enter a valid email address."
                        }
                    }
                ) {
                    Text("Send Reset", color = PlatinumWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Close", color = SilverText)
                }
            }
        )
    }
}

/**
 * Renders the exact smoky silver-grey mist + blurred dark central silhouette + deep black lower fade
 * seen in the user's reference login image.
 */
@Composable
private fun LoginAtmosphericBackdrop(
    isObsidianMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFB5B8BF),
                        0.28f to Color(0xFF9EA2AA),
                        0.55f to Color(0xFF42454D),
                        0.82f to Color(0xFF0B0C0F),
                        1.0f to Color(0xFF050608)
                    )
                )
            )
            .drawBehind {
                // Bright cool-silver outer mist at upper-left and upper-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD5D7DC).copy(alpha = 0.85f),
                            Color(0xFFB8BBC2).copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.06f, size.height * 0.10f),
                        radius = size.width * 0.65f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD5D7DC).copy(alpha = 0.78f),
                            Color(0xFFB3B7BE).copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.94f, size.height * 0.16f),
                        radius = size.width * 0.60f
                    )
                )

                // Soft blurred dark central silhouette (upper head + neck + shoulders)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF050608),
                            0.52f to Color(0xFF07080B).copy(alpha = 0.94f),
                            0.82f to Color(0xFF14161B).copy(alpha = 0.55f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(size.width * 0.48f, size.height * 0.30f),
                        radius = size.width * 0.44f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF050608),
                            0.55f to Color(0xFF06070A).copy(alpha = 0.92f),
                            0.86f to Color(0xFF121419).copy(alpha = 0.48f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.50f),
                        radius = size.width * 0.40f
                    )
                )
                // Deep black lower torso pool
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF040507),
                            0.65f to Color(0xFF050608).copy(alpha = 0.96f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(size.width * 0.46f, size.height * 0.80f),
                        radius = size.width * 0.64f
                    )
                )

                // Subtle mid-lower silver-grey edge mist on left and right flanks
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9EA2AB).copy(alpha = if (isObsidianMode) 0.42f else 0.50f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.96f, size.height * 0.72f),
                        radius = size.width * 0.38f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8C9099).copy(alpha = if (isObsidianMode) 0.34f else 0.44f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.02f, size.height * 0.78f),
                        radius = size.width * 0.35f
                    )
                )
            },
        content = content
    )
}
