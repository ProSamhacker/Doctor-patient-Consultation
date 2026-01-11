package com.example.hospitalmanagement.ADAPTER

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.NotificationEntity
import com.example.hospitalmanagement.NotificationType
import com.example.hospitalmanagement.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var notifications: List<NotificationEntity>,
    private val onNotificationClick: (NotificationEntity) -> Unit,
    // New callback for actions
    private val onActionClick: (NotificationEntity, Boolean) -> Unit // Boolean: true = Accept, false = Reject
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvNotificationTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvNotificationMessage)
        val tvTime: TextView = view.findViewById(R.id.tvNotificationTime)
        val ivIcon: ImageView = view.findViewById(R.id.ivNotificationIcon)
        val vUnreadIndicator: View = view.findViewById(R.id.vUnreadIndicator)

        // Action views
        val layoutActions: LinearLayout = view.findViewById(R.id.layoutActions)
        val btnAccept: Button = view.findViewById(R.id.btnAccept)
        val btnReject: Button = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]

        holder.tvTitle.text = notification.title
        holder.tvMessage.text = notification.message
        holder.tvTime.text = formatTime(notification.timestamp)
        holder.vUnreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

        // Icon Logic
        holder.ivIcon.setImageResource(when (notification.type) {
            NotificationType.APPOINTMENT_REMINDER -> R.drawable.ic_calendar
            NotificationType.APPOINTMENT_CONFIRMED -> R.drawable.ic_check
            NotificationType.APPOINTMENT_CANCELLED -> R.drawable.ic_cancel
            NotificationType.PRESCRIPTION_READY -> R.drawable.ic_prescription
            NotificationType.MESSAGE_RECEIVED -> R.drawable.ic_chat
            NotificationType.LAB_RESULT_READY -> R.drawable.ic_lab
            NotificationType.EMERGENCY -> R.drawable.ic_emergency
            NotificationType.INFO -> R.drawable.ic_info
        })

        // --- ACTION LOGIC ---
        // Show buttons only if title is "Appointment Request" and it hasn't been handled (read)
        if (notification.title == "Appointment Request" && !notification.isRead) {
            holder.layoutActions.visibility = View.VISIBLE

            holder.btnAccept.setOnClickListener {
                onActionClick(notification, true)
            }

            holder.btnReject.setOnClickListener {
                onActionClick(notification, false)
            }
        } else {
            holder.layoutActions.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onNotificationClick(notification) }
    }

    override fun getItemCount() = notifications.size

    fun updateData(newNotifications: List<NotificationEntity>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}