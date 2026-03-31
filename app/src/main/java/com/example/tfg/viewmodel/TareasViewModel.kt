package com.example.tfg.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tfg.modelo.Tarea
import com.example.tfg.repositorio.RepositorioTareas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TareasViewModel(private val repo: RepositorioTareas = RepositorioTareas()) : ViewModel() {

    private val _tareaCreada = MutableStateFlow<Result<String>?>(null)
    val tareaCreada: StateFlow<Result<String>?> = _tareaCreada

    private val _marcarCompletadaState = MutableStateFlow<Result<Unit>?>(null)
    val marcarCompletadaState: StateFlow<Result<Unit>?> = _marcarCompletadaState

    private val _confirmarTareaState = MutableStateFlow<Result<Unit>?>(null)
    val confirmarTareaState: StateFlow<Result<Unit>?> = _confirmarTareaState

    fun crearTarea(tarea: Tarea) {
        viewModelScope.launch {
            val res = repo.crearTarea(tarea)
            _tareaCreada.value = res
        }
    }

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

    fun resetTareaCreada() {
        _tareaCreada.value = null
    }
}
