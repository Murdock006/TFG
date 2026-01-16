package com.example.tfg.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.tfg.databinding.FragmentPgPrincipalBinding
import com.example.tfg.viewmodel.VistaModeloPrincipal

class FragmentPgPrincipal : Fragment() {

    private lateinit var binding: FragmentPgPrincipalBinding
    private val vistaModelo: VistaModeloPrincipal by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPgPrincipalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vistaModelo.textoLiveData.observe(viewLifecycleOwner) { valor ->
            binding.textViewResultados.text = valor
        }

        vistaModelo.actualizarTexto()
    }
}
