package com.example.hospitalmanagement.ADAPTER
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Doctor
import com.example.hospitalmanagement.R

class DoctorSearchAdapter(
    private var doctors: List<Doctor>,
    private val onDoctorClick: (Doctor) -> Unit
) : RecyclerView.Adapter<DoctorSearchAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivDoctorProfile)
        val tvName: TextView = view.findViewById(R.id.tvDoctorName)
        val tvSpecialization: TextView = view.findViewById(R.id.tvSpecialization)
        val tvHospital: TextView = view.findViewById(R.id.tvHospital)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvFee: TextView = view.findViewById(R.id.tvFee)
        val tvAvailability: TextView = view.findViewById(R.id.tvAvailability)
        val btnBook: Button = view.findViewById(R.id.btnBookAppointment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doctor_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val doctor = doctors[position]

        holder.tvName.text = doctor.name
        holder.tvSpecialization.text = doctor.specialization
        holder.tvHospital.text = doctor.hospitalName
        holder.tvRating.text = "⭐ ${doctor.rating}"
        holder.tvFee.text = "₹${doctor.consultationFee}"
        holder.tvAvailability.text = if (doctor.isActive) "Available" else "Unavailable"
        holder.tvAvailability.setTextColor(
            if (doctor.isActive) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        )

        holder.btnBook.setOnClickListener { onDoctorClick(doctor) }
        holder.itemView.setOnClickListener { onDoctorClick(doctor) }
    }

    override fun getItemCount() = doctors.size

    fun updateData(newDoctors: List<Doctor>) {
        doctors = newDoctors
        notifyDataSetChanged()
    }
}
