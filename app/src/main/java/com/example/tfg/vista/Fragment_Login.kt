package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.tfg.databinding.FragmentLoginBinding
import com.example.tfg.viewmodel.AuthViewModel

class FragmentLogin : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.botonTransparente.setOnClickListener {
            findNavController().navigate(com.example.tfg.R.id.action_fragment_Login_to_fragment_Registro)
        }

        binding.iniciarSesion.setOnClickListener {
            val email = binding.usuario.text.toString().trim()
            val pass = binding.textocontraseA.text.toString().trim()
            authViewModel.login(email, pass)
        }

        authViewModel.usuario.observe(viewLifecycleOwner) { usuario ->
            if (usuario != null) {
                findNavController().navigate(com.example.tfg.R.id.action_fragment_Login_to_fragment_PgPrincipal)
            }
        }

        authViewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrEmpty()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
