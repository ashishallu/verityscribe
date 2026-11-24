package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.components.VerityGlassSecondaryPill
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PeriwinkleMist
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import com.example.viewmodel.VerityUiState

@Composable
fun InsuranceDashboardScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onCallSupport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val info = uiState.insuranceInfo

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
                ProfileSubPageTopBar(
                    title = "Insurance dashboard",
                    onBack = onBack,
                    testTagPrefix = "insurance"
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ProfileSecurityBanner(
                            text = "Insurance dashboard securely managed in Verity."
                        )
                    }

                    // Plan Card
                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            strong = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = info.planName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = PlatinumWhite
                                    )
                                    if (info.cashlessEligible) {
                                        VerityGlassChip(
                                            text = "Cashless Active",
                                            highlighted = true
                                        )
                                    }
                                }
                                Text(
                                    text = "Policy no. ${info.policyNumber} • Member ID ${info.memberId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PeriwinkleMist
                                )
                            }
                        }
                    }

                    // Coverage Card
                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Coverage",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PlatinumWhite
                                )
                                Text(
                                    text = "${info.annualCover} • ${info.availableCover}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SilverText
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x2BFFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(info.utilizationFraction)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(IceBlueText, SoftLavenderText)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Renewal Card
                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Renewal",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PlatinumWhite
                                )
                                Text(
                                    text = "${info.renewalAmount} ${info.renewalDueDate}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SilverText
                                )
                            }
                        }
                    }

                    // Support Card
                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Support",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PlatinumWhite
                                )
                                Text(
                                    text = "${info.agentName} • ${info.agentPhone} • Cashless eligible",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SilverText
                                )
                                VerityGlassSecondaryPill(
                                    text = "Call ${info.agentName}",
                                    leadingIcon = Icons.Filled.Call,
                                    onClick = { onCallSupport(info.agentPhone) },
                                    modifier = Modifier.testTag("insurance_call_support_button")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkedHospitalsScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onBookVisit: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                ProfileSubPageTopBar(
                    title = "Linked hospitals",
                    subtitle = "${uiState.linkedHospitals.size} connected care centers",
                    onBack = onBack,
                    testTagPrefix = "linked_hospitals"
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ProfileSecurityBanner(
                            text = "Prescriptions, lab reports, and consultation summaries sync automatically with your linked hospitals."
                        )
                    }

                    items(uiState.linkedHospitals, key = { it.id }) { hospital ->
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            strong = hospital.isPrimary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(IceBlueText.copy(alpha = 0.16f))
                                                .border(1.dp, IceBlueText.copy(alpha = 0.40f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.LocalHospital,
                                                contentDescription = null,
                                                tint = IceBlueText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = hospital.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = PlatinumWhite
                                            )
                                            Text(
                                                text = hospital.syncStatus,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = IceBlueText
                                            )
                                        }
                                    }

                                    if (hospital.isPrimary) {
                                        VerityGlassChip(
                                            text = "Primary",
                                            highlighted = true
                                        )
                                    } else if (hospital.isCashlessEnabled) {
                                        VerityGlassChip(
                                            text = "Cashless",
                                            highlighted = false
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = PeriwinkleMist,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${hospital.address} • ${hospital.distanceLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SilverText
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    hospital.departments.forEach { dept ->
                                        VerityGlassChip(text = dept, highlighted = false)
                                    }
                                }

                                HorizontalDivider(color = Color(0x21FFFFFF))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = hospital.lastVisitLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PeriwinkleMist
                                    )
                                    VerityGlassChip(
                                        text = "Book Visit",
                                        highlighted = true,
                                        onClick = onBookVisit
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyContactsScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onCallContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
                ProfileSubPageTopBar(
                    title = "Emergency contacts",
                    subtitle = "${uiState.emergencyContacts.size} trusted contacts",
                    onBack = onBack,
                    testTagPrefix = "emergency_contacts"
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ProfileSecurityBanner(
                            text = "Trusted contacts are notified first in emergencies and visible to paramedics when needed."
                        )
                    }

                    items(uiState.emergencyContacts, key = { it.id }) { contact ->
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            strong = contact.isPrimary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(SoftLavenderText.copy(alpha = 0.18f))
                                                .border(1.dp, SoftLavenderText.copy(alpha = 0.42f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContactEmergency,
                                                contentDescription = null,
                                                tint = SoftLavenderText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = PlatinumWhite
                                            )
                                            Text(
                                                text = "${contact.relation} • ${contact.phoneNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PeriwinkleMist
                                            )
                                        }
                                    }
                                    if (contact.isPrimary) {
                                        VerityGlassChip(
                                            text = "Primary ICE",
                                            highlighted = true
                                        )
                                    }
                                }

                                VerityGlassSecondaryPill(
                                    text = "Call ${contact.name}",
                                    leadingIcon = Icons.Filled.Call,
                                    onClick = { onCallContact(contact.phoneNumber) },
                                    modifier = Modifier.testTag("call_contact_${contact.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacySecurityScreen(
    uiState: VerityUiState,
    onBack: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                ProfileSubPageTopBar(
                    title = "Privacy & security",
                    onBack = onBack,
                    testTagPrefix = "privacy_security"
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ProfileSecurityBanner(
                            text = "Your health profile, prescriptions, and credentials are salted and encrypted on this device."
                        )
                    }

                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = IceBlueText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "On-Device Credential Protection",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PlatinumWhite
                                    )
                                }
                                Text(
                                    text = "Sign-in credentials use salted SHA-256 hashing via Android DataStore. Clinical summaries are only shared with your doctor when you tap Share.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SilverText
                                )
                            }
                        }
                    }

                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = SoftLavenderText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Google Fit & Health Connect Permissions",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PlatinumWhite
                                    )
                                }
                                Text(
                                    text = "Authorized vitals: ${uiState.googleFitState.grantedCount} of ${uiState.googleFitState.totalRequiredPermissions} read permissions (Heart rate, Sleep, SpO2, Steps).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SilverText
                                )
                                VerityGlassPillButton(
                                    text = "Manage Health Connect Permissions",
                                    leadingIcon = Icons.Filled.Settings,
                                    onClick = onOpenHealthConnectSettings,
                                    modifier = Modifier.testTag("privacy_manage_health_connect")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
