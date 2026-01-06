package com.example.hospitalmanagement.FRAGMENTS

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import coil.load // Make sure you have Coil dependency
import coil.transform.CircleCropTransformation
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.auth.ProfileSetupActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupProfile(view)
        setupEditButton(view)

        return view
    }

    private fun setupEditButton(view: View) {
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fabEditProfile)
        fab.setOnClickListener {
            val intent = Intent(requireContext(), ProfileSetupActivity::class.java)
            intent.putExtra("USER_TYPE", userRole)
            intent.putExtra("IS_EDIT_MODE", true) // Signal to pre-fill data
            startActivity(intent)
        }
    }

    private fun setupProfile(view: View) {
        val ivProfile = view.findViewById<ImageView>(R.id.ivProfilePic)
        val tvName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvProfileSubtitle)

        // Helper to set row data
        fun setRow(rowId: Int, iconRes: Int, label: String, value: String?) {
            val row = view.findViewById<View>(rowId) ?: return
            row.findViewById<ImageView>(R.id.imgIcon)?.setImageResource(iconRes)
            row.findViewById<TextView>(R.id.tvLabel)?.text = label
            row.findViewById<TextView>(R.id.tvValue)?.text = value ?: "N/A"
        }

        if (userRole == "DOCTOR") {
            view.findViewById<MaterialCardView>(R.id.cardDoctorProfessional).visibility = View.VISIBLE
            view.findViewById<MaterialCardView>(R.id.cardBio).visibility = View.VISIBLE
            view.findViewById<MaterialCardView>(R.id.cardPatientMedical).visibility = View.GONE

            viewModel.currentDoctor.observe(viewLifecycleOwner) { doc ->
                doc?.let {
                    tvName.text = it.name
                    tvSubtitle.text = it.specialization

                    if (it.profileImageUrl.isNotEmpty()) {
                        ivProfile.load(it.profileImageUrl) { transformations(CircleCropTransformation()) }
                    }

                    // Contact
                    setRow(R.id.rowEmail, R.drawable.ic_email, "Email", it.email)
                    setRow(R.id.rowPhone, R.drawable.ic_phone, "Phone", it.phone)
                    view.findViewById<View>(R.id.rowAddress).visibility = View.GONE
                    view.findViewById<View>(R.id.divAddress).visibility = View.GONE

                    // Professional
                    setRow(R.id.rowHospital, R.drawable.ic_hospital, "Hospital", it.hospitalName)
                    setRow(R.id.rowExperience, R.drawable.ic_calendar, "Experience", "${it.experienceYears} Years")
                    setRow(R.id.rowLicense, R.drawable.ic_features, "License", "N/A") // Add field to entity if needed
                    setRow(R.id.rowFee, R.drawable.ic_features, "Consultation Fee", "₹${it.consultationFee}")

                    // Bio (Assume entity has it, or use default)
                    view.findViewById<TextView>(R.id.tvBio).text = "Experienced ${it.specialization} committed to patient care."
                }
            }
        } else {
            view.findViewById<MaterialCardView>(R.id.cardDoctorProfessional).visibility = View.GONE
            view.findViewById<MaterialCardView>(R.id.cardBio).visibility = View.GONE
            view.findViewById<MaterialCardView>(R.id.cardPatientMedical).visibility = View.VISIBLE

            viewModel.currentPatient.observe(viewLifecycleOwner) { pat ->
                pat?.let {
                    tvName.text = it.name
                    tvSubtitle.text = "Patient"

                    if (it.profileImageUrl.isNotEmpty()) {
                        ivProfile.load(it.profileImageUrl) { transformations(CircleCropTransformation()) }
                    }

                    // Contact
                    setRow(R.id.rowEmail, R.drawable.ic_email, "Email", it.email)
                    setRow(R.id.rowPhone, R.drawable.ic_phone, "Phone", it.phone)
                    setRow(R.id.rowAddress, R.drawable.ic_home, "Address", it.address)

                    // Stats
                    view.findViewById<TextView>(R.id.blockAge).apply {
                        text = "${it.age}\nYears"
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                    }
                    view.findViewById<TextView>(R.id.blockBlood).apply {
                        text = "${it.bloodGroup}\nBlood"
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                    }
                    view.findViewById<TextView>(R.id.blockGender).apply {
                        text = "${it.gender}\nGender"
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                    }

                    // Lists
                    view.findViewById<TextView>(R.id.tvAllergies).text =
                        if (it.allergies.isNotEmpty()) it.allergies.joinToString(", ") else "No known allergies"

                    view.findViewById<TextView>(R.id.tvConditions).text =
                        if (it.chronicConditions.isNotEmpty()) it.chronicConditions.joinToString(", ") else "No chronic conditions"
                }
            }
        }
    }
}