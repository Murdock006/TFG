package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.app.DatePickerDialog
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
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
        binding.spSexo.threshold = 0
        binding.spSexo.setText(sexoOpciones.last(), false)

        val paises = Locale.getISOCountries()
            .map { Locale("es", it).displayCountry }
            .sorted()
        val paisAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, paises)
        binding.etPais.setAdapter(paisAdapter)

        val prefs = requireContext().getSharedPreferences("tfg_prefs", android.content.Context.MODE_PRIVATE)
        val ciudades = prefs.getStringSet("registro_ciudades", emptySet())?.toList()?.sorted() ?: emptyList()
        val ciudadAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ciudades)
        binding.etCiudad.setAdapter(ciudadAdapter)

        binding.etFechaNacimiento.keyListener = null
        binding.etFechaNacimiento.setOnClickListener {
            mostrarDatePicker()
        }
        binding.etFechaNacimiento.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) mostrarDatePicker()
        }

        binding.btnRegistrar.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val fechaNacimiento = binding.etFechaNacimiento.text.toString().trim()
            val sexo = binding.spSexo.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
            val pais = binding.etPais.text.toString().trim()
            val ciudad = binding.etCiudad.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (nombre.isEmpty() || fechaNacimiento.isEmpty() || pais.isEmpty() || ciudad.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            guardarCiudadReciente(ciudad)
            vistaModeloAuth.registrar(nombre, fechaNacimiento, sexo, pais, ciudad, email, pass)
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

    private fun mostrarDatePicker() {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val fecha = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                binding.etFechaNacimiento.setText(fecha)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
    }

    private fun guardarCiudadReciente(ciudad: String) {
        val prefs = requireContext().getSharedPreferences("tfg_prefs", android.content.Context.MODE_PRIVATE)
        val actuales = prefs.getStringSet("registro_ciudades", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        actuales.add(ciudad)
        prefs.edit().putStringSet("registro_ciudades", actuales).apply()
    }
}
