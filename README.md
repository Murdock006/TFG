TFG2 - App (MVVM refactor)

Resumen breve
- Proyecto refactorizado parcialmente a MVVM.
- Paquetes principales: `vista`, `viewmodel`, `modelo`, `repositorio`, `data.inmemory`, `data.firebase`, `service` (ServiceLocator).
- Repositorios in-memory implementados para Auth, Grupo y Tarea.
- `AuthRepositorioFirebase` añadido como stub para integrar Firebase en el futuro.

Cómo compilar
- Desde PowerShell en la raíz del proyecto:

```
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Preparar integración con Firebase (pasos resumidos)
1. Añadir `google-services.json` en `app/` (ya existe o se debe reemplazar por el tuyo).  
2. Añadir dependencias en `app/build.gradle.kts`:  
   - `implementation(platform("com.google.firebase:firebase-bom:latest-version"))`  
   - `implementation("com.google.firebase:firebase-auth-ktx")`  
   - `implementation("com.google.firebase:firebase-firestore-ktx")`  
   Añadir plugin `com.google.gms:google-services` en el buildscript si no está.
3. Implementar `AuthRepositorioFirebase` utilizando `FirebaseAuth` y mapear `FirebaseUser` a `modelo.Usuario`.
4. En `ServiceLocator`, cambiar `authRepositorio` para devolver `AuthRepositorioFirebase()` en lugar del in-memory (o usar flag de build/DI).

Siguientes pasos recomendados
- Pulir estilos y layouts (WindowInsets y BottomNavigation).  
- Añadir tests unitarios para repositorios in-memory.  
- Implementar confirmaciones al marcar tareas completadas y notificaciones.

Si quieres que siga: implementar bottom navigation, pulir layouts para todos los fragments, y completar la integración con Firebase Auth ahora mismo.
