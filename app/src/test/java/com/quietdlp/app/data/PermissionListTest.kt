package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionListTest {
    @Test
    fun modernAndroidAsksForNotificationsBatteryAndInstall() {
        assertEquals(
            listOf(AppPermission.NOTIFICATIONS, AppPermission.BATTERY, AppPermission.INSTALL),
            PermissionList.applicable(36),
        )
    }

    @Test
    fun androidTwelveHasNoNotificationPermission() {
        assertEquals(listOf(AppPermission.BATTERY, AppPermission.INSTALL), PermissionList.applicable(32))
    }

    @Test
    fun androidNineNeedsStorage() {
        assertEquals(
            listOf(AppPermission.STORAGE, AppPermission.BATTERY, AppPermission.INSTALL),
            PermissionList.applicable(28),
        )
    }

    @Test
    fun androidSevenOnlyHasBattery() {
        assertEquals(listOf(AppPermission.STORAGE, AppPermission.BATTERY), PermissionList.applicable(24))
    }

    @Test
    fun notificationsAreLimitedWhenBlockedInSystemSettings() {
        assertEquals(PermState.DENIED, PermissionList.notificationsState(false, false))
        assertEquals(PermState.DENIED, PermissionList.notificationsState(false, true))
        assertEquals(PermState.GRANTED, PermissionList.notificationsState(true, true))
        assertEquals(PermState.LIMITED, PermissionList.notificationsState(true, false))
    }
}
