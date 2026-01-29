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
import coil.load
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
            intent.putExtra("IS_EDIT_MODE", true)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // TRIGGER REALTIME UPDATE
        if (userRole == "DOCTOR") {
            viewModel.loadDoctorById(userId)
        } else {
            viewModel.loadPatientById(userId)
        }
    }

    private fun setupProfile(view: View) {
        val ivProfile = view.findViewById<ImageView>(R.id.ivProfilePic)
        val tvName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvProfileSubtitle)

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
                    setRow(R.id.rowEmail, R.drawable.ic_email, "Email", it.email)
                    setRow(R.id.rowPhone, R.drawable.ic_phone, "Phone", it.phone)
                    setRow(R.id.rowHospital, R.drawable.ic_hospital, "Hospital", it.hospitalName)
                    setRow(R.id.rowExperience, R.drawable.ic_calendar, "Experience", "${it.experienceYears} Years")
                    setRow(R.id.rowFee, R.drawable.ic_features, "Consultation Fee", "₹${it.consultationFee}")
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
                    setRow(R.id.rowEmail, R.drawable.ic_email, "Email", it.email)
                    setRow(R.id.rowPhone, R.drawable.ic_phone, "Phone", it.phone)
                    setRow(R.id.rowAddress, R.drawable.ic_home, "Address", it.address)

                    view.findViewById<TextView>(R.id.blockAge).text = "${it.age}\nYears"
                    view.findViewById<TextView>(R.id.blockBlood).text = "${it.bloodGroup}\nBlood"
                    view.findViewById<TextView>(R.id.blockGender).text = "${it.gender}\nGender"
                }
            }
        }
    }
}