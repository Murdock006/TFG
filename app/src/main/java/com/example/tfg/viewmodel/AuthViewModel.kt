package com.example.tfg.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.example.tfg.modelo.Usuario
import com.example.tfg.repositorio.AuthRepositorio
import com.example.tfg.service.ServiceLocator
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repositorio: AuthRepositorio = ServiceLocator.authRepositorio
) : ViewModel() {

    private val _usuario = MutableLiveData<Usuario?>()
    val usuario: LiveData<Usuario?> = _usuario

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun registrar(nombre: String, edad: Int?, ciudad: String?, email: String, password: String) {
        val usuarioObj = Usuario(id = "", nombre = nombre, edad = edad, ciudad = ciudad, email = email)
        viewModelScope.launch {
            val res = repositorio.registrar(usuarioObj, password)
            if (res.isSuccess) {
                _usuario.value = res.getOrNull()
                _error.value = null
            } else {
                _error.value = res.exceptionOrNull()?.message
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            val res = repositorio.login(email, password)
            if (res.isSuccess) {
                _usuario.value = res.getOrNull()
                _error.value = null
            } else {
                _error.value = res.exceptionOrNull()?.message
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repositorio.logout(); _usuario.value = null }
    }
}
