package com.example.hospitalmanagement.FRAGMENTS
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R

class ProfileFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private var userId: String = ""
    private var userRole: String = "PATIENT"

    companion object {
        fun newInstance(userId: String, userRole: String): ProfileFragment {
            val fragment = ProfileFragment()
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
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupProfile(view)

        return view
    }

    private fun setupProfile(view: View) {
        if (userRole == "DOCTOR") {
            viewModel.currentDoctor.observe(viewLifecycleOwner) { doctor ->
                doctor?.let {
                    view.findViewById<TextView>(R.id.tvProfileName)?.text = it.name
                    view.findViewById<TextView>(R.id.tvProfileSpecialization)?.text = it.specialization
                    view.findViewById<TextView>(R.id.tvProfileEmail)?.text = it.email
                    view.findViewById<TextView>(R.id.tvProfilePhone)?.text = it.phone
                }
            }
        } else {
            viewModel.currentPatient.observe(viewLifecycleOwner) { patient ->
                patient?.let {
                    view.findViewById<TextView>(R.id.tvProfileName)?.text = it.name
                    view.findViewById<TextView>(R.id.tvProfileAge)?.text = "Age: ${it.age}"
                    view.findViewById<TextView>(R.id.tvProfileEmail)?.text = it.email
                    view.findViewById<TextView>(R.id.tvProfilePhone)?.text = it.phone
                    view.findViewById<TextView>(R.id.tvProfileBloodGroup)?.text = "Blood: ${it.bloodGroup}"
                }
            }
        }
    }
}