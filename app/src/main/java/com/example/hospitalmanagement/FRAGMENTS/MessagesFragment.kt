
    package com.example.hospitalmanagement.FRAGMENTS

    import android.content.Intent
            import android.os.Bundle
            import android.view.LayoutInflater
            import android.view.View
            import android.view.ViewGroup
            import android.widget.LinearLayout
            import android.widget.ProgressBar
            import android.widget.TextView
            import androidx.fragment.app.Fragment
            import androidx.lifecycle.ViewModelProvider
            import androidx.lifecycle.lifecycleScope
            import androidx.recyclerview.widget.RecyclerView
            import com.example.hospitalmanagement.ADAPTER.ConversationAdapter
            import com.example.hospitalmanagement.ADAPTER.ConversationItem
            import com.example.hospitalmanagement.Appointment // --- ADDED IMPORT ---
            import com.example.hospitalmanagement.AppointmentStatus
            import com.example.hospitalmanagement.ChatActivity
            import com.example.hospitalmanagement.MainViewModel
            import com.example.hospitalmanagement.R
            import kotlinx.coroutines.flow.first
            import kotlinx.coroutines.launch

    class MessagesFragment : Fragment() {
        private lateinit var viewModel: MainViewModel
        private lateinit var conversationAdapter: ConversationAdapter

        private var userId: String = ""
        private var userRole: String = "PATIENT"

        private lateinit var rvConversations: RecyclerView
        private lateinit var layoutEmptyState: LinearLayout
        private lateinit var progressBar: ProgressBar
        private lateinit var tvTotalUnread: TextView

        companion object {
            fun newInstance(userId: String, userRole: String): MessagesFragment {
                val fragment = MessagesFragment()
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

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.fragment_messages, container, false)

            viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

            setupUI(view)
            loadConversations()

            return view
        }

        private fun setupUI(view: View) {
            rvConversations = view.findViewById(R.id.rvConversations)
            layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
            progressBar = view.findViewById(R.id.progressBar)
            tvTotalUnread = view.findViewById(R.id.tvTotalUnread)

            // Setup adapter
            conversationAdapter =
                ConversationAdapter(emptyList()) { conversation -> openChat(conversation) }
            rvConversations.adapter = conversationAdapter
        }

        private fun loadConversations() {
            progressBar.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE

            // Using observe on LiveData
            viewModel.allAppointments.observe(viewLifecycleOwner) { appointments ->
                if (appointments.isEmpty()) {
                    progressBar.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                    rvConversations.visibility = View.GONE
                    return@observe
                }

                // Launch a coroutine to fetch details (Doctor/Patient names) and Messages
                lifecycleScope.launch {
                    val conversations = mutableListOf<ConversationItem>()
                    var totalUnread = 0

                    // Group appointments by unique user pair (deduplicate)
                    val groupedByUser = mutableMapOf<String, Appointment>()

                    // Sort by most recent first to keep the latest appointment per user
                    for (appt in appointments.sortedByDescending { it.createdAt }) {
                        // Only show appointments that are scheduled or completed
                        if (appt.status == AppointmentStatus.CANCELLED) continue

                        // Determine the other user's ID
                        val otherUserId = if (userRole == "DOCTOR") appt.patientId else appt.doctorId

                        // Keep only the most recent appointment per unique user
                        if (!groupedByUser.containsKey(otherUserId)) {
                            groupedByUser[otherUserId] = appt
                        }
                    }

                    // Now process only unique user conversations
                    for ((_, appt) in groupedByUser) {
                        // 1. Fetch Missing Details manually (because we only have IDs)
                        val patient = viewModel.repository.getPatient(appt.patientId)
                        val doctor = viewModel.repository.getDoctor(appt.doctorId)

                        // Skip if data is inconsistent/missing
                        if (patient == null || doctor == null) continue

                        // 2. Fetch Messages
                        val messages = viewModel.repository.getAppointmentMessages(appt.appId).first()
                        val lastMessage = messages.lastOrNull()

                        // 3. Get Unread Count
                        val unreadCount =
                            viewModel.repository.getUnreadMessageCount(appt.appId, userId).first()
                        totalUnread += unreadCount

                        // 4. Determine Other Party Name/Role
                        val (otherPartyName, otherPartyRole) =
                            if (userRole == "DOCTOR") {
                                Pair(patient.name, "Patient")
                            } else {
                                Pair(doctor.name, doctor.specialization)
                            }

                        conversations.add(
                            ConversationItem(
                                appointment = appt, // Now correctly passing Appointment
                                lastMessage = lastMessage?.content ?: "No messages yet",
                                lastMessageTime = lastMessage?.timestamp ?: appt.createdAt,
                                unreadCount = unreadCount,
                                otherPartyName = otherPartyName,
                                otherPartyRole = otherPartyRole
                            )
                        )
                    }

                    // Sort by last message time
                    conversations.sortByDescending { it.lastMessageTime }

                    // Update UI
                    progressBar.visibility = View.GONE
                    if (conversations.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                        rvConversations.visibility = View.GONE
                    } else {
                        layoutEmptyState.visibility = View.GONE
                        rvConversations.visibility = View.VISIBLE
                        conversationAdapter.updateData(conversations)
                    }

                    // Update total unread badge
                    if (totalUnread > 0) {
                        tvTotalUnread.visibility = View.VISIBLE
                        tvTotalUnread.text = if (totalUnread > 99) "99+" else totalUnread.toString()
                    } else {
                        tvTotalUnread.visibility = View.GONE
                    }
                }
            }
        }

        private fun openChat(conversation: ConversationItem) {
            val intent =
                Intent(requireContext(), ChatActivity::class.java).apply {
                    // Accessing properties directly from the Appointment object
                    putExtra("APP_ID", conversation.appointment.appId)
                    putExtra("USER_ID", userId)
                    putExtra("USER_ROLE", userRole)
                    putExtra("OTHER_PARTY_NAME", conversation.otherPartyName)
                    putExtra(
                        "OTHER_PARTY_PHONE",
                        if (userRole == "DOCTOR") {
                            conversation.appointment.patientId
                        } else {
                            conversation.appointment.doctorId
                        }
                    )
                }
            startActivity(intent)
        }

        override fun onResume() {
            super.onResume()
            // Trigger a refresh (re-observe happens automatically, but we might want to ensure updates)
            // loadConversations() is called in onCreateView, and LiveData updates will handle the rest.
        }
    }
