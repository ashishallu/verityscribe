package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.MedicalTeal

@Composable
fun SignInScreen(
    onSignIn: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF084B61)),
        contentAlignment = Alignment.Center
    ) {
        // Full-bleed atmospheric doctor portrait backdrop
        val backdropModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier
                .fillMaxSize()
                .blur(12.dp)
        } else {
            Modifier.fillMaxSize()
        }

        Image(
            painter = painterResource(id = R.drawable.img_login_doctor_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = backdropModifier
        )

        // Ocean Teal -> deep slate atmospheric gradient scrim matching app theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC0A5E7A),
                            Color(0x88207893),
                            Color(0xA60E4E65),
                            Color(0xE6073140)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(34.dp))

            // Brand Wordmark matching mockup
            Text(
                text = "VerityScribe",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 25.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Title matching mockup
            Text(
                text = "Sign In to Your\nClinical Workspace",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(38.dp))

            // Email Field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Email",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(start = 4.dp)
                )
                FrostedGlassTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Enter your email",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardType = KeyboardType.Email,
                    testTag = "sign_in_email_input"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Password Field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Password",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(start = 4.dp)
                )
                FrostedGlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Enter your password",
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                    testTag = "sign_in_password_input"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Remember me + Forgot Password row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { rememberMe = !rememberMe }
                        .testTag("remember_me_toggle")
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (rememberMe) Color.White.copy(alpha = 0.25f) else Color.Transparent
                            )
                            .border(
                                width = 1.4.dp,
                                color = Color.White.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(5.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rememberMe) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Remember me checked",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "Remember me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }

                Text(
                    text = "Forgot Password?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showForgotPasswordDialog = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("forgot_password_button")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Frosted Glass Pill Login Button matching mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.36f),
                                Color.White.copy(alpha = 0.20f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.20f),
                                Color.White.copy(alpha = 0.42f)
                            )
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable {
                        onSignIn(email, password, rememberMe)
                    }
                    .testTag("sign_in_submit_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = ClinicalSurface,
            title = {
                Text(
                    text = "Reset Physician Password",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
            },
            text = {
                Text(
                    text = "Password recovery is managed by your hospital. Please contact your hospital administrator for help resetting your clinical account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Got it", color = MedicalTeal)
                }
            }
        )
    }
}

@Composable
private fun FrostedGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.30f)
                    )
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                        fontSize = 15.sp,
                        color = Color.White
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword && !passwordVisible) KeyboardType.Password else keyboardType
                    ),
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
            if (isPassword && onTogglePasswordVisibility != null) {
                IconButton(
                    onClick = onTogglePasswordVisibility,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
