package com.example.hospitalmanagement.ADAPTER

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Patient
import com.example.hospitalmanagement.R

class PatientSearchAdapter(
    private var patients: List<Patient>,
    private val onPatientClick: (Patient) -> Unit
) : RecyclerView.Adapter<PatientSearchAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPatientName)
        val tvDetails: TextView = view.findViewById(R.id.tvPatientDetails)
        val tvPhone: TextView = view.findViewById(R.id.tvPatientPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_patient_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val patient = patients[position]
        holder.tvName.text = patient.name
        holder.tvDetails.text = "Age: ${patient.age} • ${patient.gender}"
        holder.tvPhone.text = patient.phone

        holder.itemView.setOnClickListener { onPatientClick(patient) }
    }

    override fun getItemCount() = patients.size

    fun updateData(newPatients: List<Patient>) {
        patients = newPatients
        notifyDataSetChanged()
    }
}