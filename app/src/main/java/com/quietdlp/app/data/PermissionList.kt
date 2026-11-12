package com.quietdlp.app.data

enum class AppPermission { NOTIFICATIONS, STORAGE, BATTERY, INSTALL }

enum class PermState { GRANTED, DENIED, LIMITED }

object PermissionList {
    fun applicable(sdk: Int): List<AppPermission> = buildList {
        if (sdk >= 33) add(AppPermission.NOTIFICATIONS)
        if (sdk <= 28) add(AppPermission.STORAGE)
        if (sdk >= 23) add(AppPermission.BATTERY)
        if (sdk >= 26) add(AppPermission.INSTALL)
    }

    fun notificationsState(permissionGranted: Boolean, notificationsEnabled: Boolean): PermState = when {
        !permissionGranted -> PermState.DENIED
        notificationsEnabled -> PermState.GRANTED
        else -> PermState.LIMITED
    }
}
