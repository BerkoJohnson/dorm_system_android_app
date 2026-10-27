package com.example.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.ui.viewmodel.AppScreen

object SchoolShortcutManager {
    const val ACTION_OPEN_SHORTCUT = "com.example.action.OPEN_SCHOOL_SHORTCUT"

    fun update(
        context: Context,
        canRollCall: Boolean,
        canGateOperate: Boolean,
        canViewExeats: Boolean
    ) {
        val shortcuts = buildList {
            if (canRollCall) {
                add(shortcut(context, "roll_call", "Start roll call", AppScreen.ROLL_CALL,
                    android.R.drawable.ic_menu_agenda))
            }
            if (canGateOperate) {
                add(shortcut(context, "gate", "Gate lookup", AppScreen.GATE_CHECK,
                    android.R.drawable.ic_menu_view))
            }
            if (canViewExeats) {
                add(shortcut(context, "exeats", "View exeats", AppScreen.EXEATS,
                    android.R.drawable.ic_menu_my_calendar))
            }
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    fun clear(context: Context) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    private fun shortcut(
        context: Context,
        id: String,
        label: String,
        screen: AppScreen,
        icon: Int
    ) = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_SHORTCUT)
                .putExtra(MainActivity.EXTRA_NOTIFICATION_SCREEN, screen.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        .build()
}
