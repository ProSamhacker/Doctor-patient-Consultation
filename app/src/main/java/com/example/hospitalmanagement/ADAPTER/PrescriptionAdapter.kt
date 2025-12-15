package com.example.hospitalmanagement.ADAPTER

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Prescription
import com.example.hospitalmanagement.R

class PrescriptionAdapter(
    private var items: List<Prescription>,
    private val tts: TextToSpeech?
) : RecyclerView.Adapter<PrescriptionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMedName)
        val tvInstructions: TextView = view.findViewById(R.id.tvInstructions)
        val btnPlay: LinearLayout = view.findViewById(R.id.btnPlayAudio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prescription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // FIX: Join all medication names into a single string
        val medNames = item.medications.joinToString(", ") { it.medicationName }
        val allInstructions = item.medications.joinToString(". ") { 
            "${it.medicationName}: ${it.dosage} ${it.frequency}" 
        }

        holder.tvName.text = medNames
        holder.tvInstructions.text = item.instructions.ifBlank { "See details" }

        holder.btnPlay.setOnClickListener {
            // FIX: Read out the full list of medications
            val speech = "Prescription for $medNames. Instructions: $allInstructions. Note: ${item.instructions}"
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Prescription>) {
        items = newItems
        notifyDataSetChanged()
    }
}