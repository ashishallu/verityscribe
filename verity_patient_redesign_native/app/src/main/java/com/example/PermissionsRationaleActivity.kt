package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.VerityTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VerityTheme(darkTheme = true) {
                VerityGlassBackdrop(isObsidianMode = true) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VerityGlassCard(
                            strong = true,
                            modifier = Modifier
                                .widthIn(max = 540.dp)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = "Health privacy",
                                    tint = IceBlueText
                                )
                                Text(
                                    text = "Google Fit & Health Connect Privacy",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PlatinumWhite
                                )
                                Text(
                                    text = "Verity requests read-only access to Heart Rate, Sleep Sessions, Blood Oxygen (SpO2), and Steps from Google Fit via Android Health Connect solely to update your Health Snapshot on this device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SilverText
                                )
                                Text(
                                    text = "Your vitals stay encrypted on-device and are never shared without your consent. You can revoke access at any time in Android Health Connect settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SilverText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                VerityGlassPillButton(
                                    text = "Done",
                                    onClick = { finish() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
