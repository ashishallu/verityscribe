package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.VeritySeedData
import com.example.model.ConsultationType
import com.example.model.DoctorDirectoryEntry
import com.example.model.HospitalOption
import com.example.ui.components.VerityGlassBackdrop
import com.example.ui.components.VerityGlassCard
import com.example.ui.components.VerityGlassPillButton
import com.example.ui.theme.GlassAlertRed
import com.example.ui.theme.IceBlueText
import com.example.ui.theme.PlatinumWhite
import com.example.ui.theme.SilverText
import com.example.ui.theme.SoftLavenderText
import java.util.Calendar
import kotlinx.coroutines.delay

private enum class BookingDropdownSection {
    HOSPITAL,
    DEPARTMENT,
    DOCTOR,
    CONSULTATION_TYPE
}

@Composable
fun BookAppointmentScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onConfirmBooking: (
        doctorName: String,
        specialty: String,
        hospital: String,
        dateLabel: String,
        timeLabel: String,
        consultationType: String,
        reason: String,
        notes: String,
        avatarRes: Int
    ) -> Unit,
    hospitals: List<HospitalOption> = emptyList(),
    doctors: List<DoctorDirectoryEntry> = emptyList(),
    modifier: Modifier = Modifier
) {
    val timeSlots = VeritySeedData.bookingTimeSlots
    val dateOptions = remember { generateNext15Days() }
    var currentMinuteOfDay by remember { mutableIntStateOf(Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }) }

    var selectedHospital by remember { mutableStateOf<HospitalOption?>(null) }
    var selectedDepartment by remember { mutableStateOf<String?>(null) }
    var selectedDoctor by remember { mutableStateOf<DoctorDirectoryEntry?>(null) }

    var selectedDateLabel by remember { mutableStateOf<String?>(null) }
    var isDateStripExpanded by remember { mutableStateOf(true) }
    var selectedTimeLabel by remember { mutableStateOf<String?>(null) }
    var selectedConsultationType by remember { mutableStateOf(ConsultationType.IN_PERSON) }

    var reasonForVisit by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var activeDropdown by remember { mutableStateOf<BookingDropdownSection?>(null) }
    var showAllTimeSlots by remember { mutableStateOf(false) }
    var showReviewPaymentDialog by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    var hospitalWidthPx by remember { mutableIntStateOf(0) }
    var hospitalHeightPx by remember { mutableIntStateOf(0) }
    var departmentWidthPx by remember { mutableIntStateOf(0) }
    var departmentHeightPx by remember { mutableIntStateOf(0) }
    var doctorWidthPx by remember { mutableIntStateOf(0) }
    var doctorHeightPx by remember { mutableIntStateOf(0) }
    var typeWidthPx by remember { mutableIntStateOf(0) }
    var typeHeightPx by remember { mutableIntStateOf(0) }

    val timeListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            delay(30_000)
        }
    }

    val selectedDateIsToday = dateOptions.firstOrNull { it.fullLabel == selectedDateLabel }?.isToday == true
    val availableTimeSlots = availableBookingSlots(
        slots = timeSlots,
        isToday = selectedDateIsToday,
        currentHour = currentMinuteOfDay / 60,
        currentMinute = currentMinuteOfDay % 60
    )

    LaunchedEffect(selectedTimeLabel, showAllTimeSlots, selectedDateLabel, currentMinuteOfDay) {
        if (selectedTimeLabel !in availableTimeSlots) selectedTimeLabel = null
        if (!showAllTimeSlots && selectedTimeLabel != null) {
            val idx = availableTimeSlots.indexOf(selectedTimeLabel)
            if (idx >= 0) {
                timeListState.animateScrollToItem(idx)
            }
        }
    }

    val availableDepartments = remember(selectedHospital) {
        selectedHospital?.departments
            ?: hospitals.flatMap { it.departments }.distinct()
    }

    val availableDoctors = remember(selectedHospital, selectedDepartment) {
        doctors.filter { doc ->
            (selectedHospital == null || doc.hospital == selectedHospital?.name) &&
                (selectedDepartment == null || doc.department == selectedDepartment)
        }
    }

    val dateHeaderArrowRotation by animateFloatAsState(
        targetValue = if (isDateStripExpanded) 180f else 0f,
        animationSpec = tween(220),
        label = "visit_date_header_arrow"
    )

    AnimatedContent(
        targetState = showAllTimeSlots,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "booking_view_all_slots_transition",
        modifier = modifier
    ) { showingAllSlots ->
        if (showingAllSlots) {
            AllTimeSlotsScreen(
                isDark = isDark,
                selectedDateLabel = selectedDateLabel ?: dateOptions.first().fullLabel,
                selectedTimeLabel = selectedTimeLabel,
                allSlots = availableTimeSlots,
                onSelectSlotAndReturn = { slot ->
                    selectedTimeLabel = slot
                    validationMessage = null
                    showAllTimeSlots = false
                },
                onClose = { showAllTimeSlots = false }
            )
        } else {
            VerityGlassBackdrop(isObsidianMode = isDark) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Translucent frosted glass top bar
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
                                    modifier = Modifier.testTag("book_appointment_back")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = PlatinumWhite
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Book an appointment",
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            // Hero title
                            item {
                                Column {
                                    Text(
                                        text = "Find your care team",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PlatinumWhite
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Choose a hospital, specialist and convenient time.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = IceBlueText
                                    )
                                }
                            }

                            // Card 1: Hospital, Department, Doctor (Floating glassy popups)
                            item {
                                VerityGlassCard(
                                    cornerRadius = 24.dp,
                                    strong = true,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Hospital
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Hospital",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                            Box {
                                                BookingDropdownField(
                                                    icon = Icons.Filled.LocalHospital,
                                                    text = selectedHospital?.name ?: "Select hospital",
                                                    isPlaceholder = selectedHospital == null,
                                                    expanded = activeDropdown == BookingDropdownSection.HOSPITAL,
                                                    accentTint = IceBlueText,
                                                    onClick = {
                                                        activeDropdown = if (activeDropdown == BookingDropdownSection.HOSPITAL) {
                                                            null
                                                        } else {
                                                            BookingDropdownSection.HOSPITAL
                                                        }
                                                    },
                                                    testTag = "booking_hospital_dropdown",
                                                    modifier = Modifier.onGloballyPositioned { coords ->
                                                        hospitalWidthPx = coords.size.width
                                                        hospitalHeightPx = coords.size.height
                                                    }
                                                )
                                                GlassyFloatingDropdown(
                                                    expanded = activeDropdown == BookingDropdownSection.HOSPITAL,
                                                    anchorWidthPx = hospitalWidthPx,
                                                    anchorHeightPx = hospitalHeightPx,
                                                    onDismissRequest = { activeDropdown = null }
                                                ) {
                                                    hospitals.forEachIndexed { index, hospital ->
                                                        if (index > 0) {
                                                            HorizontalDivider(color = Color(0x1CFFFFFF))
                                                        }
                                                        GlassyDropdownOptionRow(
                                                            title = hospital.name,
                                                            subtitle = hospital.departments.joinToString(" • "),
                                                            trailingBadge = "${hospital.departments.size} depts",
                                                            selected = selectedHospital?.id == hospital.id,
                                                            onClick = {
                                                                selectedHospital = hospital
                                                                if (selectedDepartment !in hospital.departments) {
                                                                    selectedDepartment = null
                                                                }
                                                                if (selectedDoctor?.hospital != hospital.name) {
                                                                    selectedDoctor = null
                                                                }
                                                                activeDropdown = null
                                                            },
                                                            testTag = "booking_hospital_option_${hospital.id}"
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Department
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Department",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                            Box {
                                                BookingDropdownField(
                                                    icon = Icons.Filled.Category,
                                                    text = selectedDepartment ?: "Select department",
                                                    isPlaceholder = selectedDepartment == null,
                                                    expanded = activeDropdown == BookingDropdownSection.DEPARTMENT,
                                                    accentTint = SoftLavenderText,
                                                    onClick = {
                                                        activeDropdown = if (activeDropdown == BookingDropdownSection.DEPARTMENT) {
                                                            null
                                                        } else {
                                                            BookingDropdownSection.DEPARTMENT
                                                        }
                                                    },
                                                    testTag = "booking_department_dropdown",
                                                    modifier = Modifier.onGloballyPositioned { coords ->
                                                        departmentWidthPx = coords.size.width
                                                        departmentHeightPx = coords.size.height
                                                    }
                                                )
                                                GlassyFloatingDropdown(
                                                    expanded = activeDropdown == BookingDropdownSection.DEPARTMENT,
                                                    anchorWidthPx = departmentWidthPx,
                                                    anchorHeightPx = departmentHeightPx,
                                                    onDismissRequest = { activeDropdown = null }
                                                ) {
                                                    availableDepartments.forEachIndexed { index, dept ->
                                                        if (index > 0) {
                                                            HorizontalDivider(color = Color(0x1CFFFFFF))
                                                        }
                                                        val docCount = doctors.count {
                                                            it.department == dept &&
                                                                (selectedHospital == null || it.hospital == selectedHospital?.name)
                                                        }
                                                        GlassyDropdownOptionRow(
                                                            title = dept,
                                                            trailingBadge = if (docCount > 0) "$docCount available" else null,
                                                            selected = selectedDepartment == dept,
                                                            onClick = {
                                                                selectedDepartment = dept
                                                                if (selectedDoctor?.department != dept) {
                                                                    selectedDoctor = null
                                                                }
                                                                activeDropdown = null
                                                            },
                                                            testTag = "booking_department_option_${dept.lowercase().replace(" ", "_")}"
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Doctor
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Doctor",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                            Box {
                                                BookingDropdownField(
                                                    icon = Icons.Filled.PersonOutline,
                                                    text = selectedDoctor?.let { "${it.name} • ${it.department}" }
                                                        ?: "Select available doctor",
                                                    isPlaceholder = selectedDoctor == null,
                                                    expanded = activeDropdown == BookingDropdownSection.DOCTOR,
                                                    accentTint = IceBlueText,
                                                    onClick = {
                                                        activeDropdown = if (activeDropdown == BookingDropdownSection.DOCTOR) {
                                                            null
                                                        } else {
                                                            BookingDropdownSection.DOCTOR
                                                        }
                                                    },
                                                    testTag = "booking_doctor_dropdown",
                                                    modifier = Modifier.onGloballyPositioned { coords ->
                                                        doctorWidthPx = coords.size.width
                                                        doctorHeightPx = coords.size.height
                                                    }
                                                )
                                                GlassyFloatingDropdown(
                                                    expanded = activeDropdown == BookingDropdownSection.DOCTOR,
                                                    anchorWidthPx = doctorWidthPx,
                                                    anchorHeightPx = doctorHeightPx,
                                                    onDismissRequest = { activeDropdown = null }
                                                ) {
                                                    availableDoctors.forEachIndexed { index, doc ->
                                                        if (index > 0) {
                                                            HorizontalDivider(color = Color(0x1CFFFFFF))
                                                        }
                                                        GlassyDoctorOptionRow(
                                                            doctor = doc,
                                                            selected = selectedDoctor?.id == doc.id,
                                                            onClick = {
                                                                selectedDoctor = doc
                                                                if (selectedHospital == null) {
                                                                    selectedHospital = hospitals.find { it.name == doc.hospital }
                                                                }
                                                                if (selectedDepartment == null) {
                                                                    selectedDepartment = doc.department
                                                                }
                                                                validationMessage = null
                                                                activeDropdown = null
                                                            },
                                                            testTag = "booking_doctor_option_${doc.id}"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Card 2: When would you like to visit?
                            // - Dates expand/collapse when clicking the header
                            // - Picking a date closes the dates strip and reveals 30-min time slots
                            item {
                                VerityGlassCard(
                                    cornerRadius = 24.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { isDateStripExpanded = !isDateStripExpanded }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .testTag("booking_visit_date_header"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CalendarToday,
                                                    contentDescription = null,
                                                    tint = IceBlueText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "When would you like to visit?",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatinumWhite
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(Color(0x22FFFFFF))
                                                    .border(1.dp, Color(0x36FFFFFF), CircleShape)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = selectedDateLabel ?: "Select date",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (selectedDateLabel != null) IceBlueText else SoftLavenderText
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                                    contentDescription = if (isDateStripExpanded) "Hide dates" else "Show dates",
                                                    tint = if (selectedDateLabel != null) IceBlueText else SoftLavenderText,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .rotate(dateHeaderArrowRotation)
                                                )
                                            }
                                        }

                                        // Next 15 days horizontal scroll (closes upon selection, re-opens on header click)
                                        AnimatedVisibility(
                                            visible = isDateStripExpanded,
                                            enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(200)),
                                            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
                                        ) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .padding(bottom = 4.dp)
                                                    .testTag("booking_dates_row")
                                            ) {
                                                items(
                                                    items = dateOptions,
                                                    key = { it.fullLabel }
                                                ) { option ->
                                                    BookingDatePill(
                                                        option = option,
                                                        selected = option.fullLabel == selectedDateLabel,
                                                        onClick = {
                                                            selectedDateLabel = option.fullLabel
                                                            isDateStripExpanded = false
                                                            validationMessage = null
                                                        },
                                                        testTag = "booking_date_${option.dayNumber}_${option.monthShort}"
                                                    )
                                                }
                                            }
                                        }

                                        // 30-min time slot timings ONLY appear after selecting a date
                                        AnimatedVisibility(
                                            visible = selectedDateLabel != null,
                                            enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(220)),
                                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                HorizontalDivider(
                                                    color = Color(0x1EFFFFFF),
                                                    modifier = Modifier.padding(horizontal = 20.dp)
                                                )

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 20.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Filled.AccessTime,
                                                            contentDescription = null,
                                                            tint = SoftLavenderText,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Select time slot *",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = PlatinumWhite
                                                        )
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .defaultMinSize(minHeight = 48.dp)
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .clickable { if (availableTimeSlots.isNotEmpty()) showAllTimeSlots = true }
                                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                                            .testTag("booking_view_all_slots"),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "View all",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = IceBlueText
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = "View all time slots",
                                                            tint = IceBlueText,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                LazyRow(
                                                    state = timeListState,
                                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier
                                                        .padding(bottom = 4.dp)
                                                        .testTag("booking_time_slots_row")
                                                ) {
                                                    items(
                                                        items = availableTimeSlots,
                                                        key = { it }
                                                    ) { slot ->
                                                        BookingTimeSlotPill(
                                                            slot = slot,
                                                            selected = slot == selectedTimeLabel,
                                                            onClick = {
                                                                selectedTimeLabel = slot
                                                                validationMessage = null
                                                            },
                                                            testTag = "booking_slot_${slot.replace(" ", "_").replace(":", "")}"
                                                        )
                                                    }
                                                }
                                                if (availableTimeSlots.isEmpty()) {
                                                    Text(
                                                        text = "No more appointment times are available today. Please choose another date.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = SoftLavenderText,
                                                        modifier = Modifier.padding(horizontal = 20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Card 3: Consultation type, Reason for visit *, Notes, CTA button
                            item {
                                VerityGlassCard(
                                    cornerRadius = 24.dp,
                                    strong = true,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        // Consultation type (Floating glassy popup)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Consultation type",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PlatinumWhite
                                            )
                                            Box {
                                                BookingDropdownField(
                                                    icon = Icons.Filled.VideoCall,
                                                    text = selectedConsultationType.label,
                                                    isPlaceholder = false,
                                                    expanded = activeDropdown == BookingDropdownSection.CONSULTATION_TYPE,
                                                    accentTint = IceBlueText,
                                                    onClick = {
                                                        activeDropdown = if (activeDropdown == BookingDropdownSection.CONSULTATION_TYPE) {
                                                            null
                                                        } else {
                                                            BookingDropdownSection.CONSULTATION_TYPE
                                                        }
                                                    },
                                                    testTag = "booking_consultation_type",
                                                    modifier = Modifier.onGloballyPositioned { coords ->
                                                        typeWidthPx = coords.size.width
                                                        typeHeightPx = coords.size.height
                                                    }
                                                )
                                                GlassyFloatingDropdown(
                                                    expanded = activeDropdown == BookingDropdownSection.CONSULTATION_TYPE,
                                                    anchorWidthPx = typeWidthPx,
                                                    anchorHeightPx = typeHeightPx,
                                                    onDismissRequest = { activeDropdown = null }
                                                ) {
                                                    ConsultationType.entries.forEachIndexed { index, type ->
                                                        if (index > 0) {
                                                            HorizontalDivider(color = Color(0x1CFFFFFF))
                                                        }
                                                        val modeSubtitle = when (type) {
                                                            ConsultationType.AI_CHAT -> "Guided AI symptom & care chat"
                                                            ConsultationType.AI_VIRTUAL -> "Interactive AI virtual visit"
                                                            ConsultationType.IN_PERSON -> "Visit hospital clinic in person"
                                                            ConsultationType.VIDEO -> "Live HD video with specialist"
                                                        }
                                                        GlassyDropdownOptionRow(
                                                            title = type.label,
                                                            subtitle = modeSubtitle,
                                                            selected = selectedConsultationType == type,
                                                            onClick = {
                                                                selectedConsultationType = type
                                                                activeDropdown = null
                                                            },
                                                            testTag = "booking_type_option_${type.name.lowercase()}"
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Reason for visit *
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 52.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color(0x1CFFFFFF))
                                                    .border(1.dp, Color(0x3BFFFFFF), RoundedCornerShape(16.dp))
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.EditNote,
                                                    contentDescription = null,
                                                    tint = IceBlueText,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (reasonForVisit.isEmpty()) {
                                                        Text(
                                                            text = "Reason for visit *",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = SilverText.copy(alpha = 0.78f)
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = reasonForVisit,
                                                        onValueChange = { if (it.length <= 300) reasonForVisit = it },
                                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                            color = PlatinumWhite
                                                        ),
                                                        cursorBrush = SolidColor(IceBlueText),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("booking_reason_input")
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${reasonForVisit.length}/300",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SilverText,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }

                                        // Notes
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 52.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color(0x1CFFFFFF))
                                                    .border(1.dp, Color(0x3BFFFFFF), RoundedCornerShape(16.dp))
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Notes,
                                                    contentDescription = null,
                                                    tint = SoftLavenderText,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (notes.isEmpty()) {
                                                        Text(
                                                            text = "Notes",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = SilverText.copy(alpha = 0.78f)
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = notes,
                                                        onValueChange = { if (it.length <= 500) notes = it },
                                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                            color = PlatinumWhite
                                                        ),
                                                        cursorBrush = SolidColor(SoftLavenderText),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("booking_notes_input")
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${notes.length}/500",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SilverText,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }

                                        if (validationMessage != null) {
                                            Text(
                                                text = validationMessage!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GlassAlertRed
                                            )
                                        }

                                        // Metallic glass CTA button
                                        VerityGlassPillButton(
                                            text = "Review and continue to payment",
                                            onClick = {
                                                val chosenDoc = selectedDoctor
                                                val chosenDate = selectedDateLabel
                                                val chosenTime = selectedTimeLabel
                                                val trimmedReason = reasonForVisit.trim()
                                                when {
                                                    chosenDoc == null ->
                                                        validationMessage = "Please select a doctor from your care team."
                                                    chosenDate == null -> {
                                                        isDateStripExpanded = true
                                                        validationMessage = "Please select a visit date."
                                                    }
                                                    chosenTime == null || chosenTime !in availableTimeSlots ->
                                                        validationMessage = "Please select a visit time slot."
                                                    trimmedReason.isEmpty() ->
                                                        validationMessage = "Please enter a brief reason for your visit."
                                                    else -> {
                                                        validationMessage = null
                                                        showReviewPaymentDialog = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("booking_review_payment_button")
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showReviewPaymentDialog && selectedDoctor != null && selectedDateLabel != null) {
        val doc = selectedDoctor!!
        val date = selectedDateLabel!!
        val time = selectedTimeLabel ?: timeSlots.first()
        Dialog(onDismissRequest = { showReviewPaymentDialog = false }) {
            GlassyBookingConfirmDialog(
                doctor = doc,
                dateLabel = date,
                timeLabel = time,
                consultationTypeLabel = selectedConsultationType.label,
                reason = reasonForVisit.trim(),
                onDismiss = { showReviewPaymentDialog = false },
                onConfirmPay = {
                    showReviewPaymentDialog = false
                    val dateIso = dateOptions.firstOrNull { it.fullLabel == date }?.isoDate
                        ?: throw IllegalStateException("The selected appointment date is no longer available.")
                    onConfirmBooking(
                        doc.name,
                        doc.department,
                        doc.hospital,
                        dateIso,
                        time,
                        selectedConsultationType.label,
                        reasonForVisit.trim(),
                        notes.trim(),
                        doc.avatarRes
                    )
                }
            )
        }
    }
}
