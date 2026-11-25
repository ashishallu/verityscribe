package com.example

import com.example.viewmodel.VerityScribeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun workspaceStartsSignedOutWithoutMockClinicalData() = runTest {
        val vm = VerityScribeViewModel()
        assertFalse(vm.uiState.value.isSignedIn)
        assertTrue(vm.uiState.value.appointments.isEmpty())
        assertTrue(vm.uiState.value.doctorHistory.isEmpty())
    }
}
