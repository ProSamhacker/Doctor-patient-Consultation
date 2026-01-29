package com.example.hospitalmanagement

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileOverlayDialog(
    private val userId: String,
    private val userRole: String, // "DOCTOR" or "PATIENT" (The role of the PROFILE being viewed)
    private val repository: HospitalRepository
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_profile_overlay, null)

        val ivAvatar = view.findViewById<ImageView>(R.id.ivOverlayAvatar)
        val tvName = view.findViewById<TextView>(R.id.tvOverlayName)
        val tvRole = view.findViewById<TextView>(R.id.tvOverlayRole)
        val tvDetails = view.findViewById<TextView>(R.id.tvOverlayDetails)
        val btnClose = view.findViewById<Button>(R.id.btnCloseOverlay)

        tvRole.text = if(userRole == "DOCTOR") "Doctor" else "Patient"

        // Load Data asynchronously
        CoroutineScope(Dispatchers.Main).launch {
            if (userRole == "DOCTOR") {
                val doctor = withContext(Dispatchers.IO) { repository.getDoctor(userId) }
                doctor?.let {
                    tvName.text = it.name
                    tvDetails.text = "${it.specialization}\n${it.hospitalName}\n${it.email}"
                }
            } else {
                val patient = withContext(Dispatchers.IO) { repository.getPatient(userId) }
                patient?.let {
                    tvName.text = it.name
                    tvDetails.text = "Age: ${it.age}\nGender: ${it.gender}\nBlood: ${it.bloodGroup}\nContact: ${it.phone}"
                }
            }
        }

        btnClose.setOnClickListener { dismiss() }

        builder.setView(view)
        return builder.create()
    }
}
