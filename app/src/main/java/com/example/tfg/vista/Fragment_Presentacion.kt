package com.example.tfg.vista

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.tfg.R
import com.example.tfg.databinding.FragmentPresentacionBinding

class FragmentPresentacion : Fragment() {

    private lateinit var binding: FragmentPresentacionBinding
    private val mensajes = listOf(
        "Cargando dependencias...",
        "Inicializando módulos...",
        "Preparando Tareas..."
    )
    private var mensajeIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPresentacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mostrarMensajes()
    }

    private fun mostrarMensajes() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (mensajeIndex < mensajes.size) {
                    binding.textoPresentacion.text = mensajes[mensajeIndex]
                    mensajeIndex++
                    handler.postDelayed(this, 2000)
                } else {
                    findNavController().navigate(R.id.action_fragment_Presentacion_to_fragment_Login)
                }
            }
        }, 0)
    }
}
