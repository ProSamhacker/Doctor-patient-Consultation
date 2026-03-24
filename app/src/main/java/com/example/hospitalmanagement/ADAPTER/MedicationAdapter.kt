package com.example.hospitalmanagement.ADAPTER

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.MedicationSchedule
import com.example.hospitalmanagement.R
import com.google.android.material.button.MaterialButton

data class MedicationTrackerItem(val schedule: MedicationSchedule, var isTaken: Boolean = false)

class MedicationAdapter(
        private var items: List<MedicationTrackerItem>,
        private val onTakeClick: (MedicationTrackerItem) -> Unit
) : RecyclerView.Adapter<MedicationAdapter.ViewHolder>() {

    fun updateData(newItems: List<MedicationTrackerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMedName)
        val tvDetails: TextView = view.findViewById(R.id.tvMedDetails)
        val btnTake: MaterialButton = view.findViewById(R.id.btnMarkTaken)
        val ivCheck: ImageView = view.findViewById(R.id.ivTakenCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_medication_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val schedule = item.schedule

        holder.tvName.text = schedule.medicationName
        holder.tvDetails.text = "${schedule.dosage} • ${schedule.timing}"

        if (item.isTaken) {
            holder.btnTake.visibility = View.GONE
            holder.ivCheck.visibility = View.VISIBLE
        } else {
            holder.btnTake.visibility = View.VISIBLE
            holder.ivCheck.visibility = View.GONE
            holder.btnTake.setOnClickListener {
                onTakeClick(item)
                item.isTaken = true // Optimistic update
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount() = items.size
}
