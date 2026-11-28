<h1 align="center">🎭 Box RolePlay</h1>

<p align="center"><b>Roleplay con IA 100 % local en tu Android. Sin nube. Sin cuentas. Sin límites.</b></p>

<p align="center"><a href="README_EN.md">🇬🇧 Read this in English</a></p>

[![Fork](https://img.shields.io/badge/Fork%20de-Box%20(jegly)-6272A4.svg)](https://github.com/jegly/Box)
[![Base](https://img.shields.io/badge/Base-Box%201.0.12-6272A4.svg)](https://github.com/jegly/Box/tree/8d6503a8a393ace3d50cfd5084827a0614028c67)
[![Android](https://img.shields.io/badge/Android-13%2B-50FA7B.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-BD93F9.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Engine](https://img.shields.io/badge/Motores-LiteRT%20%2B%20llama.cpp-BD93F9.svg)]()
[![llama.cpp](https://img.shields.io/badge/llama.cpp-GGUF%20%C2%B7%20Vulkan-FFB86C.svg)](https://github.com/ggerganov/llama.cpp)
[![TTS](https://img.shields.io/badge/Voz-Pocket%20%2B%20Kokoro-FF79C6.svg)](https://github.com/k2-fsa/sherpa-onnx)
[![Offline](https://img.shields.io/badge/Red-100%25%20Local-FF5555.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-8BE9FD.svg)](LICENSE)

---

## ¿Qué es Box RolePlay?

**Box RolePlay** es un fork de [Box](https://github.com/jegly/Box) (que a su vez deriva de [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)) reorientado por completo a una idea: **conversar con personajes de IA en tu teléfono, sin que nada salga de él**.

> [!NOTE]
> **Base del fork: Box [`1.0.12`](https://github.com/jegly/Box/tree/8d6503a8a393ace3d50cfd5084827a0614028c67)** (commit `8d6503a8`). Este proyecto se desarrolla sobre el código abierto de Box en ese punto de su historia. Las versiones posteriores del README de upstream corresponden a APK distribuidos; aquí la referencia es siempre el código de este repositorio.

Los modelos corren en el dispositivo (LiteRT y llama.cpp con GGUF): puedes interpretar personajes, montar escenas con varios personajes y mantener memoria a largo plazo sin cuentas, sin suscripciones y sin conexión.

> **Estado actual:** rama principal `main`, con la tienda, el chat, la memoria por turno y Kokoro en español ya integrados. El checklist de dispositivo está en [`analisis/PRUEBAS.md`](analisis/PRUEBAS.md) y el plan priorizado en [`analisis/PLAN_PRUEBAS.md`](analisis/PLAN_PRUEBAS.md). Kokoro en español está validado en móvil; la recuperación de memoria y los botones nuevos del chat siguen pendientes de validación física.

---

## ✨ Características de RolePlay

### 👤 Personajes
- **Biblioteca de personajes** con avatar, descripción, escenario, saludos y etiquetas; grupos y filtros.
- **Ficha de personaje**: estadísticas de uso, recuerdos, editar/compartir/borrar, elegir saludo y «continuar chat».
- **Tu persona activa**: tu perfil con nombre y descripción que la IA usa para saber quién eres.
- **Copia de seguridad** en `.zip` (personajes, recuerdos, personas, presets y escenas) e importación selectiva.
- **Tienda de personajes**: búsqueda en Chub, cuadrícula de 2 columnas, páginas numeradas, filtro +18, importación por enlace y botón «Sorpréndeme».

### 🎭 Escenas multipersonaje
- Escenarios con nombre, prompt, etiquetas y 2+ personajes; la IA da vida a cada uno (`Nombre: texto`) sin hablar por ti.
- **Barra de escena** en el chat: `Yo` · `Narrador` · cada personaje · `🎲 Aleatorio`, con narrador opcional.

### 🧠 Memoria a largo plazo (dos niveles)
- Tras cada respuesta se guarda una **nota ligera** automáticamente; con **«Absorber clústeres»** el modelo la destila en recuerdos definitivos.
- Los recuerdos se recuperan **en cada turno** por palabras clave, recencia, importancia y uso, con umbral y tope configurables. La sesión actual queda excluida para evitar eco.
- El **lorebook** del personaje es canon del autor y se inyecta por turno por encima de la memoria aprendida.
- El bloque de memoria se mantiene estable mientras no cambien los recuerdos elegidos, para no romper la caché de prefijo de llama.cpp. «Información del chat» muestra qué se recuperó y con qué puntuación.
- Interruptores por personaje para *Usar memoria* y *Recopilar recuerdos automáticamente*; pantalla de Memoria con buscador, edición y borrado.

### 🗣 Voz: clonación y voces incluidas
- **Pocket TTS** vía sherpa-onnx: clona una voz desde una muestra `.wav` de 5–12 s. La muestra se limpia al guardar (DC, ruido, silencios, volumen) y se conserva el original para poder rehacerlo.
- **Kokoro** con voces incluidas en el modelo, seleccionables por personaje. **Español real** gracias a fonemización IPA propia (`KokoroPhonemizer` + lexicon), sin depender de espeak-ng.
- **Voz distinta por personaje**, lectura automática de respuestas y **voz en vivo** frase a frase mientras el modelo escribe.
- **Modelos descargables desde Hugging Face** dentro de la app (por idioma, con reanudación), y biblioteca de voces con nombre, etiquetas, grabación y escucha previa.
- Limpieza de emojis y puntuación para una lectura natural; respeta el foco de audio de otras apps.

### 💬 Chat
- Botones de **Copiar, Editar, Hablar/Parar, Regenerar y Continuar** sobre cada mensaje del personaje.
- «Continuar» recorta el final de la respuesta y pide al modelo que siga, sin dejar un mensaje de usuario visible.

### ⚙️ Motores guardados y por personaje
- Un **motor** guarda modelo, visión (mmproj), persona activa y muestreo; se aplica con un toque o se asigna por personaje.
- **Presets de muestreo** con nombre (temperatura, top-K, top-P…).

### 📥 Descarga desde Hugging Face
- Buscador integrado de **modelos GGUF** por texto, `usuario/repositorio` o enlace, con tamaño, cuantización y aviso de si cabe en RAM.
- Descargas **reanudables** (`.part`), control de espacio y soporte de **mmproj** para visión. Lo mismo para modelos de voz.

### 👁 Visión y otras apps
- Visión con GGUF (mmproj) en *Ask Image*; generación de imágenes, transcripción, preguntar con audio, lab de prompts y benchmark como apps independientes.
- Inicio propio con mascota y apps fijadas; cajón lateral con chats agrupados por personaje.

### 📊 Rendimiento y diagnóstico
- Contador de tokens/s de generación y de prompt bajo el chat; ajustes de hilos, flash attention y caché KV.
- **Exportar logs** (logcat + equipo + último crash) desde Ajustes de Roleplay; liberación del modelo tras 10 min en segundo plano.

---

## 🧱 Lo que hereda de Box (base `1.0.12`)

| Área | Detalle |
|---|---|
| Motores de inferencia | **LiteRT** para `.litertlm`/Gemma y **llama.cpp** para GGUF (hilos, batch, flash attention y caché KV configurables) |
| Modelos | Lista curada de descargas, importación de GGUF local y descarga desde Hugging Face (añadido propio) |
| Chat | Multi-turno, markdown, historial cifrado con SQLCipher, reanudable |
| Imagen | Generación con Stable Diffusion vía stable-diffusion.cpp (GGUF) |
| Voz de entrada | Speech-to-text con whisper.cpp |
| Privacidad | Historial cifrado (AES-256), bloqueo biométrico |
| Benchmark | Pantalla de pruebas de rendimiento de modelos |

Todo lo demás —personajes, escenas, memoria, TTS, descargas de Hugging Face, motores guardados, tienda— es **código propio de este fork**.

---

## 🚧 Roadmap

- [x] Fases 1–5 — motor GGUF, TTS (Pocket + Kokoro), visión, memoria y ligereza en segundo plano
- [x] Fases 6 y A–E2 — pulido, inicio nuevo, ficha de personaje, apps, escenas, cajón lateral y memoria en dos niveles
- [x] Tienda con paginación y aleatorio; acciones de chat; memoria por turno con scoring y trazabilidad
- [x] Kokoro en español con fonemización propia (validado en dispositivo)
- [ ] **Voice Studio**: importar mp3/m4a, remuestreo, recorte con forma de onda y comparación A/B antes de clonar
- [ ] Ajustes de recuperación de memoria en la UI (hoy existen con valores por defecto)
- [ ] Memoria semántica (embeddings locales) y absorción en segundo plano

---

## 🔨 Compilar desde fuente

```bash
git clone --recurse-submodules https://github.com/TempoSo/Box-RolePlay.git
cd Box-RolePlay/Android
./gradlew :app:assembleDebug
```

> `--recurse-submodules` es obligatorio: el proyecto usa `llama.cpp`, `stable-diffusion.cpp` y `whisper.cpp`. La primera compilación construye librerías nativas y puede tardar 15–25 min.
>
> Si falla por sherpa-onnx, el AAR va en `Android/src/app/libs/sherpa-onnx.aar` (ver `Android/src/app/libs/LEEME.txt`).

También puedes abrir `Android/` en Android Studio y ejecutar en un dispositivo físico (recomendado; el emulador no tiene aceleración NPU/GPU fiable).

### Requisitos

- Android 13 o superior
- ~4 GB de almacenamiento libre para un LLM cuantizado típico
- 6–8 GB de RAM recomendados (según el modelo)

---

## 📁 Estructura del repositorio

```
├── Android/            # Código de la app (Kotlin + Jetpack Compose)
│   └── src/
│       ├── app/        #   módulo principal
│       ├── llama.cpp/  #   submódulo (motor GGUF)
│       └── ...         #   stable-diffusion.cpp, whisper.cpp, smollm
├── analisis/           # Documentos de trabajo
│   ├── BUGS.md / BUGS_CERRADOS.md
│   ├── PRUEBAS.md / PLAN_PRUEBAS.md
│   ├── CAMBIOS.md      #   registro de cambios
│   ├── MEMORIA.md / MEMORIA_MEJORAS.md
├── model_allowlists/   # Allowlists de modelos por versión
├── skills/             # Agent skills (documentación y ejemplos)
└── images/             # Recursos gráficos
```

---

## 🙏 Créditos

- **[Box](https://github.com/jegly/Box)** de jegly — la base de este fork.
- **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)** — el proyecto original.
- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** — motor GGUF.
- **[sherpa-onnx (k2-fsa)](https://github.com/k2-fsa/sherpa-onnx)** — motores Pocket TTS y Kokoro.
- **[LiteRT](https://ai.google.dev/edge/litert)** — runtime de modelos `.litertlm`.
- **[whisper.cpp](https://github.com/ggerganov/whisper.cpp)** y **[stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp)** — voz a texto e imagen.
- **[Hugging Face LiteRT Community](https://huggingface.co/litert-community)** — modelos convertidos y publicados.

> **Aviso:** Box RolePlay es un proyecto independiente, no afiliado ni respaldado por Google LLC ni por el autor original de Box.

---

## 📄 Licencia

Apache License 2.0. Consulta [LICENSE](LICENSE).
