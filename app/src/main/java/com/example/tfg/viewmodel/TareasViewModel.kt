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

    fun crearTarea(tarea: Tarea) {
        viewModelScope.launch {
            val res = repo.crearTarea(tarea)
            _tareaCreada.value = res
        }
    }

    fun marcarCompletada(tareaId: String, ejecutorUid: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repo.marcarCompletada(tareaId, ejecutorUid)
            onResult(res)
        }
    }

    fun confirmarTarea(tareaId: String, confirmadoPorUid: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repo.confirmarTarea(tareaId, confirmadoPorUid)
            onResult(res)
        }
    }
}
