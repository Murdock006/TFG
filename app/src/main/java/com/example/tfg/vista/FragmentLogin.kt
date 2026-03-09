package com.example.tfg.vista

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.tfg.databinding.FragmentLoginBinding
import com.example.tfg.viewmodel.VistaModeloAuth
import com.example.tfg.viewmodel.ParejaViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class FragmentLogin : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private val vistaModeloAuth: VistaModeloAuth by viewModels()
    private val parejaVM: ParejaViewModel by activityViewModels()

    private var googleClient: GoogleSignInClient? = null

    private val launcherGoogle = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intent = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email
            val nombre = account?.displayName
            if (!idToken.isNullOrEmpty()) {
                // Mostrar info en UI
                binding.tvUsuarioInfo.visibility = View.VISIBLE
                binding.tvUsuarioInfo.text = "Sesión: ${nombre ?: email ?: "-"}\n${email ?: ""}"

                // pasar token al viewmodel para login con Firebase
                vistaModeloAuth.loginConTokenProveedor(idToken, "google")

                // mostrar toast con email
                Toast.makeText(requireContext(), "Sesión iniciada: ${email ?: nombre ?: "-"}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "ID token no disponible", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), "Error Google Sign-In: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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
            vistaModeloAuth.login(email, pass)
        }

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.example.tfg.R.string.google_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(requireContext(), gso)

        binding.btnGoogle.setOnClickListener {
            val intent = googleClient?.signInIntent
            if (intent != null) launcherGoogle.launch(intent)
        }

        vistaModeloAuth.usuario.observe(viewLifecycleOwner) { usuario ->
            if (usuario != null) {
                // Cargar grupo asociado al usuario para asegurar persistencia
                parejaVM.cargarGrupoPorUsuario(usuario.id)
                // Navegar al principal
                findNavController().navigate(com.example.tfg.R.id.action_fragment_Login_to_fragment_PgPrincipal)
            }
        }

        vistaModeloAuth.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrEmpty()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
