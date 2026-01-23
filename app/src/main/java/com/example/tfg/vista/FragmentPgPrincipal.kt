package com.example.tfg.vista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
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

        // Conectar botones de UI a acciones de navegación
        binding.categoriaCocina.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "Cocina") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaLimpieza.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "Limpieza") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaRopa.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "Ropa") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaMascotas.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "Mascotas") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaRecados.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "Recados") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }
        binding.categoriaPersonalizado.setOnClickListener {
            val bundle = Bundle().apply { putString("categoria", "Personalizada") }
            findNavController().navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        }

        binding.verCalendario.setOnClickListener {
            findNavController().navigate(com.example.tfg.R.id.fragment_Calendario)
        }
        binding.misRecompensas.setOnClickListener {
            findNavController().navigate(com.example.tfg.R.id.fragment_Recompensas)
        }
        binding.perfilPareja.setOnClickListener {
            findNavController().navigate(com.example.tfg.R.id.fragment_Pareja)
        }
    }
}
