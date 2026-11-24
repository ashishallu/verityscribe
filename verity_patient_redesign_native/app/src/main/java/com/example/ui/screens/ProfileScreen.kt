package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import com.example.viewmodel.VerityUiState
import java.io.File

@Composable
fun ProfileScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onUploadPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onOpenMedicalHistory: () -> Unit,
    onOpenEmergencyContacts: () -> Unit,
    onOpenInsurance: () -> Unit,
    onOpenLinkedHospitals: () -> Unit,
    onOpenConnectedDevices: () -> Unit,
    onOpenPrivacySecurity: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadPhoto(uri)
        }
    }
    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val hasPhoto = remember(uiState.profilePhotoPath) {
        val path = uiState.profilePhotoPath
        !path.isNullOrBlank() && File(path).exists()
    }

    VerityGlassBackdrop(
        isObsidianMode = uiState.darkMode,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xD90E1015), Color(0x99171A21))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("profile_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PlatinumWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.titleLarge,
                            color = PlatinumWhite
                        )
                    }
                    HorizontalDivider(
                        color = Color(0x26FFFFFF),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                // Profile Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(94.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x38FFFFFF), Color(0x14FFFFFF))
                                        )
                                    )
                                    .border(1.2.dp, Color(0x66FFFFFF), CircleShape)
                                    .clickable(onClick = launchPhotoPicker)
                                    .testTag("profile_avatar_upload_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasPhoto) {
                                    AsyncImage(
                                        model = File(uiState.profilePhotoPath!!),
                                        contentDescription = "${uiState.profile.name} profile photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = "Profile photo placeholder",
                                        tint = PlatinumWhite,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF424857))
                                    .border(1.dp, Color(0x99FFFFFF), CircleShape)
                                    .clickable(onClick = launchPhotoPicker),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Upload photo",
                                    tint = PlatinumWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = launchPhotoPicker)
                                    .testTag("profile_upload_photo_chip"),
                                contentAlignment = Alignment.Center
                            ) {
                                VerityGlassChip(
                                    text = if (hasPhoto) "Change photo" else "Upload photo",
                                    highlighted = true
                                )
                            }
                            if (hasPhoto) {
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(onClick = onRemovePhoto)
                                        .testTag("profile_remove_photo_chip"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VerityGlassChip(
                                        text = "Remove",
                                        highlighted = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.profile.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = PlatinumWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verity ID • ${uiState.profile.verityId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceBlueText
                        )
                        if (uiState.loggedInEmail.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.loggedInEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftLavenderText
                            )
                        }
                    }
                }

                // Your care network
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Your care network",
                            style = MaterialTheme.typography.titleLarge,
                            color = PlatinumWhite
                        )
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                ProfileRow(
                                    icon = Icons.Filled.MedicalInformation,
                                    title = "Medical history",
                                    subtitle = "Conditions, allergies, documents and emergency details",
                                    onClick = onOpenMedicalHistory,
                                    testTag = "profile_medical_history"
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color(0x21FFFFFF)
                                )
                                ProfileRow(
                                    icon = Icons.Filled.ContactEmergency,
                                    title = "Emergency contacts",
                                    subtitle = "${uiState.profile.emergencyContactsCount} trusted contacts",
                                    onClick = onOpenEmergencyContacts,
                                    testTag = "profile_emergency_contacts"
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color(0x21FFFFFF)
                                )
                                ProfileRow(
                                    icon = Icons.Outlined.Shield,
                                    title = "Insurance",
                                    subtitle = uiState.profile.insurancePlan,
                                    onClick = onOpenInsurance,
                                    testTag = "profile_insurance"
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color(0x21FFFFFF)
                                )
                                ProfileRow(
                                    icon = Icons.Filled.LocalHospital,
                                    title = "Linked hospitals",
                                    subtitle = uiState.profile.linkedHospitalsSummary,
                                    onClick = onOpenLinkedHospitals,
                                    testTag = "profile_linked_hospitals"
                                )
                            }
                        }
                    }
                }

                // Preferences
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Preferences",
                            style = MaterialTheme.typography.titleLarge,
                            color = PlatinumWhite
                        )
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                ProfileRow(
                                    icon = Icons.Outlined.Watch,
                                    title = "Connected devices",
                                    subtitle = uiState.profile.connectedDeviceSummary,
                                    onClick = onOpenConnectedDevices,
                                    testTag = "profile_connected_devices"
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color(0x21FFFFFF)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 52.dp)
                                        .clickable { onToggleDarkMode(!uiState.darkMode) }
                                        .padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DarkMode,
                                        contentDescription = null,
                                        tint = PlatinumWhite,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Deep slate glass",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = PlatinumWhite
                                        )
                                        Text(
                                            text = "Smoky slate grey vs brighter silver mist",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SilverText
                                        )
                                    }
                                    Switch(
                                        checked = uiState.darkMode,
                                        onCheckedChange = onToggleDarkMode,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ObsidianBlack,
                                            checkedTrackColor = PlatinumWhite,
                                            uncheckedThumbColor = PlatinumWhite,
                                            uncheckedTrackColor = Color(0x33FFFFFF),
                                            uncheckedBorderColor = Color(0x52FFFFFF)
                                        ),
                                        modifier = Modifier.testTag("profile_dark_mode_switch")
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color(0x21FFFFFF)
                                )
                                ProfileRow(
                                    icon = Icons.Outlined.Lock,
                                    title = "Privacy & security",
                                    subtitle = "Control your health data",
                                    onClick = onOpenPrivacySecurity,
                                    testTag = "profile_privacy_security"
                                )
                            }
                        }
                    }
                }

                // Sign out action
                item {
                    VerityGlassSecondaryPill(
                        text = "Sign out",
                        leadingIcon = Icons.AutoMirrored.Filled.Logout,
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_logout_button")
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PlatinumWhite,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = PlatinumWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PeriwinkleMist
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SilverText
        )
    }
}
