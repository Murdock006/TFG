package es.sintaxys.teamtask.repositorio

import es.sintaxys.teamtask.modelo.Grupo
import kotlinx.coroutines.flow.Flow

interface GrupoRepositorio {
    suspend fun crearGrupo(grupo: Grupo): Result<Grupo>
    suspend fun obtenerGrupos(): Result<List<Grupo>>
    fun observarGrupos(): Flow<List<Grupo>>
}
