<h1 align="center">🎭 Box RolePlay</h1>

<p align="center"><b>Roleplay con IA 100 % local en tu Android. Sin nube. Sin cuentas. Sin límites.</b></p>

[![Fork](https://img.shields.io/badge/Fork%20de-Box%20(jegly)-6272A4.svg)](https://github.com/jegly/Box)
[![Base](https://img.shields.io/badge/Base-Box%201.0.12-6272A4.svg)](https://github.com/jegly/Box/tree/8d6503a8a393ace3d50cfd5084827a0614028c67)
[![Android](https://img.shields.io/badge/Android-13%2B-50FA7B.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-BD93F9.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Engine](https://img.shields.io/badge/Motores-LiteRT%20%2B%20llama.cpp-BD93F9.svg)]()
[![llama.cpp](https://img.shields.io/badge/llama.cpp-GGUF%20%C2%B7%20Vulkan-FFB86C.svg)](https://github.com/ggerganov/llama.cpp)
[![sherpa-onnx](https://img.shields.io/badge/Pocket%20TTS-sherpa--onnx-FF79C6.svg)](https://github.com/k2-fsa/sherpa-onnx)
[![Offline](https://img.shields.io/badge/Red-100%25%20Local-FF5555.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-8BE9FD.svg)](LICENSE)

---

## ¿Qué es Box RolePlay?

**Box RolePlay** es un fork de [Box](https://github.com/jegly/Box) (que a su vez deriva de [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)) reorientado por completo a una sola idea: **conversar con personajes de IA en tu teléfono, sin que nada salga de él**.

> [!NOTE]
> **Base del fork: Box [`1.0.12`](https://github.com/jegly/Box/tree/8d6503a8a393ace3d50cfd5084827a0614028c67)** (commit `8d6503a8`, nov 2026). Este proyecto se desarrolla sobre el código abierto de Box en ese punto de su historia. Las versiones posteriores documentadas en el README de upstream corresponden a compilaciones distribuidas como APK; aquí la referencia es siempre el código de este repositorio.

Los modelos corren en el dispositivo (LiteRT y llama.cpp con GGUF), así que puedes interpretar personajes, montar escenas con varios personajes y mantener memorias de largo plazo sin cuentas, sin suscripciones y sin conexión.

> **Estado actual:** rama activa `fase1-rendimiento` — rendimiento del motor GGUF, TTS con Pocket, visión con mmproj, memoria larga y estructura nueva de la app. La lista de pruebas vivas está en [`analisis/PRUEBAS.md`](analisis/PRUEBAS.md).

---

## ✨ Características de RolePlay

### 👤 Personajes
- **Biblioteca de personajes** con avatar, descripción, escenario, saludos y etiquetas.
- **Ficha de personaje** (resumen): estadísticas de uso, recuerdos, editar/compartir/borrar, elegir saludo y "continuar chat" con la última conversación.
- **Grupos y etiquetas** para filtrar la biblioteca (un personaje puede estar en varios grupos).
- **Tu persona activa**: tu propio perfil con nombre y descripción, que la IA usa para saber quién eres.
- **Copia de seguridad** de todos los personajes en un `.zip` (con avatares) e importación.

### 🎭 Escenas multipersonaje
- Crea **escenarios** con nombre, prompt, etiquetas y 2+ personajes.
- La IA responde dando vida a cada personaje (`Nombre: texto`) sin hablar por ti.
- **Barra de escena** en el chat: `Yo` · `Narrador` · cada personaje · `🎲 Aleatorio`.
- Narrador opcional que describe la escena.

### 🧠 Memoria a largo plazo (dos niveles)
- Tras cada respuesta se guarda una **nota ligera** automáticamente, sin notarse en la velocidad.
- Con **"Absorber clústeres"** el modelo resume esas notas en recuerdos reales y definitivos.
- Solo los recuerdos procesados entran en el prompt de los chats nuevos.
- Interruptores por personaje: *Usar memoria* y *Recopilar recuerdos automáticamente*.
- Pantalla de **Memoria** con buscador, edición y borrado por personaje.

### 🗣 Voz por personaje
- **Pocket TTS** vía sherpa-onnx: clona la voz desde una muestra `.wav` de referencia.
- **Voz distinta por personaje** desde el editor, y lectura automática de las respuestas.
- **Voz en vivo**: la respuesta empieza a sonar frase a frase mientras el modelo sigue escribiendo.
- **Modelos de voz descargables** desde Hugging Face dentro de la app (por idioma, con reanudación).
- Limpieza de emojis y puntuación para que la lectura suene natural; respeta el foco de audio de otras apps.

### ⚙️ Motores guardados y por personaje
- Un **motor** guarda la combinación completa: modelo, visión (mmproj), persona activa y muestreo.
- Aplícalo con un toque, o asigna un **motor por personaje** (prioridad: motor > personaje > predeterminado).
- **Presets de muestreo** con nombre (temperatura, top-K, top-P…).

### 📥 Descarga desde Hugging Face
- Buscador integrado de **modelos GGUF**: por texto, `usuario/repositorio` o enlace directo.
- Ficha de cada archivo: tamaño, cuantización y si **cabe bien / justo / demasiado grande** según tu RAM.
- Descargas **reanudables** (`.part`), aviso de falta de espacio y soporte de **mmproj** para visión.
- Lo mismo para los **modelos de voz** de Pocket TTS.

### 👁 Visión con GGUF (mmproj)
- Importa un proyector multimodal (mmproj) junto a tu GGUF y pide descripciones de imágenes en *Ask Image*.

### 🏠 Inicio propio y mini-apps
- Nueva pantalla de inicio con **mascota**, "Toca para chatear" y **apps fijadas**.
- **Apps independientes**: Texto a voz, Visión, Generador de imágenes, Transcripción, Preguntar con audio, Lab de prompts y Benchmark — cada una abre sin cargar un LLM salvo su tarea.
- **Cajón lateral**: búsqueda de chats, chats agrupados por personaje, Personajes, Apps y Memoria.

### 📊 Rendimiento y diagnóstico
- **Contador de rendimiento** bajo el chat: tokens/s de generación y de prompt tras cada respuesta.
- Ajustes de motor (hilos, flash attention, caché KV) medibles con el contador del chat; el bench sintético pasará a una **app de benchmark independiente**.
- **Exportar logs** (logcat + equipo + último crash) desde Ajustes de Roleplay.
- Liberación del modelo tras 10 min en segundo plano y recarga automática al volver.

---

## 🧱 Lo que hereda de Box (base `1.0.12`)

Del código de Box en ese commit el fork conserva el motor sobre el que se construye el roleplay:

| Área | Detalle |
|---|---|
| Motores de inferencia | **LiteRT** para modelos `.litertlm`/Gemma + **llama.cpp** para GGUF (CPU, con hilos, batch, flash attention y caché KV configurables) |
| Modelos | Lista curada de descargas + importación de cualquier GGUF local + descarga desde Hugging Face (añadido propio) |
| Chat | Multi-turno, markdown, historial cifrado con SQLCipher, reanudable |
| Imagen | Generación con Stable Diffusion vía stable-diffusion.cpp (GGUF) |
| Voz de entrada | Speech-to-text con whisper.cpp |
| Privacidad | Historial cifrado (AES-256), bloqueo biométrico |
| Agente | Skills básicos heredados de Gallery/Box 1.0.x (agent chat, acciones móviles) |
| Benchmark | Pantalla de pruebas de rendimiento de modelos |

Todo lo demás que ves en la app —personajes, escenas, memoria a largo plazo, Pocket TTS, descargas de Hugging Face, motores guardados— es **código propio de este fork**.

---

## 🚧 Roadmap

Proyecto en desarrollo por fases sobre la rama `fase1-rendimiento`. Todo lo marcado está **implementado en código**; la validación en dispositivo avanza en la checklist de [`analisis/PRUEBAS.md`](analisis/PRUEBAS.md) y el estado de los bugs en [`analisis/BUGS.md`](analisis/BUGS.md).

- [x] **Fases 1–5** — Velocidad del motor GGUF, TTS con Pocket (voz por personaje y voz en vivo), visión con mmproj, memoria larga y ligereza en segundo plano
- [x] **Fase 6** — Pulido: nombre de la app, presets de muestreo con nombre
- [x] **Fases A–E2** — Inicio nuevo con apps fijadas, ficha de personaje, apps independientes, escenas multipersonaje, barra de escena, cajón lateral y memoria en dos niveles
- [x] **Extras** — Descarga de modelos y voces desde Hugging Face, motores guardados (y motor por personaje), grupos de personajes, copia de seguridad de personajes (el bench sintético se moverá a una app de benchmark aparte)
- [ ] **Pendiente de decidir** — Memoria semántica (embeddings MiniLM + Room) y entrada de voz (ASR con sherpa-onnx)

---

## 🔨 Compilar desde fuente

```bash
git clone --recurse-submodules -b fase1-rendimiento https://github.com/TempoSo/Box-RolePlay.git
cd Box-RolePlay/Android
./gradlew :app:assembleDebug
```

> El flag `--recurse-submodules` es obligatorio: el proyecto usa los submódulos `llama.cpp`, `stable-diffusion.cpp` y `whisper.cpp`. La primera compilación construye las librerías nativas desde código y puede tardar 15–25 minutos.

También puedes abrir la carpeta `Android/` en **Android Studio** y ejecutar sobre un dispositivo físico (recomendado; el emulador no tiene aceleración NPU/GPU fiable).

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
│       └── ...         #   stable-diffusion.cpp, whisper.cpp
├── analisis/           # Documentos de trabajo
│   ├── BUGS.md         #   inventario vivo: abiertos + dudosos
│   ├── BUGS_CERRADOS.md#   histórico de cerrados (solo si se audita)
│   ├── PRUEBAS.md      #   checklist original de pruebas por fase
│   ├── PLAN_PRUEBAS.md #   plan priorizado + resultados en dispositivo
│   ├── CAMBIOS.md      #   registro de cambios (qué, dónde, por qué)
│   └── MEMORIA.md      #   diseño de memoria a largo plazo
├── model_allowlists/   # Allowlists de modelos por versión
├── skills/             # Agent skills (documentación y ejemplos)
└── images/             # Recursos gráficos
```

---

## 🙏 Créditos

- **[Box](https://github.com/jegly/Box)** de jegly — la base de este fork. Un trabajo enorme de IA local en Android, heredado con gratitud.
- **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)** — el proyecto original sobre el que se construyó Box.
- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** — Georgi Gerganov y contribuidores; el motor GGUF que da vida al roleplay.
- **[sherpa-onnx (k2-fsa)](https://github.com/k2-fsa/sherpa-onnx)** — el motor de voz de Pocket TTS.
- **[LiteRT](https://ai.google.dev/edge/litert)** — el runtime de Google para los modelos `.litertlm` con GPU/NPU/TPU.
- **[whisper.cpp](https://github.com/ggerganov/whisper.cpp)** y **[stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp)** — voz a texto e imagen generativa locales.
- **[Hugging Face LiteRT Community](https://huggingface.co/litert-community)** — por convertir y publicar los modelos que la app ejecuta.

> **Aviso:** Box RolePlay es un proyecto independiente. No está afiliado ni respaldado por Google LLC ni por el autor original de Box. El crédito por la plataforma subyacente es de sus autores originales.

---

## 📄 Licencia

Apache License 2.0. Consulta [LICENSE](LICENSE).
