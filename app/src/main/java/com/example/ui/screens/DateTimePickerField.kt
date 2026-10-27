package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun DateTimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val millis = try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        System.currentTimeMillis()
    }
    val display = if (value.isBlank()) {
        "Choose date and time"
    } else {
        SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault())
            .format(Date(millis))
    }

    OutlinedButton(
        onClick = {
            val initial = Calendar.getInstance().apply { timeInMillis = millis }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val chosen = Calendar.getInstance().apply {
                        timeInMillis = millis
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                    }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            chosen.set(Calendar.HOUR_OF_DAY, hour)
                            chosen.set(Calendar.MINUTE, minute)
                            chosen.set(Calendar.SECOND, 0)
                            chosen.set(Calendar.MILLISECOND, 0)
                            onValueChange(Instant.ofEpochMilli(chosen.timeInMillis).toString())
                        },
                        initial.get(Calendar.HOUR_OF_DAY),
                        initial.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(context)
                    ).show()
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("$label: $display")
    }
}
