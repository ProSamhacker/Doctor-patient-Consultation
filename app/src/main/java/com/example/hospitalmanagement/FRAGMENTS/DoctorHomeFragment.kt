package com.example.hospitalmanagement.FRAGMENTS

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DoctorHomeFragment : Fragment() {
    private var tvDoctorName: TextView? = null
    private var tvScheduleCount: TextView? = null
    private var tvScheduleDate: TextView? = null
    private var tvScheduleTime: TextView? = null
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_doctor_home, container, false)
        tvDoctorName = view.findViewById(R.id.tvDoctorName)
        tvScheduleCount = view.findViewById(R.id.tvScheduleCount)
        tvScheduleDate = view.findViewById(R.id.tvScheduleDate)
        tvScheduleTime = view.findViewById(R.id.tvScheduleTime)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        observeData()
        return view
    }

    private fun observeData() {
        viewModel.currentDoctor.observe(viewLifecycleOwner) { doctor ->
            doctor?.let { tvDoctorName?.text = it.name }
        }

        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            val now = System.currentTimeMillis()
            val todayAppointments = appointments.filter {
                it.status == AppointmentStatus.SCHEDULED &&
                        it.dateTime > now &&
                        DateUtils.isToday(it.dateTime)
            }.sortedBy { it.dateTime }

            if (todayAppointments.isNotEmpty()) {
                val nextAppt = todayAppointments[0]
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

                tvScheduleCount?.text = "${todayAppointments.size} Pending Today"
                tvScheduleTime?.text = "Today at ${timeFormat.format(Date(nextAppt.dateTime))}"
                tvScheduleDate?.text = "Loading..."

                lifecycleScope.launch {
                    val patient = viewModel.repository.getPatient(nextAppt.patientId)
                    tvScheduleDate?.text = patient?.name ?: "Unknown Patient"
                }
            } else {
                tvScheduleCount?.text = "All Clear"
                tvScheduleDate?.text = "No upcoming appointments"
                tvScheduleTime?.text = ""
            }
        }
    }
}