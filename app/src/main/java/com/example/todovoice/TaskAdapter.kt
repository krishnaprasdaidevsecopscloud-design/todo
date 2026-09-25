package com.example.todovoice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private var items: List<Task>,
    private val onOpenLink: (Task) -> Unit,
    private val onInProgress: (Task) -> Unit,
    private val onPending: (Task) -> Unit,
    private val onCompleted: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textTitle)
        val note: TextView = view.findViewById(R.id.textNote)
        val date: TextView = view.findViewById(R.id.textDate)
        val source: TextView = view.findViewById(R.id.textSource)
        val btnInProgress: Button = view.findViewById(R.id.btnInProgress)
        val btnPending: Button = view.findViewById(R.id.btnPending)
        val btnCompleted: Button = view.findViewById(R.id.btnCompleted)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = items[position]
        holder.title.text = task.title
        holder.note.text = task.note
        val dateMillis = task.pendingUntil ?: task.dueDate
        val label = if (task.status == TaskStatus.COMPLETED && task.completedAt != null)
            "Completed: ${df.format(Date(task.completedAt))}"
        else
            "Due: ${df.format(Date(dateMillis))}" +
                (task.remindAt?.let { " · ⏰ ${DateUtils.formatTime(holder.itemView.context, it)}" } ?: "")
        holder.date.text = label
        holder.source.visibility = if (task.calendarEventId != null) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (!task.appLink.isNullOrBlank()) onOpenLink(task)
        }

        // Only show status actions that still make sense for this task
        holder.btnInProgress.visibility =
            if (task.status == TaskStatus.TODO || task.status == TaskStatus.IN_PROGRESS) View.VISIBLE else View.GONE
        holder.btnPending.visibility =
            if (task.status != TaskStatus.COMPLETED) View.VISIBLE else View.GONE
        holder.btnCompleted.visibility =
            if (task.status != TaskStatus.COMPLETED) View.VISIBLE else View.GONE

        holder.btnInProgress.setOnClickListener { onInProgress(task) }
        holder.btnPending.setOnClickListener { onPending(task) }
        holder.btnCompleted.setOnClickListener { onCompleted(task) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<Task>) {
        items = newItems
        notifyDataSetChanged()
    }
}
