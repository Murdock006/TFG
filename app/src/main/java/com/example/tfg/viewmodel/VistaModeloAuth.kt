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

    private val TAG = "VistaModeloAuth"

    fun registrar(nombre: String, edad: Int?, ciudad: String?, email: String, password: String) {
        val usuarioObj = Usuario(id = "", nombre = nombre, edad = edad, ciudad = ciudad, email = email)
        viewModelScope.launch {
            try {
                // intentar cerrar sesión previa para evitar problemas de estado
                try { repositorio.logout() } catch (_: Exception) { }
                val res = repositorio.registrar(usuarioObj, password)
                if (res.isSuccess) {
                    _usuario.value = res.getOrNull()
                    _error.value = null
                    // warm-up: forzar carga de lista de usuarios para que UI resuelva nombres rápidamente
                    try {
                        val lista = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                        Log.d(TAG, "warmup usuarios tras registrar: ${lista.size}")
                    } catch (e: Exception) { Log.w(TAG, "warmup usuarios falló: ${e.message}") }
                } else {
                    val ex = res.exceptionOrNull()
                    _error.value = "Registro fallido: ${ex?.javaClass?.simpleName}: ${ex?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Registro fallido: ${e.javaClass.simpleName}: ${e.message}"
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
                    // warm-up: forzar carga de lista de usuarios para que UI resuelva nombres rápidamente
                    try {
                        val lista = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                        Log.d(TAG, "warmup usuarios tras login: ${lista.size}")
                    } catch (e: Exception) { Log.w(TAG, "warmup usuarios falló: ${e.message}") }
                } else {
                    val ex = res.exceptionOrNull()
                    _error.value = "Login fallido: ${ex?.javaClass?.simpleName}: ${ex?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Login fallido: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    fun logout() {
        viewModelScope.launch { try { repositorio.logout() } catch (_: Exception) { } ; _usuario.value = null }
    }
}
