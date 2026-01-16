package com.example.tfg.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// ViewModel para el fragmento de registro: expone campos de registro y validación mínima
class RegistroViewModel : ViewModel() {

    private val _nombre = MutableLiveData<String>()
    val nombre: LiveData<String> = _nombre

    private val _edad = MutableLiveData<String>()
    val edad: LiveData<String> = _edad

    private val _ciudad = MutableLiveData<String>()
    val ciudad: LiveData<String> = _ciudad

    private val _email = MutableLiveData<String>()
    val email: LiveData<String> = _email

    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _password

    private val _registroValido = MutableLiveData<Boolean>()
    val registroValido: LiveData<Boolean> = _registroValido

    fun actualizarNombre(v: String) { _nombre.value = v }
    fun actualizarEdad(v: String) { _edad.value = v }
    fun actualizarCiudad(v: String) { _ciudad.value = v }
    fun actualizarEmail(v: String) { _email.value = v }
    fun actualizarPassword(v: String) { _password.value = v }

    fun validarRegistro() {
        val valido = !_nombre.value.isNullOrBlank() &&
                !_edad.value.isNullOrBlank() &&
                !_ciudad.value.isNullOrBlank() &&
                !_email.value.isNullOrBlank() &&
                !_password.value.isNullOrBlank()
        _registroValido.value = valido
    }
}
