package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.Medicine
import com.example.model.NearbyStore
import com.example.model.PharmacyPrice
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassChip
import com.example.ui.components.VerityPillIconBadge
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText

@Composable
fun MedicineDetailScreen(
    medicine: Medicine,
    isDark: Boolean,
    onBack: () -> Unit,
    onSelectPharmacy: (PharmacyPrice) -> Unit,
    onCallStore: (NearbyStore) -> Unit,
    modifier: Modifier = Modifier
) {
    VerityGlassBackdrop(
        isObsidianMode = isDark,
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
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("medicine_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PlatinumWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = PlatinumWhite
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Hero Icon + Name Dosage
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            VerityPillIconBadge(size = 82.dp, cornerRadius = 22.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "${medicine.name} ${medicine.dosage}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PlatinumWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Clinical Details Glass Card
                    item {
                        VerityGlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                DetailGlassRow("Purpose", medicine.purpose)
                                DetailGlassRow("How to take", medicine.howToTake)
                                DetailGlassRow("Food instructions", medicine.foodInstructions)
                                DetailGlassRow("Possible side effects", medicine.sideEffects)
                                DetailGlassRow("Doctor notes", medicine.doctorNotes)
                            }
                        }
                    }

                    // Quantity history
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Quantity history",
                                style = MaterialTheme.typography.titleLarge,
                                color = PlatinumWhite
                            )
                            VerityGlassCard(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 16.dp)
                                ) {
                                    QuantityGlassColumn(
                                        label = "Initial quantity",
                                        value = if (medicine.inventoryTracked) "${medicine.initialQuantity} tablets" else "Not tracked",
                                        modifier = Modifier.weight(1f)
                                    )
                                    QuantityGlassColumn(
                                        label = "Remaining",
                                        value = if (medicine.inventoryTracked) "${medicine.remainingQuantity} tablets" else "Not tracked",
                                        modifier = Modifier.weight(1f)
                                    )
                                    QuantityGlassColumn(
                                        label = "Daily usage",
                                        value = if (medicine.dailyUsage > 0) "${medicine.dailyUsage}" else medicine.shortSchedule,
                                        modifier = Modifier.weight(0.75f)
                                    )
                                }
                            }
                        }
                    }

                    // Compare medicine prices
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Compare medicine prices",
                                style = MaterialTheme.typography.titleLarge,
                                color = PlatinumWhite
                            )
                            medicine.prices.forEach { price ->
                                VerityGlassCard(
                                    strong = price.isBestValue,
                                    cornerRadius = 20.dp,
                                    onClick = { onSelectPharmacy(price) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pharmacy_price_${price.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VerityPillIconBadge(size = 42.dp, cornerRadius = 12.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = price.vendorName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                            Text(
                                                text = price.discountInfo,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SilverText
                                            )
                                            Text(
                                                text = price.etaInfo,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PlatinumWhite.copy(alpha = 0.88f)
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "₹${price.priceRupees}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = PlatinumWhite
                                            )
                                            if (price.isBestValue) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                VerityGlassChip(
                                                    text = "BEST VALUE",
                                                    highlighted = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (medicine.prices.isEmpty()) {
                                Text("Pharmacy pricing is not available from the connected services.", color = SilverText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Nearby medical stores
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Nearby medical stores",
                                style = MaterialTheme.typography.titleLarge,
                                color = PlatinumWhite
                            )
                            VerityGlassCard(
                                cornerRadius = 22.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    medicine.nearbyStores.forEachIndexed { idx, store ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0x21FFFFFF))
                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Storefront,
                                                    contentDescription = null,
                                                    tint = PlatinumWhite,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = store.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite
                                                )
                                                Text(
                                                    text = "${store.distanceKm} • ${store.stockStatus} • ${store.openUntil}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SilverText
                                                )
                                            }
                                            IconButton(
                                                onClick = { onCallStore(store) },
                                                modifier = Modifier.testTag("call_store_${store.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Call,
                                                    contentDescription = "Call ${store.name}",
                                                    tint = PlatinumWhite
                                                )
                                            }
                                        }
                                        if (idx < medicine.nearbyStores.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = Color(0x1FFFFFFF)
                                            )
                                        }
                                    }
                                    if (medicine.nearbyStores.isEmpty()) {
                                        Text(
                                            text = "Store stock and location data are not available from the connected services.",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                            color = SilverText,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DetailGlassRow(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = PlatinumWhite
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = SilverText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuantityGlassColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SilverText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = PlatinumWhite
        )
    }
}
