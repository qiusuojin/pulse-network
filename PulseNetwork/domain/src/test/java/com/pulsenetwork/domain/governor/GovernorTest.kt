package com.pulsenetwork.domain.governor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Governor 接口测试
 */
class GovernorTest {

    @Test
    fun `GovernorStatus meetsNetworkTaskRequirements returns true when all conditions met`() {
        val status = GovernorStatus(
            isCharging = true,
            batteryLevel = 85,
            batteryTemperature = 35f,
            isScreenOn = false,
            cpuUsage = 0.3f,
            availableMemoryMB = 2048,
            totalMemoryMB = 4096,
            npuAvailable = true,
            thermalState = ThermalState.NORMAL,
            canAcceptNetworkTasks = true
        )

        assertTrue(status.meetsNetworkTaskRequirements())
    }

    @Test
    fun `GovernorStatus meetsNetworkTaskRequirements returns false when not charging`() {
        val status = GovernorStatus(
            isCharging = false,
            batteryLevel = 85,
            batteryTemperature = 35f,
            isScreenOn = false,
            cpuUsage = 0.3f,
            availableMemoryMB = 2048,
            totalMemoryMB = 4096,
            npuAvailable = true,
            thermalState = ThermalState.NORMAL,
            canAcceptNetworkTasks = false
        )

        assertFalse(status.meetsNetworkTaskRequirements())
    }

    @Test
    fun `GovernorStatus meetsNetworkTaskRequirements returns false when battery low`() {
        val status = GovernorStatus(
            isCharging = true,
            batteryLevel = 70,
            batteryTemperature = 35f,
            isScreenOn = false,
            cpuUsage = 0.3f,
            availableMemoryMB = 2048,
            totalMemoryMB = 4096,
            npuAvailable = true,
            thermalState = ThermalState.NORMAL,
            canAcceptNetworkTasks = false
        )

        assertFalse(status.meetsNetworkTaskRequirements())
    }

    @Test
    fun `GovernorStatus meetsNetworkTaskRequirements returns false when screen on`() {
        val status = GovernorStatus(
            isCharging = true,
            batteryLevel = 85,
            batteryTemperature = 35f,
            isScreenOn = true,
            cpuUsage = 0.3f,
            availableMemoryMB = 2048,
            totalMemoryMB = 4096,
            npuAvailable = true,
            thermalState = ThermalState.NORMAL,
            canAcceptNetworkTasks = false
        )

        assertFalse(status.meetsNetworkTaskRequirements())
    }

    @Test
    fun `GovernorStatus meetsNetworkTaskRequirements returns false when temperature high`() {
        val status = GovernorStatus(
            isCharging = true,
            batteryLevel = 85,
            batteryTemperature = 42f,
            isScreenOn = false,
            cpuUsage = 0.3f,
            availableMemoryMB = 2048,
            totalMemoryMB = 4096,
            npuAvailable = true,
            thermalState = ThermalState.SERIOUS,
            canAcceptNetworkTasks = false
        )

        assertFalse(status.meetsNetworkTaskRequirements())
    }

    @Test
    fun `TaskDecision indicates allowed or not`() {
        val allowed = TaskDecision(
            allowed = true,
            reason = "All conditions met",
            suggestedPriority = TaskPriority.NORMAL
        )
        assertTrue(allowed.allowed)

        val denied = TaskDecision(
            allowed = false,
            reason = "Battery too low",
            suggestedPriority = TaskPriority.LOW
        )
        assertFalse(denied.allowed)
    }
}
