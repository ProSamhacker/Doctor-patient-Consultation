package com.example.hospitalmanagement.FRAGMENTS

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.PatientDashboardActivity
import com.example.hospitalmanagement.R

class FeaturesFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private var userRole: String = "PATIENT"

    companion object {
        fun newInstance(userRole: String): FeaturesFragment {
            val fragment = FeaturesFragment()
            val args = Bundle()
            args.putString("USER_ROLE", userRole)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { userRole = it.getString("USER_ROLE", "PATIENT") }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_features, container, false)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        setupFeatureCards(view)
        return view
    }

    private fun setupFeatureCards(view: View) {
        val cardHandsFreeScribe = view.findViewById<View>(R.id.cardHandsFreeScribe)
            ?: view.findViewWithTag<CardView>("handsFreeScribeCard")
        val cardLaymanTranslator = view.findViewById<View>(R.id.cardLaymanTranslator)
            ?: view.findViewWithTag<CardView>("laymanTranslatorCard")

        // If IDs don't exist in XML, find cards by position
        val allCards = (view as? ViewGroup)?.let { findAllCardViews(it) } ?: emptyList()
        val scribeCard = cardHandsFreeScribe ?: allCards.getOrNull(0)
        val translatorCard = cardLaymanTranslator ?: allCards.getOrNull(1)

        scribeCard?.setOnClickListener {
            if (userRole == "DOCTOR") {
                openLiveScribe()
            } else {
                Toast.makeText(context, "This feature is only available for doctors", Toast.LENGTH_SHORT).show()
            }
        }

        translatorCard?.setOnClickListener { openLaymanTranslator() }
    }

    private fun findAllCardViews(viewGroup: ViewGroup): List<View> {
        val cards = mutableListOf<View>()
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is CardView) {
                cards.add(child)
            } else if (child is ViewGroup) {
                cards.addAll(findAllCardViews(child))
            }
        }
        return cards
    }

    private fun openLiveScribe() {
        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            val activeAppointment = appointments.firstOrNull {
                it.status == com.example.hospitalmanagement.AppointmentStatus.IN_PROGRESS
            }

            if (activeAppointment != null) {
                val liveScribeFragment = LiveScribeFragment.newInstance(activeAppointment.appId)
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, liveScribeFragment)
                    .addToBackStack("LiveScribe")
                    .commit()
            } else {
                Toast.makeText(context, "No active consultation found. Start a consultation first.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openLaymanTranslator() {
        // Updated logic: Delegate directly to PatientDashboardActivity
        val activity = requireActivity()
        if (activity is PatientDashboardActivity) {
            activity.showAiAssistantDialog()
        } else {
            Toast.makeText(context, "Feature not available in this view", Toast.LENGTH_SHORT).show()
        }
    }
}