// Repositorio mínimo que carga categorias desde res/raw/categorias_sugeridas.json
package com.example.tfg.repositorio

import android.content.Context
import com.example.tfg.modelo.Categoria
import com.example.tfg.modelo.TareaSugerida
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CategoriasRepositorio(private val context: Context) {

    suspend fun cargarCategoriasDesdeRaw(): List<Categoria> = withContext(Dispatchers.IO) {
        val raw = context.resources.openRawResource(com.example.tfg.R.raw.categorias_sugeridas).bufferedReader().use { it.readText() }
        val obj = JSONObject(raw)
        val arr = obj.getJSONArray("categorias")
        val lista = mutableListOf<Categoria>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val id = c.optString("id")
            val nombre = c.optString("nombre")
            val descripcion = c.optString("descripcion")
            val color = c.optString("colorHex")
            val icono = c.optString("icono")
            val orden = c.optInt("orden", 0)
            val tareasJson = c.optJSONArray("tareas")
            val tareas = mutableListOf<TareaSugerida>()
            if (tareasJson != null) {
                for (j in 0 until tareasJson.length()) {
                    val t = tareasJson.getJSONObject(j)
                    val tarea = TareaSugerida(
                        id = t.optString("id"),
                        titulo = t.optString("titulo"),
                        descripcion = t.optString("descripcion"),
                        dificultad = t.optString("dificultad"),
                        puntos = t.optInt("puntos", 0),
                        duracionMinutos = if (t.has("duracionMinutos")) t.optInt("duracionMinutos") else null
                    )
                    tareas.add(tarea)
                }
            }
            lista.add(Categoria(id = id, nombre = nombre, descripcion = descripcion, colorHex = color, icono = icono, orden = orden, tareas = tareas))
        }
        lista
    }
}
