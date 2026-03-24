package com.example.hospitalmanagement.FRAGMENTS

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.MedicationSchedule
import com.example.hospitalmanagement.R

class MedicationScheduleAdapter(
        private val medications: List<MedicationSchedule>,
        private val tts: TextToSpeech?
) : RecyclerView.Adapter<MedicationScheduleAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMedName)
        val tvDetails: TextView = view.findViewById(R.id.tvMedDetails)
        // Hidden views in this context
        // val btnTake: MaterialButton = view.findViewById(R.id.btnTake)
        // val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_medication_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medication = medications[position]

        holder.tvName.text = medication.medicationName
        holder.tvDetails.text =
                "${medication.dosage} • ${medication.frequency} • ${medication.timing}"

        holder.itemView.setOnClickListener {
            val speech =
                    "Take ${medication.medicationName}. Dosage: ${medication.dosage}. Timing: ${medication.timing}. Instructions: ${medication.instructions}"
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun getItemCount() = medications.size
}
