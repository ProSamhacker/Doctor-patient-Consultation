package com.example.hospitalmanagement.FRAGMENTS

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.MedicationSchedule
import com.example.hospitalmanagement.R

class MedicationScheduleAdapter(
    private val medications: List<MedicationSchedule>,
    private val tts: TextToSpeech?
) : RecyclerView.Adapter<MedicationScheduleAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMedName: TextView = view.findViewById(R.id.tvMedName)
        val tvDosage: TextView = view.findViewById(R.id.tvDosage)
        val tvFrequency: TextView = view.findViewById(R.id.tvFrequency)
        val tvTiming: TextView = view.findViewById(R.id.tvTiming)
        val btnPlay: LinearLayout = view.findViewById(R.id.btnPlayAudio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medication = medications[position]

        holder.tvMedName.text = medication.medicationName
        holder.tvDosage.text = medication.dosage
        holder.tvFrequency.text = medication.frequency
        holder.tvTiming.text = medication.timing

        holder.btnPlay.setOnClickListener {
            val speech = "Take ${medication.medicationName}, ${medication.dosage}, ${medication.frequency}, ${medication.timing}. ${medication.instructions}"
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun getItemCount() = medications.size
}
