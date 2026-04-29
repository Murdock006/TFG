package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ArrayAdapter
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

        val sexoOpciones = listOf(
            getString(com.example.tfg.R.string.sexo_opcion_masculino),
            getString(com.example.tfg.R.string.sexo_opcion_femenino),
            getString(com.example.tfg.R.string.sexo_opcion_otro),
            getString(com.example.tfg.R.string.sexo_opcion_no_decir)
        )
        val sexoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sexoOpciones)
        binding.spSexo.setAdapter(sexoAdapter)
        binding.spSexo.setText(sexoOpciones.last(), false)

        binding.btnRegistrar.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val fechaNacimiento = binding.etFechaNacimiento.text.toString().trim()
            val sexo = binding.spSexo.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
            val ciudad = binding.etCiudad.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (nombre.isEmpty() || fechaNacimiento.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Completá los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vistaModeloAuth.registrar(nombre, fechaNacimiento, sexo, ciudad, email, pass)
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
