package com.example.tfg.repositorio

import com.example.tfg.modelo.Grupo
import kotlinx.coroutines.flow.Flow

interface GrupoRepositorio {
    suspend fun crearGrupo(grupo: Grupo): Result<Grupo>
    suspend fun obtenerGrupos(): Result<List<Grupo>>
    fun observarGrupos(): Flow<List<Grupo>>
}
