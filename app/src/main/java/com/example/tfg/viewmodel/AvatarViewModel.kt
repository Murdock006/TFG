package com.example.tfg.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tfg.data.firebase.AvatarRepositorioFirebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class AvatarViewModel(application: Application) : AndroidViewModel(application) {

    private val avatarRepo = AvatarRepositorioFirebase(application.applicationContext)
    private val TAG = "AvatarViewModel"

    // Estado de carga del avatar
    private val _avatarState = MutableStateFlow<Result<String>?>(null)
    val avatarState: StateFlow<Result<String>?> = _avatarState.asStateFlow()

    // URL del avatar actual (se carga al iniciar)
    private val _avatarUrlActual = MutableStateFlow<String?>(null)
    val avatarUrlActual: StateFlow<String?> = _avatarUrlActual.asStateFlow()

    // Estado de carga
    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    /**
     * Sube un avatar seleccionado por el usuario
     */
    fun subirAvatar(imageUri: Uri) {
        viewModelScope.launch {
            _cargando.value = true
            try {
                val res = avatarRepo.subirAvatar(imageUri)
                _avatarState.value = res
                
                if (res.isSuccess) {
                    _avatarUrlActual.value = res.getOrNull()
                    Log.d(TAG, "Avatar subido exitosamente")
                } else {
                    Log.e(TAG, "Error subiendo avatar: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception en subirAvatar", e)
                _avatarState.value = Result.failure(e)
            } finally {
                _cargando.value = false
            }
        }
    }

    /**
     * Carga la URL actual del avatar del usuario
     */
    fun cargarAvatarActual() {
        viewModelScope.launch {
            try {
                val url = avatarRepo.obtenerAvatarUrlActual()
                _avatarUrlActual.value = url
                Log.d(TAG, "Avatar actual cargado: ${url ?: "sin avatar"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando avatar actual", e)
            }
        }
    }

    /**
     * Elimina el avatar del usuario
     */
    fun eliminarAvatar() {
        viewModelScope.launch {
            _cargando.value = true
            try {
                val res = avatarRepo.eliminarAvatar()
                if (res.isSuccess) {
                    _avatarUrlActual.value = null
                    _avatarState.value = Result.success("")
                    Log.d(TAG, "Avatar eliminado")
                } else {
                    Log.e(TAG, "Error eliminando avatar: ${res.exceptionOrNull()?.message}")
                    _avatarState.value = Result.failure(res.exceptionOrNull() ?: Exception("Error desconocido"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception en eliminarAvatar", e)
                _avatarState.value = Result.failure(e)
            } finally {
                _cargando.value = false
            }
        }
    }

    /**
     * Resetea el estado de subida para limpiar errores
     */
    fun resetAvatarState() {
        _avatarState.value = null
    }
}
