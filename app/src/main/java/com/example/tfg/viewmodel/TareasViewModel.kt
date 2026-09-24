package com.example.tfg.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tfg.repositorio.TareaRepositorio
import com.example.tfg.service.LocalizadorServicios
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TareasViewModel(private val repo: TareaRepositorio = LocalizadorServicios.repositorioTarea) : ViewModel() {

    private val _marcarCompletadaState = MutableStateFlow<Result<Unit>?>(null)
    val marcarCompletadaState: StateFlow<Result<Unit>?> = _marcarCompletadaState

    private val _confirmarTareaState = MutableStateFlow<Result<Unit>?>(null)
    val confirmarTareaState: StateFlow<Result<Unit>?> = _confirmarTareaState

    fun marcarCompletada(tareaId: String, ejecutorUid: String) {
        viewModelScope.launch {
            val res = repo.marcarCompletada(tareaId, ejecutorUid)
            _marcarCompletadaState.value = res
        }
    }

    fun confirmarTarea(tareaId: String, confirmadoPorUid: String) {
        viewModelScope.launch {
            val res = repo.confirmarTarea(tareaId, confirmadoPorUid)
            _confirmarTareaState.value = res
        }
    }

    // Funciones para resetear estados después de consumirlos
    fun resetMarcarCompletadaState() {
        _marcarCompletadaState.value = null
    }

    fun resetConfirmarTareaState() {
        _confirmarTareaState.value = null
    }
}
