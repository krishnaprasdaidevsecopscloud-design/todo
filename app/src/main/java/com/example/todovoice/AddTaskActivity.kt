package com.example.todovoice

import android.app.DatePickerDialog
import android.os.Bundle
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
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        val title = findViewById<EditText>(R.id.inputTitle)
        val note = findViewById<EditText>(R.id.inputNote)
        val link = findViewById<EditText>(R.id.inputLink)
        val dateText = findViewById<TextView>(R.id.textSelectedDate)
        val pickDateBtn = findViewById<Button>(R.id.btnPickDate)
        val saveBtn = findViewById<Button>(R.id.btnSave)

        dateText.text = df.format(selectedDateMillis)

        pickDateBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedDateMillis
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    selectedDateMillis = picked.timeInMillis
                    dateText.text = df.format(selectedDateMillis)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = DateUtils.startOfDay() - 1000 // today or later
            }.show()
        }

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

            lifecycleScope.launch {
                TaskRepository.getInstance(this@AddTaskActivity).addTask(
                    title = titleText,
                    note = noteText,
                    dueDate = selectedDateMillis,
                    appLink = linkText.ifEmpty { null }
                )
                Toast.makeText(this@AddTaskActivity, "Task added", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
