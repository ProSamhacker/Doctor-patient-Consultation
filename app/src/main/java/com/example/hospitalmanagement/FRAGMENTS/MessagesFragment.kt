package com.example.hospitalmanagement.FRAGMENTS
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R

class MessagesFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private var userId: String = ""
    private var userRole: String = "PATIENT"

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

        // Implement messaging UI

        return view
    }
}