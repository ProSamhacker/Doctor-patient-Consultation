package com.example.hospitalmanagement.ADAPTER

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppointmentAdapter(
    private var appointments: List<Appointment>,
    private val userRole: String,
    private val onCallClick: (Appointment) -> Unit,
    private val onPrescribeClick: (Appointment) -> Unit,
    private val onViewClick: (Appointment) -> Unit,
    private val onProfileClick: (String, String) -> Unit // New callback: (userId, userType)
) : RecyclerView.Adapter<AppointmentAdapter.ViewHolder>() {

    // Store names locally: Map<ID, Name>
    private var namesMap: Map<String, String> = emptyMap()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPatientName: TextView = view.findViewById(R.id.tvPatientName)
        val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvComplaint: TextView = view.findViewById(R.id.tvComplaint)
        val btnCall: Button = view.findViewById(R.id.btnCall)
        val btnPrescribe: Button = view.findViewById(R.id.btnPrescribe)
        val btnView: Button = view.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appointment = appointments[position]

        // Determine target ID and Type
        val targetId = if (userRole == "DOCTOR") appointment.patientId else appointment.doctorId
        val targetRole = if (userRole == "DOCTOR") "PATIENT" else "DOCTOR"

        // Use cached name or fallback to ID
        val displayName = namesMap[targetId] ?: if (userRole == "DOCTOR") {
            "Patient ID: ${appointment.patientId}"
        } else {
            "Doctor ID: ${appointment.doctorId}"
        }

        holder.tvPatientName.text = displayName

        // PROFILE CLICK
        holder.tvPatientName.setOnClickListener {
            onProfileClick(targetId, targetRole)
        }

        holder.tvDateTime.text = formatDateTime(appointment.dateTime)
        holder.tvStatus.text = appointment.status.name
        holder.tvComplaint.text = appointment.chiefComplaint

        // Set status color
        holder.tvStatus.setTextColor(when (appointment.status) {
            AppointmentStatus.SCHEDULED -> 0xFF4CAF50.toInt()
            AppointmentStatus.IN_PROGRESS -> 0xFF2196F3.toInt()
            AppointmentStatus.COMPLETED -> 0xFF9E9E9E.toInt()
            AppointmentStatus.CANCELLED -> 0xFFF44336.toInt()
            AppointmentStatus.NO_SHOW -> 0xFFFF9800.toInt()
            else -> 0xFF000000.toInt()
        })

        if (userRole == "DOCTOR") {
            holder.btnCall.visibility = View.VISIBLE
            holder.btnPrescribe.visibility = View.VISIBLE
            holder.btnCall.setOnClickListener { onCallClick(appointment) }
            holder.btnPrescribe.setOnClickListener { onPrescribeClick(appointment) }
        } else {
            holder.btnCall.visibility = View.GONE
            holder.btnPrescribe.visibility = View.GONE
        }

        holder.btnView.setOnClickListener { onViewClick(appointment) }
    }

    override fun getItemCount() = appointments.size

    fun updateData(newAppointments: List<Appointment>) {
        appointments = newAppointments
        notifyDataSetChanged()
    }

    // New method to update names map
    fun updateNames(newNames: Map<String, String>) {
        namesMap = newNames
        notifyDataSetChanged()
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
