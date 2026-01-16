package com.example.tfg.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// ViewModel para el fragmento de login: expone validación mínima
class LoginViewModel : ViewModel() {

    private val _usuario = MutableLiveData<String>()
    val usuario: LiveData<String> = _usuario

    private val _contrasena = MutableLiveData<String>()
    val contrasena: LiveData<String> = _contrasena

    private val _loginValido = MutableLiveData<Boolean>()
    val loginValido: LiveData<Boolean> = _loginValido

    fun actualizarUsuario(valor: String) {
        _usuario.value = valor
    }

    fun actualizarContrasena(valor: String) {
        _contrasena.value = valor
    }

    fun validarLogin() {
        val u = _usuario.value.orEmpty().trim()
        val p = _contrasena.value.orEmpty().trim()
        _loginValido.value = u.isNotEmpty() && p.isNotEmpty()
    }
}
