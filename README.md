# TFG2 - Aplicación de Gestión de Tareas en Grupo TeamTask 👥📋

Una aplicación Android moderna para **gestionar tareas colaborativas en grupos**, con sistema de puntos, recompensas, calendario y disputas. Construida con **MVVM**, **Kotlin Coroutines**, **StateFlow** y **Firebase Firestore**.

## 🎯 Características Principales

### 👥 Gestión de Grupos
- Crear grupos de trabajo
- Invitar miembros por código único
- Ver lista de miembros y roles
- Salir del grupo
- Editar nombre del grupo

### 📝 Sistema de Tareas
- Crear tareas personalizadas (asignables o no)
- Utilizar tareas predefinidas por categoría
- Asignar/reasignar tareas a miembros
- Marcar como completadas
- Confirmar tareas completadas
- Abrir disputas/reclamaciones con evidencias
- Ver tareas en vista lista y calendario

### 💰 Sistema de Puntos
- Puntos de actividad (por completar tareas)
- Puntos de recompensa (exclusivos para canje)
- Puntos reservados (en tareas pendientes)
- Racha de días (motivación)
- Canje de recompensas personalizadas

### 📅 Vistas
- **Dashboard**: Resumen grupo, tareas recientes, puntos
- **Tareas**: Lista detallada con filtros (pendientes, asignadas, historial)
- **Calendario**: Ver tareas por día, filtrar por estado
- **Recompensas**: Disponibles, pendientes, historial
- **Perfil**: Datos de usuario y grupo activo

### 🔐 Autenticación
- Google Sign-In integrado
- Email/Contraseña
- Persistencia de sesión
- Warmup no-bloqueante para carga rápida de usuarios

---

## 🏗️ Arquitectura

```
TFG2/
├── vista/                     # UI Layer (Fragments & Adapters)
│   ├── Fragment*.kt          # Fragments (Tareas, Pareja, Principal, etc.)
│   ├── *Adapter.kt           # RecyclerView Adapters
│   └── MainActivity.kt
│
├── viewmodel/                 # Business Logic Layer
│   ├── VistaModeloAuth.kt     # Autenticación
│   ├── TareasViewModel.kt     # Lógica de tareas
│   ├── ParejaViewModel.kt     # Lógica de grupos
│   └── VistaModeloPrincipal.kt
│
├── repositorio/               # Data Access Layer
│   ├── RepositorioAuth.kt
│   ├── RepositorioTareas.kt
│   ├── RepositorioPareja.kt
│   └── RepositorioDisputas.kt
│
├── data/                      # Data Sources
│   ├── firebase/              # Firebase implementations
│   └── inmemory/              # In-memory implementations
│
├── modelo/                    # Data Classes
│   ├── Tarea.kt
│   ├── Grupo.kt
│   ├── Usuario.kt
│   └── ...
│
└── service/                   # Utilities
    ├── LocalizadorServicios.kt   # Service Locator
    └── NotificationScheduler.kt
```

### Flujo de Datos

```
UI (Fragments) 
    ↓
ViewModel (StateFlow)
    ↓
Repository (Firebase/In-memory)
    ↓
Data Sources (Firestore / Local)
    ↓
Model Classes
```

---

## 🛠️ Stack Tecnológico

### Core
- **Kotlin** — Lenguaje principal
- **MVVM** — Patrón de arquitectura
- **Coroutines** — Operaciones asíncronas
- **StateFlow** — Manejo de estado reactivo

### UI
- **Jetpack Fragments** — Navigation
- **RecyclerView** — Listas
- **ViewBinding** — Type-safe view binding
- **Material Design** — Componentes UI

### Backend & Storage
- **Firebase Authentication** — Google Sign-In, Email/Password
- **Cloud Firestore** — Base de datos NoSQL
- **Firebase Storage** — Almacenamiento de evidencias (disputas)

### Otros
- **Retrofit** — API calls (si aplica)
- **Hilt/Service Locator** — Inyección de dependencias
- **WorkManager/Notifications** — Notificaciones programadas

---

## 🚀 Configuración & Instalación

### Requisitos
- Android Studio Flamingo+
- JDK 11+
- Android SDK 28+
- Gradle 8.0+

### Pasos

1. **Clonar el repositorio**
```bash
git clone https://github.com/tu-usuario/TFG2.git
cd TFG2
```

2. **Configurar Firebase**
   - Crear proyecto en [Firebase Console](https://console.firebase.google.com)
   - Descargar `google-services.json` y colocar en `app/`
   - Habilitar: Authentication (Google, Email), Firestore, Storage

3. **Configurar Google Sign-In**
   - Obtener SHA-1 de tu keystore:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   - Agregar en Firebase Console → Authentication → Google

4. **Compilar y ejecutar**
```bash
./gradlew assembleDebug
./gradlew installDebug
```

O en Android Studio: **Run → Run 'app'**

---

## 📱 Flujos de Uso

### 1️⃣ Crear Grupo
```
Login → Dashboard → "Crear Grupo" 
→ Ingresar nombre → Grupo creado ✅
```

### 2️⃣ Invitar Miembro
```
Dashboard → "Generar invitación" 
→ Compartir código → Otro usuario: "Aceptar invitación" + código
→ Usuario agregado al grupo ✅
```

### 3️⃣ Crear y Asignar Tarea
```
Tareas → "Crear tarea" → Llenar datos 
→ Estado: "pendiente" (sin asignar)
→ Dashboard: "Asignar" → Elegir miembro 
→ Tarea asignada ✅
```

### 4️⃣ Completar Tarea
```
Usuario asignado: Tareas → "Completar"
→ Tarea pasa a "completada"
→ Creador: Tareas → "Confirmar"
→ Tarea "confirmada" + puntos sumados ✅
```

### 5️⃣ Reclamar/Disputar
```
Usuario asignado: Si desacuerda en confirmación
→ Tareas → "Reclamar" → Subir evidencia (foto)
→ Disputa abierta (para revisión manual) ✅
```

---

## 🔑 Entidades Principales

### Usuario
```kotlin
data class Usuario(
    val id: String,
    val nombre: String,
    val email: String,
    val puntos: Int,
    val puntosReservados: Int,
    val puntosRecompensa: Int,
    val rachaDias: Int
)
```

### Tarea
```kotlin
data class Tarea(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val puntos: Int,
    val creadoPor: String,
    val asignadoA: String?,
    val grupoId: String,
    val estado: String, // "pendiente", "completada", "confirmada"
    val fechaProgramada: Timestamp?,
    val esImportante: Boolean
)
```

### Grupo
```kotlin
data class Grupo(
    val id: String,
    val nombre: String,
    val miembros: Map<String, String>, // uid -> rol
    val fechaCreacion: Timestamp
)
```

---

## 🐛 Fixes Recientes (v0.2.2)

### v0.2.2 - Memory Leak Prevention
- ✅ Agregado `repeatOnLifecycle` a todos los Fragments
- ✅ Observers ahora se pausan cuando Fragment no está visible
- ✅ Eliminadas suscripciones activas innecesarias

### v0.2.1 - Architecture Migration
- ✅ Migración completa de callbacks a StateFlow
- ✅ TareasViewModel ahora centraliza lógica de tareas
- ✅ Arquitectura MVVM correcta

### v0.2.0 - Performance
- ✅ Warmup no-bloqueante en Google Sign-In
- ✅ Login: 10-20s → 1-2s
- ✅ Nombres de usuarios se cargan correctamente

---

## 📊 Estados de Tarea

```
┌─────────────────────────────────────────┐
│         FLUJO DE ESTADOS DE TAREA       │
├─────────────────────────────────────────┤
│                                         │
│  CREADA                                 │
│    ↓                                    │
│  PENDIENTE (sin asignar)                │
│    ↓                                    │
│  ASIGNADA → Usuario la completa         │
│    ↓                                    │
│  COMPLETADA (pendiente confirmación)    │
│    ├─→ Creador confirma → CONFIRMADA   │
│    └─→ Usuario reclama → RECLAMADA     │
│                                         │
│  Alternativa: ELIMINADA                │
│                                         │
└─────────────────────────────────────────┘
```

---

## 🧪 Testing

Actualmente **sin tests automatizados**. Próximas sesiones incluirán:
- [ ] Unit tests (ViewModel, Repository)
- [ ] Integration tests (Firestore)
- [ ] UI tests (Fragments)

---

## 📝 Convenciones de Código

- **ViewModels**: `VistaModeloNombre.kt`
- **Fragments**: `FragmentNombre.kt`
- **Adapters**: `NombreAdapter.kt`
- **Repositorios**: `RepositorioNombre.kt`
- **Variables privadas**: `_stateFlow` (MutableStateFlow), `stateFlow` (StateFlow público)
- **Coroutines**: Usar `viewLifecycleOwner.lifecycleScope` en Fragments

---

## 🔍 Debugging & Logs

Tag recomendado para LogCat:
```kotlin
private val TAG = "FragmentTareas"
Log.d(TAG, "mensaje debug")
Log.e(TAG, "error", exception)
```

Errores comunes:
- **PERMISSION_DENIED**: Revisar reglas de Firestore
- **No AppCheckProvider**: Firebase App Check no configurado (ignorar en dev)
- **JobCancellationException**: Normal cuando Fragment se destruye

---

## 🤝 Contribuir

1. Fork el repositorio
2. Crea una rama (`git checkout -b feature/AmazingFeature`)
3. Commit cambios (`git commit -m 'Add AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

---

## 📄 Licencia

Este proyecto es privado. Contacta al autor para permisos.

---

## 👤 Autor

**Victor** - Desarrollo y arquitectura

---

## 📞 Soporte

Para issues, preguntas o sugerencias:
- Abre un Issue en GitHub
- Contacta directamente

---

## 🚧 Roadmap

- [ ] Tests unitarios
- [ ] Notificaciones push
- [ ] Dark mode
- [ ] Exportar tareas (PDF/CSV)
- [ ] Estadísticas avanzadas
- [ ] Integración con calendarios externos
- [ ] Modo offline
- [ ] Invitaciones por QR

---

**Última actualización**: Marzo 31, 2026  
**Versión estable**: v0.2.2 (listener-cleanup)
