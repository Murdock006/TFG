package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.tfg.databinding.FragmentRegistroBinding
import com.example.tfg.viewmodel.VistaModeloAuth

class FragmentRegistro : Fragment() {

    private lateinit var binding: FragmentRegistroBinding
    private val vistaModeloAuth: VistaModeloAuth by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRegistroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegistrar.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val edad = binding.etEdad.text.toString().toIntOrNull()
            val ciudad = binding.etCiudad.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (nombre.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vistaModeloAuth.registrar(nombre, edad, ciudad, email, pass)
        }

        vistaModeloAuth.usuario.observe(viewLifecycleOwner) { usuario ->
            if (usuario != null) {
                Toast.makeText(requireContext(), "✅ Registro exitoso. Hemos enviado un email de verificación a ${usuario.email}. Revisa tu bandeja de entrada y verifica tu cuenta antes de iniciar sesión.", Toast.LENGTH_LONG).show()
                // Navegar de vuelta a login para que inicie sesión tras verificar
                findNavController().popBackStack()
            }
        }

        vistaModeloAuth.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrEmpty()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
