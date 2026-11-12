package com.quietdlp.app.data

/** Los permisos y accesos especiales que QuietDLP usa de verdad, y que el usuario puede dar o quitar. */
enum class AppPermission { NOTIFICATIONS, STORAGE, BATTERY, INSTALL }

enum class PermState { GRANTED, DENIED, LIMITED }

object PermissionList {
    /**
     * Cuáles hacen falta en esta versión de Android, en el orden en que se piden:
     * avisos (Android 13+), almacenamiento (solo Android 9 o anterior), batería y instalar apps (Android 8+).
     */
    fun applicable(sdk: Int): List<AppPermission> = buildList {
        if (sdk >= 33) add(AppPermission.NOTIFICATIONS)
        if (sdk <= 28) add(AppPermission.STORAGE)
        if (sdk >= 23) add(AppPermission.BATTERY)
        if (sdk >= 26) add(AppPermission.INSTALL)
    }

    /** Si el permiso de avisos está dado pero el usuario bloqueó las notificaciones de la app, queda limitado. */
    fun notificationsState(permissionGranted: Boolean, notificationsEnabled: Boolean): PermState = when {
        !permissionGranted -> PermState.DENIED
        notificationsEnabled -> PermState.GRANTED
        else -> PermState.LIMITED
    }
}
