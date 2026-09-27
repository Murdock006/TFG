package es.sintaxys.teamtask.util

import es.sintaxys.teamtask.modelo.Usuario

/**
 * Fuente única de verdad para mostrar la identidad de un usuario en la UI.
 *
 * Regla de producto: NUNCA exponer el correo junto al nombre. Solo se muestra el
 * nombre y, cuando el usuario no tiene nombre (típico de Google Sign-In sin
 * displayName), se usa el correo como fallback. Si tampoco hay correo, se cae al
 * id (uid) para no renderizar un texto vacío.
 *
 * Todas las listas y selectores de usuarios deben usar esta extensión en lugar de
 * componer manualmente el texto "nombre (correo)".
 */
fun Usuario.nombreVisible(): String = nombre.ifBlank { email.ifBlank { id } }
