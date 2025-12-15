package com.example.hospitalmanagement.ADAPTER

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Medication
import com.example.hospitalmanagement.R

class MedicationAdapter(private var items: List<Medication>) :
    RecyclerView.Adapter<MedicationAdapter.MedViewHolder>() {

    inner class MedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val medId: TextView = itemView.findViewById(R.id.medId)
        val medName: TextView = itemView.findViewById(R.id.medName)
        val medSection: TextView = itemView.findViewById(R.id.medSection)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication, parent, false)
        return MedViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedViewHolder, position: Int) {
        val med = items[position]
        holder.medId.text = (position + 1).toString()   // SR No instead of DB id
        holder.medName.text = med.name
        holder.medSection.text = med.section
    }


    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Medication>) {
        items = newItems
        notifyDataSetChanged()
    }
}
