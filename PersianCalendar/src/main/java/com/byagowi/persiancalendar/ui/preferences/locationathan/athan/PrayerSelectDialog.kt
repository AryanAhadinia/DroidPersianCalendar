package com.byagowi.persiancalendar.ui.preferences.locationathan.athan

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.byagowi.persiancalendar.PREF_ATHAN_ALARM
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.utils.appPrefs
import com.byagowi.persiancalendar.utils.splitIgnoreEmpty

fun Fragment.showPrayerSelectDialog(): Boolean {
    val context = context ?: return true
    val entriesKeys = resources.getStringArray(R.array.prayerTimeKeys)
    val alarms = (context.appPrefs.getString(PREF_ATHAN_ALARM, null) ?: "")
        .splitIgnoreEmpty(",").toMutableSet()
    val checked = entriesKeys.map { it in alarms }.toBooleanArray()

    AlertDialog.Builder(context)
        .setTitle(R.string.athan_alarm)
        .setMultiChoiceItems(R.array.prayerTimeNames, checked) { _, which, isChecked ->
            val key = entriesKeys[which].toString()
            if (isChecked) alarms.add(key) else alarms.remove(key)
        }
        .setPositiveButton(R.string.accept) { _, _ ->
            this.context?.appPrefs?.edit {
                putString(PREF_ATHAN_ALARM, alarms.joinToString(","))
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()

    return true
}
