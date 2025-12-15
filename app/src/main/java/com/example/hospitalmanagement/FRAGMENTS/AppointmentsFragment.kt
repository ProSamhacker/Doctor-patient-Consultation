package com.example.hospitalmanagement.FRAGMENTS
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.AppointmentAdapter
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R

class AppointmentsFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var rvAppointments: RecyclerView
    private lateinit var adapter: AppointmentAdapter
    private var userRole: String = "PATIENT"
    private var userId: String = ""

    companion object {
        fun newInstance(userId: String, userRole: String): AppointmentsFragment {
            val fragment = AppointmentsFragment()
            val args = Bundle()
            args.putString("USER_ID", userId)
            args.putString("USER_ROLE", userRole)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("USER_ID", "")
            userRole = it.getString("USER_ROLE", "PATIENT")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_appointments, container, false)

        rvAppointments = view.findViewById(R.id.rvAppointments)
        rvAppointments.layoutManager = LinearLayoutManager(requireContext())

        adapter = AppointmentAdapter(
            appointments = emptyList(),
            userRole = userRole,
            onCallClick = { appointment -> handleCallClick(appointment) },
            onPrescribeClick = { appointment -> handlePrescribeClick(appointment) },
            onViewClick = { appointment -> handleViewClick(appointment) }
        )
        rvAppointments.adapter = adapter

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        observeData()

        return view
    }

    private fun observeData() {
        viewModel.allAppointments.observe(viewLifecycleOwner) { appointments ->
            adapter.updateData(appointments)
        }
    }

    private fun handleCallClick(appointment: Appointment) {
        // Implement call functionality
    }

    private fun handlePrescribeClick(appointment: Appointment) {
        // Navigate to prescription screen
    }

    private fun handleViewClick(appointment: Appointment) {
        // View appointment details
    }
}
