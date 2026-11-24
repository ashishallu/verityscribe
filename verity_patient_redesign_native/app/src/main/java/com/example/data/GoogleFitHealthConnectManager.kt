package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class HealthBridgeStatus(val label: String) {
    CONNECTED("Connected"),
    PERMISSIONS_NEEDED("Permission Needed"),
    UPDATE_REQUIRED("Update Required"),
    UNAVAILABLE("Health Connect Unavailable")
}

data class GoogleFitSyncSnapshot(
    val status: HealthBridgeStatus = HealthBridgeStatus.PERMISSIONS_NEEDED,
    val isGoogleFitInstalled: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val heartRateBpm: Long? = null,
    val sleepFormatted: String? = null,
    val spo2Percent: Int? = null,
    val stepsToday: Long? = null,
    val lastSyncedLabel: String? = null,
    val statusDetail: String = "Connect Google Fit via Android Health Connect to sync live vitals."
) {
    val totalRequiredPermissions: Int get() = 4
    val grantedCount: Int get() = grantedPermissions.size
}

class GoogleFitHealthConnectManager(private val context: Context) {

    companion object {
        const val GOOGLE_FIT_PACKAGE = "com.google.android.apps.fitness"
        const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val requiredPermissions: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }

    fun isGoogleFitInstalled(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    GOOGLE_FIT_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(GOOGLE_FIT_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)
    }

    suspend fun inspectAndSync(): GoogleFitSyncSnapshot {
        val fitInstalled = isGoogleFitInstalled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return GoogleFitSyncSnapshot(
                status = HealthBridgeStatus.UNAVAILABLE,
                isGoogleFitInstalled = fitInstalled,
                statusDetail = "Health Connect requires Android 8.0 (API 26) or higher."
            )
        }

        val sdkStatus = runCatching {
            HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER_PACKAGE)
        }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

        return when (sdkStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                GoogleFitSyncSnapshot(
                    status = HealthBridgeStatus.UNAVAILABLE,
                    isGoogleFitInstalled = fitInstalled,
                    statusDetail = "Android Health Connect is not available on this device. Install Health Connect & Google Fit to sync live vitals."
                )
            }

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                GoogleFitSyncSnapshot(
                    status = HealthBridgeStatus.UPDATE_REQUIRED,
                    isGoogleFitInstalled = fitInstalled,
                    statusDetail = "Health Connect requires an update before Google Fit vitals can be synced."
                )
            }

            else -> readFromHealthConnect(fitInstalled)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun readFromHealthConnect(fitInstalled: Boolean): GoogleFitSyncSnapshot {
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
            ?: return GoogleFitSyncSnapshot(
                status = HealthBridgeStatus.UNAVAILABLE,
                isGoogleFitInstalled = fitInstalled,
                statusDetail = "Could not initialize Health Connect client."
            )

        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
        val grantedRequired = granted.intersect(requiredPermissions)

        if (grantedRequired.isEmpty()) {
            return GoogleFitSyncSnapshot(
                status = HealthBridgeStatus.PERMISSIONS_NEEDED,
                isGoogleFitInstalled = fitInstalled,
                grantedPermissions = emptySet(),
                statusDetail = if (fitInstalled) {
                    "Google Fit detected. Tap Connect Google Fit to authorize Health Connect vitals access."
                } else {
                    "Tap Connect Google Fit to authorize Health Connect permissions for live steps, sleep, heart rate, and SpO2."
                }
            )
        }

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()

        val stepsToday = if (HealthPermission.getReadPermission(StepsRecord::class) in grantedRequired) {
            runCatching {
                val agg = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                    )
                )
                val aggregatedCount = agg[StepsRecord.COUNT_TOTAL]
                if (aggregatedCount != null && aggregatedCount > 0L) {
                    aggregatedCount
                } else {
                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = StepsRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                        )
                    )
                    response.records.takeIf { it.isNotEmpty() }?.sumOf { it.count }
                }
            }.getOrNull()
        } else null

        val latestHeartRate = if (HealthPermission.getReadPermission(HeartRateRecord::class) in grantedRequired) {
            runCatching {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)
                    )
                )
                response.records
                    .flatMap { it.samples }
                    .maxByOrNull { it.time }
                    ?.beatsPerMinute
            }.getOrNull()
        } else null

        val latestSleepFormatted = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in grantedRequired) {
            runCatching {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofHours(36)), now)
                    )
                )
                val latestSession = response.records.maxByOrNull { it.endTime }
                latestSession?.let { session ->
                    val minutes = Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0)
                    val hours = minutes / 60
                    val remMinutes = minutes % 60
                    "${hours}h ${remMinutes}m"
                }
            }.getOrNull()
        } else null

        val latestSpo2 = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in grantedRequired) {
            runCatching {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = OxygenSaturationRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofHours(48)), now)
                    )
                )
                response.records
                    .maxByOrNull { it.time }
                    ?.percentage
                    ?.value
                    ?.roundToInt()
            }.getOrNull()
        } else null

        val hasAnyLiveMetric = listOf(stepsToday, latestHeartRate, latestSleepFormatted, latestSpo2).any { it != null }
        val syncStamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val statusMsg = when {
            hasAnyLiveMetric -> "Synced live vitals from Google Fit via Health Connect at $syncStamp."
            grantedRequired.size < requiredPermissions.size ->
                "Authorized ${grantedRequired.size}/4 vitals. Grant all permissions or log activity in Google Fit to populate live metrics."
            else ->
                "Connected to Health Connect ($syncStamp). Waiting for Google Fit to write recent Heart Rate, Sleep, SpO2, or Steps."
        }

        return GoogleFitSyncSnapshot(
            status = if (grantedRequired.size == requiredPermissions.size) {
                HealthBridgeStatus.CONNECTED
            } else {
                HealthBridgeStatus.PERMISSIONS_NEEDED
            },
            isGoogleFitInstalled = fitInstalled,
            grantedPermissions = grantedRequired,
            heartRateBpm = latestHeartRate,
            sleepFormatted = latestSleepFormatted,
            spo2Percent = latestSpo2,
            stepsToday = stepsToday,
            lastSyncedLabel = syncStamp,
            statusDetail = statusMsg
        )
    }

    fun formatSteps(steps: Long): String {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(steps)
    }

    fun openGoogleFitOrStore(): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(GOOGLE_FIT_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(launchIntent) }.isSuccess
        }
        val playIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$GOOGLE_FIT_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(playIntent) }.isSuccess
    }

    fun openHealthConnectSettings(): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.android.vending")
                data = Uri.parse(
                    "market://details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
                )
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
