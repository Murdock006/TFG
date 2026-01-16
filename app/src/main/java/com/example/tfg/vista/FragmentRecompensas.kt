package com.example.tfg.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tfg.databinding.FragmentRecompensasBinding

class FragmentRecompensas : Fragment() {

    private lateinit var binding: FragmentRecompensasBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRecompensasBinding.inflate(inflater, container, false)
        return binding.root
    }
}
