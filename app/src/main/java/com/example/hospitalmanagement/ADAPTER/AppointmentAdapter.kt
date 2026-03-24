package com.example.hospitalmanagement.ADAPTER

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.Doctor
import com.example.hospitalmanagement.Patient
import com.example.hospitalmanagement.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppointmentAdapter(
        private var appointments: List<Appointment>,
        private val userRole: String,
        private val onCallClick: (Appointment) -> Unit,
        private val onPrescribeClick: (Appointment) -> Unit,
        private val onRecordVitalsClick: (Appointment) -> Unit,
        private val onProfileClick: (String, String) -> Unit,
        private val onAcceptClick: (Appointment) -> Unit,
        private val onRejectClick: (Appointment) -> Unit,
        private val onViewPrescriptionClick: (Appointment) -> Unit
) : RecyclerView.Adapter<AppointmentAdapter.ViewHolder>() {

    private var doctorMap: Map<String, Doctor> = emptyMap()
    private var patientMap: Map<String, Patient> = emptyMap()
    private var prescriptionMap: Map<Int, Boolean> = emptyMap()

    // Refreshes SCHEDULED countdowns every 60 seconds
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            notifyDataSetChanged()
            handler.postDelayed(this, 60_000L)
        }
    }

    init { handler.post(updateRunnable) }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        handler.removeCallbacks(updateRunnable)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPatientName: TextView = view.findViewById(R.id.tvPatientName)
        val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvComplaint: TextView = view.findViewById(R.id.tvComplaint)
        val btnCall: Button = view.findViewById(R.id.btnCall)
        val btnPrescribe: Button = view.findViewById(R.id.btnPrescribe)
        val btnVitals: Button = view.findViewById(R.id.btnVitals)
        val btnReject: Button = view.findViewById(R.id.btnReject)
        val btnViewProfile: Button = view.findViewById(R.id.btnViewProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appointment = appointments[position]
        val targetId = if (userRole == "DOCTOR") appointment.patientId else appointment.doctorId
        val targetRole = if (userRole == "DOCTOR") "PATIENT" else "DOCTOR"

        // ── Name ──
        val displayName =
                if (userRole == "DOCTOR") {
                    patientMap[targetId]?.name ?: "Patient ID: ${appointment.patientId}"
                } else {
                    val doc = doctorMap[targetId]
                    if (doc != null) "Dr. ${doc.name} - ${doc.specialization}"
                    else "Doctor ID: ${appointment.doctorId}"
                }
        holder.tvPatientName.text = displayName
        holder.tvPatientName.setOnClickListener { onProfileClick(targetId, targetRole) }

        // ── Info ──
        holder.tvDateTime.text = formatDateTime(appointment.dateTime)
        holder.tvComplaint.text = appointment.chiefComplaint
        holder.tvStatus.text = appointment.status.name
        holder.tvStatus.setTextColor(getStatusColor(appointment.status))

        // ── Button state machine ──
        val now = System.currentTimeMillis()
        val scheduledTime = appointment.dateTime
        val fiveMin = 5 * 60 * 1000L

        // Reset all buttons
        holder.btnCall.visibility = View.GONE
        holder.btnCall.isEnabled = true
        holder.btnReject.visibility = View.GONE
        holder.btnViewProfile.visibility = View.GONE

        when (appointment.status) {

            AppointmentStatus.PENDING -> {
                if (userRole == "DOCTOR") {
                    // Doctor sees Accept + Reject + View Profile
                    setupButton(holder.btnCall, "✓ Accept", 0xFF4CAF50.toInt()) {
                        onAcceptClick(appointment)
                    }
                    setupButton(holder.btnReject, "✕ Reject", 0xFFF44336.toInt()) {
                        onRejectClick(appointment)
                    }
                    holder.btnViewProfile.visibility = View.VISIBLE
                    holder.btnViewProfile.setOnClickListener {
                        onProfileClick(targetId, targetRole)
                    }
                } else {
                    setupButton(holder.btnCall, "⏳ Awaiting Response", 0xFFFFA000.toInt()) {}
                    holder.btnCall.isEnabled = false
                }
            }

            AppointmentStatus.SCHEDULED -> {
                // Accept/Reject buttons must be hidden regardless of role
                holder.btnReject.visibility = View.GONE

                val remaining = scheduledTime - now
                when {
                    remaining > fiveMin -> {
                        // Show live countdown
                        setupButton(
                            holder.btnCall,
                            "🕐 ${formatCountdown(remaining)}",
                            Color.GRAY
                        ) {}
                        holder.btnCall.isEnabled = false
                    }
                    remaining > 0 -> {
                        setupButton(holder.btnCall, "Prepare Room 🔔", 0xFFFFA000.toInt()) {
                            onCallClick(appointment)
                        }
                    }
                    remaining > -fiveMin -> {
                        setupButton(holder.btnCall, "JOIN NOW 🎯", 0xFF4CAF50.toInt()) {
                            onCallClick(appointment)
                        }
                    }
                    else -> {
                        setupButton(holder.btnCall, "Time Expired", 0xFFF44336.toInt()) {}
                        holder.btnCall.isEnabled = false
                    }
                }
            }

            AppointmentStatus.IN_PROGRESS -> {
                // Meeting is in progress — no rejoin
                setupButton(holder.btnCall, "In Progress 🎯", 0xFF2196F3.toInt()) {}
                holder.btnCall.isEnabled = false
            }

            AppointmentStatus.COMPLETED -> {
                holder.btnCall.visibility = View.GONE
            }

            AppointmentStatus.CANCELLED -> {
                setupButton(holder.btnCall, "Cancelled", 0xFFF44336.toInt()) {}
                holder.btnCall.isEnabled = false
                holder.btnCall.visibility = View.VISIBLE
            }

            AppointmentStatus.NO_SHOW -> {
                setupButton(holder.btnCall, "No Show", 0xFFD32F2F.toInt()) {}
                holder.btnCall.isEnabled = false
                holder.btnCall.visibility = View.VISIBLE
            }

            AppointmentStatus.EXPIRED -> {
                setupButton(holder.btnCall, "Expired", 0xFF9E9E9E.toInt()) {}
                holder.btnCall.isEnabled = false
                holder.btnCall.visibility = View.VISIBLE
            }
        }

        configureAuxButtons(holder, appointment)
    }

    private fun setupButton(btn: Button, text: String, color: Int, onClick: () -> Unit) {
        btn.visibility = View.VISIBLE
        btn.text = text
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.setOnClickListener { onClick() }
    }

    private fun configureAuxButtons(holder: ViewHolder, appointment: Appointment) {
        if (userRole == "DOCTOR") {
            holder.btnPrescribe.visibility = View.GONE
            holder.btnVitals.visibility = View.GONE
        } else {
            holder.btnPrescribe.visibility =
                    if (prescriptionMap[appointment.appId] == true) View.VISIBLE else View.GONE
            holder.btnPrescribe.text = "View Rx"
            holder.btnPrescribe.setOnClickListener { onViewPrescriptionClick(appointment) }
            holder.btnVitals.visibility = View.GONE
        }
    }

    /** Formats milliseconds remaining into a human-readable countdown. */
    private fun formatCountdown(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return when {
            hours > 0 -> "Starts in ${hours}h ${minutes}m"
            minutes > 0 -> "Starts in ${minutes}m"
            else -> "Starting soon"
        }
    }

    private fun getStatusColor(status: AppointmentStatus): Int {
        return when (status) {
            AppointmentStatus.PENDING -> 0xFFFFA000.toInt()
            AppointmentStatus.SCHEDULED -> 0xFF4CAF50.toInt()
            AppointmentStatus.IN_PROGRESS -> 0xFF2196F3.toInt()
            AppointmentStatus.COMPLETED -> 0xFF9E9E9E.toInt()
            AppointmentStatus.CANCELLED -> 0xFFF44336.toInt()
            AppointmentStatus.NO_SHOW -> 0xFFD32F2F.toInt()
            AppointmentStatus.EXPIRED -> 0xFF9E9E9E.toInt()
        }
    }

    override fun getItemCount() = appointments.size

    fun updateData(newAppointments: List<Appointment>) {
        appointments = newAppointments
        notifyDataSetChanged()
    }

    fun updateDoctorMap(map: Map<String, Doctor>) {
        doctorMap = map
        notifyDataSetChanged()
    }

    fun updatePatientMap(map: Map<String, Patient>) {
        patientMap = map
        notifyDataSetChanged()
    }

    fun updatePrescriptionMap(map: Map<Int, Boolean>) {
        prescriptionMap = map
        notifyDataSetChanged()
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
