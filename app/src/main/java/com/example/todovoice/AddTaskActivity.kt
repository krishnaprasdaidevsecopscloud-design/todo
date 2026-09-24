package com.example.todovoice

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTaskActivity : AppCompatActivity() {

    private var selectedDateMillis: Long = DateUtils.startOfDay()
    private var selectedHour: Int? = null      // null = no time, no alarm
    private var selectedMinute: Int = 0
    private var selectedTone: String? = null   // null = app default tone
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private lateinit var timeText: TextView
    private lateinit var clearTimeBtn: Button
    private lateinit var toneText: TextView

    private val tonePicker = AlarmTonePicker(this) { tone ->
        selectedTone = tone
        showTone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        val title = findViewById<EditText>(R.id.inputTitle)
        val note = findViewById<EditText>(R.id.inputNote)
        val link = findViewById<EditText>(R.id.inputLink)
        val dateText = findViewById<TextView>(R.id.textSelectedDate)
        val pickDateBtn = findViewById<Button>(R.id.btnPickDate)
        val pickTimeBtn = findViewById<Button>(R.id.btnPickTime)
        val saveBtn = findViewById<Button>(R.id.btnSave)
        timeText = findViewById(R.id.textSelectedTime)
        clearTimeBtn = findViewById(R.id.btnClearTime)
        toneText = findViewById(R.id.textSelectedTone)

        dateText.text = df.format(selectedDateMillis)
        showTime()
        showTone()

        pickDateBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedDateMillis
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    selectedDateMillis = DateUtils.startOfDay(picked.timeInMillis)
                    dateText.text = df.format(selectedDateMillis)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = DateUtils.startOfDay() - 1000 // today or later
            }.show()
        }

        pickTimeBtn.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                    showTime()
                },
                selectedHour ?: now.get(Calendar.HOUR_OF_DAY),
                if (selectedHour != null) selectedMinute else now.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(this)
            ).show()
        }

        clearTimeBtn.setOnClickListener {
            selectedHour = null
            showTime()
        }

        findViewById<Button>(R.id.btnPickTone).setOnClickListener { tonePicker.show(selectedTone) }

        saveBtn.setOnClickListener {
            val titleText = title.text.toString().trim()
            val noteText = note.text.toString().trim()
            val linkText = link.text.toString().trim()

            if (titleText.isEmpty()) {
                title.error = "Title is required"
                return@setOnClickListener
            }
            if (noteText.isEmpty()) {
                note.error = "Add a short note on why you're creating this task"
                return@setOnClickListener
            }
            val remindAt = remindAtMillis()
            if (remindAt != null && remindAt <= System.currentTimeMillis()) {
                Toast.makeText(this, "That time has already passed. Pick a later time.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                TaskRepository.getInstance(this@AddTaskActivity).addTask(
                    title = titleText,
                    note = noteText,
                    dueDate = selectedDateMillis,
                    appLink = linkText.ifEmpty { null },
                    remindAt = remindAt,
                    alarmTone = selectedTone
                )
                val msg = if (remindAt != null) "Task added. Alarm at ${DateUtils.formatTime(this@AddTaskActivity, remindAt)}"
                else "Task added"
                Toast.makeText(this@AddTaskActivity, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun remindAtMillis(): Long? =
        selectedHour?.let { DateUtils.atTime(selectedDateMillis, it, selectedMinute) }

    private fun showTime() {
        val at = remindAtMillis()
        timeText.text = if (at == null) "No time (no alarm)" else "Alarm at ${DateUtils.formatTime(this, at)}"
        clearTimeBtn.visibility = if (at == null) View.GONE else View.VISIBLE
    }

    private fun showTone() {
        val defaultName = AlarmTonePicker.displayName(this, AppPrefs.getDefaultTone(this), "System alarm sound")
        toneText.text = "Tone: " + AlarmTonePicker.displayName(this, selectedTone, "Default ($defaultName)")
    }
}
