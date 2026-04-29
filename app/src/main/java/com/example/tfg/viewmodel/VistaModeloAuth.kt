package com.example.tfg.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.example.tfg.modelo.Usuario
import com.example.tfg.repositorio.AuthRepositorio
import com.example.tfg.service.LocalizadorServicios
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.util.Log

class VistaModeloAuth(
    private val repositorio: AuthRepositorio = LocalizadorServicios.repositorioAuth
) : ViewModel() {

    private val _usuario = MutableLiveData<Usuario?>()
    val usuario: LiveData<Usuario?> = _usuario

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _eliminacionCuenta = MutableLiveData<Result<Unit>?>(null)
    val eliminacionCuenta: LiveData<Result<Unit>?> = _eliminacionCuenta

    private val TAG = "VistaModeloAuth"

    fun registrar(
        nombre: String,
        fechaNacimiento: String?,
        sexo: String?,
        pais: String?,
        ciudad: String?,
        email: String,
        password: String
    ) {
        val usuarioObj = Usuario(
            id = "",
            nombre = nombre,
            fechaNacimiento = fechaNacimiento,
            sexo = sexo,
            pais = pais,
            ciudad = ciudad,
            email = email
        )
        viewModelScope.launch {
            try {
                // intentar cerrar sesión previa para evitar problemas de estado
                try { repositorio.logout() } catch (_: Exception) { }
                val res = repositorio.registrar(usuarioObj, password)
                if (res.isSuccess) {
                    _usuario.value = res.getOrNull()
                    _error.value = null
                    // warm-up: forzar carga de lista de usuarios para que UI resuelva nombres rápidamente (no-bloqueante)
                    launch {
                        try {
                            val lista = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                            Log.d(TAG, "warmup usuarios tras registrar: ${lista.size}")
                        } catch (e: Exception) { Log.w(TAG, "warmup usuarios falló: ${e.message}") }
                    }
                } else {
                    val ex = res.exceptionOrNull()
                    _error.value = ex?.message ?: "Error al registrarse"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al registrarse"
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                // cerrar sesión previa para evitar conflictos al cambiar de cuenta
                try { repositorio.logout() } catch (_: Exception) { }
                val res = repositorio.login(email, password)
                if (res.isSuccess) {
                    _usuario.value = res.getOrNull()
                    _error.value = null
                    // warm-up: forzar carga de lista de usuarios para que UI resuelva nombres rápidamente (no-bloqueante)
                    launch {
                        try {
                            val lista = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                            Log.d(TAG, "warmup usuarios tras login: ${lista.size}")
                        } catch (e: Exception) { Log.w(TAG, "warmup usuarios falló: ${e.message}") }
                    }
                } else {
                    val ex = res.exceptionOrNull()
                    _error.value = ex?.message ?: "Error al iniciar sesión"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al iniciar sesión"
            }
        }
    }

    fun logout() {
        viewModelScope.launch { try { repositorio.logout() } catch (_: Exception) { } ; _usuario.value = null }
    }

    fun eliminarCuentaActual() {
        viewModelScope.launch {
            try {
                val res = repositorio.eliminarCuentaActual()
                if (res.isSuccess) {
                    _usuario.value = null
                    _error.value = null
                } else {
                    val msg = res.exceptionOrNull()?.message ?: "No se pudo eliminar la cuenta"
                    _error.value = msg
                }
                _eliminacionCuenta.value = res
            } catch (e: Exception) {
                val msg = e.message ?: "No se pudo eliminar la cuenta"
                _error.value = msg
                _eliminacionCuenta.value = Result.failure(Exception(msg))
            }
        }
    }

    fun resetEliminacionCuentaState() {
        _eliminacionCuenta.value = null
    }

    // Nuevo: login usando token de proveedor externo (ej. Google idToken)
    fun loginConTokenProveedor(idToken: String, proveedor: String = "google") {
        viewModelScope.launch {
            try {
                val res = repositorio.loginConTokenProveedor(idToken, proveedor)
                if (res.isSuccess) {
                    _usuario.value = res.getOrNull()
                    _error.value = null
                    // warm-up: forzar carga de lista de usuarios para que UI resuelva nombres rápidamente (no-bloqueante)
                    launch {
                        try {
                            val lista = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                            Log.d(TAG, "warmup usuarios tras login proveedor: ${lista.size}")
                        } catch (e: Exception) { Log.w(TAG, "warmup usuarios falló: ${e.message}") }
                    }
                } else {
                    val ex = res.exceptionOrNull()
                    _error.value = "Login con proveedor fallido: ${ex?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Login con proveedor fallido: ${e.message}"
            }
        }
    }
}
