package com.example.todovoice

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ListMode { ACTIVE, PENDING, COMPLETED }

class TaskListFragment : Fragment(R.layout.fragment_task_list) {

    private lateinit var mode: ListMode
    private lateinit var adapter: TaskAdapter
    private lateinit var repo: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = ListMode.valueOf(requireArguments().getString(ARG_MODE)!!)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = TaskRepository.getInstance(requireContext())

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerTasks)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = TaskAdapter(
            items = emptyList(),
            onOpenLink = { task -> openLink(task) },
            onInProgress = { task -> handleInProgress(task) },
            onPending = { task -> handlePending(task) },
            onCompleted = { task -> handleCompleted(task) }
        )
        recycler.adapter = adapter

        val flow = when (mode) {
            ListMode.ACTIVE -> repo.activeTasks()
            ListMode.PENDING -> repo.pendingTasks()
            ListMode.COMPLETED -> repo.completedTasks()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            flow.collect { list -> adapter.submitList(list) }
        }
    }

    private fun openLink(task: Task) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(task.appLink))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't open link: ${task.appLink}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleInProgress(task: Task) {
        // Rule: In Progress is only allowed for the current day's task
        if (!DateUtils.isToday(task.dueDate)) {
            Toast.makeText(
                requireContext(),
                "Only today's tasks can be marked In Progress",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repo.markInProgress(task)
            VoiceAlarmReceiver.schedule(requireContext(), task.id)
            Toast.makeText(requireContext(), "In Progress — you'll get voice reminders until you update it", Toast.LENGTH_LONG).show()
        }
    }

    private fun handlePending(task: Task) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.markPending(task, picked.timeInMillis)
                    VoiceAlarmReceiver.cancel(requireContext(), task.id)
                    Toast.makeText(requireContext(), "Moved to Pending, due ${day}/${month + 1}/${year}", Toast.LENGTH_SHORT).show()
                }
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun handleCompleted(task: Task) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.markCompleted(task)
            VoiceAlarmReceiver.cancel(requireContext(), task.id)
            Toast.makeText(requireContext(), "Marked Completed", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_MODE = "arg_mode"
        fun newInstance(mode: ListMode) = TaskListFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode.name) }
        }
    }
}
