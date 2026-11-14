# Integración con Box-RolePlay (TempoSo/Box-RolePlay)

> Mapa de ruta para llevar a tu app las capacidades de Layla usando **proyectos open source upstream**, sin copiar código propietario del APK de Layla.
> Basado en el análisis completo de `original/obsoleto.apk` y en la estructura real de tu repositorio (verificado el 13/11/2026).

## Regla de oro

**Tu repo es Apache 2.0 y público. El código propio de Layla es propietario.** No se copia código, assets, prompts ni modelos de Layla a tu repo. Lo que sí se hace es *replicar capacidades* con los mismos motores open source que Layla usó por debajo — sacados de sus repositorios oficiales, con licencias limpias y versiones más nuevas.

Además: tu app es **Kotlin + Jetpack Compose**; Layla es **React Native**. Casi nada del código de Layla sería reutilizable ni aunque fuera legal. Lo que se transfiere es la *arquitectura* y la *elección de motores*.

## Tu Box ya tiene (verificado en el repo)

| Capa | En tu repo |
|---|---|
> ⚠️ **Verificado sobre el código real de tu fork (base Box v1.0.1), no sobre el README del upstream v3.5.7** — el README de jegly/Box main describe la versión actual, que tu fork aún no incluye.
>
> 📌 **Hallazgo adicional (13/11/2026): el código público del upstream TAMBIÉN va por detrás de sus APK.** El repo público de `jegly/Box` (581 archivos en main) **no contiene** el código de sherpa-onnx, ni el pipeline RAG/MiniLM, ni Supertonic/Piper/Kokoro — esas funciones solo existen en los APK publicados. Sí está publicado: **Room + SQLCipher** (chat cifrado, dependencias en `build.gradle.kts`). Tu fork está 2 commits detrás del upstream main — probablemente esos exactos.

| Capa | En tu repo (verificado) |
|---|---|
| LLM local | ✅ submódulo `Android/src/llama.cpp` + `libs.litertlm` (LiteRT-LM) + `smollm/` |
| Imágenes | ✅ submódulo `stable-diffusion.cpp` + `stablediffusion/` + TFLite GPU |
| ASR (escucha) | ✅ submódulo `whisper.cpp` con su UI (`ui/whisper/`: WhisperScreen, WhisperViewModel) — pero el input de voz del chat usa el **SpeechRecognizer del sistema** (no offline) |
| TTS (voz) | ❌ **No hay motor TTS propio** — usa el TTS del sistema Android |
| NPU | ✅ jniLibs QNN (Qualcomm V69–V75) + dispatch LiteRT Qualcomm/MediaTek/GoogleTensor |
| Chat UI | ✅ `com.google.ai.edge.gallery.ui.common.chat` (36 archivos) |
| Agente + MCP | ✅ `customtasks/agentchat` (15 archivos) + skills |
| Persistencia | ⚠️ DataStore + `security.crypto` (prefs cifradas) — **sin Room ni SQLCipher** |
| Seguridad | ✅ `security/` (7 archivos) |
| Inyección | ✅ Hilt en `di/` |
| RAG / embeddings | ❌ **No existe** en tu fork (llegó en versiones posteriores de Box) |
| sherpa-onnx | ❌ **No existe** en tu fork (llegó después) |

**Conclusión corregida: ~60% de los motores de Layla los tienes; te faltan los de voz (TTS/ASR offline) y la memoria semántica — que son justamente el corazón del roleplay.**

## Gap real: lo que Layla tiene y tu Box no

| # | Función de Layla | Cómo replicarla legalmente | Licencia | Esfuerzo |
|---|---|---|---|---|
| 1 | **Memoria de personaje a largo plazo** (vectordb: recuerdos semánticos) | `sqlite-vec` (MIT) en SQLite + embeddings MiniLM (GGUF/ONNX via llama.cpp o onnxruntime) — **hay que construir el pipeline desde cero** (tu fork no tiene RAG todavía) | MIT | Media |
| 2 | **TTS en streaming frase a frase** (el personaje habla mientras el LLM genera) | **Añadir sherpa-onnx** (Apache 2.0, AAR en Maven Central) con Piper/Kokoro; enlazar el stream de tokens de llama.cpp/LiteRT-LM a una cola de frases → TTS → audio. Alternativa rápida de arranque: TTS del sistema Android (ya funciona, menos calidad) | Apache 2.0 | Media-Alta |
| 3 | **Mensajes programados del personaje** (el personaje te escribe a las X) | WorkManager + alarmas exactas (`SCHEDULE_EXACT_ALARM`) — patrón estándar de Android, ~1 día de trabajo | — | Baja |
| 4 | **Música IA** (ACE-Step) | **ACE-Step** upstream (`github.com/ace-step/ACE-Step`), Apache 2.0. Layla lo compila como engine C++ (`libacestep_engine.so`); exige convertir/pesos ONNX. Riesgo: modelo pesado y GPU-hambriento | Apache 2.0 | **Alta** |
| 5 | **Asistente de sistema + overlay** (Layla reemplaza a Google Assistant) | `VoiceInteractionService` + `MediaProjection` + overlay `SYSTEM_ALERT_WINDOW` — API públicas de Android; tu Box ya tiene "Box Assist" como base | — | Media |
| 6 | **Personajes animados (Live2D)** | Cubism SDK es **propietario**: licencia gratuita de Live2D para apps pequeñas, pero no puedes redistribuir el SDK en tu repo (instalación por submodule privado o descarga del usuario). Alternativa 100% libre: **Rive** (runtime BSD) o sprites/VRM (formato abierto 3D con `UniVRM`-equivalente) | ❗ Cubism: propietario | Media-Alta |
| 7 | **Python embebido / terminal Bash** (lython, bashkit) | **No recomendado**: `bashkit` y `lython` son componentes propios/ignotos de Layla, no proyectos upstream claros. Si necesitas scripting: Chaquopy (Python en Android, MIT) — pero para roleplay no aporta | — | — |
| 8 | **P2P entre dispositivos** (`liblayla_p2p_core`) | WebRTC (BSD, Google) — pero para roleplay personal no es prioridad | BSD | Alta |
| 9 | **Lector de códigos / Tasker / Cast** | Integraciones de producto de Layla; solo si las quieres de verdad | — | Baja-Media |

## Lo que NO se toma de Layla (inventario de prohibiciones)

| Elemento | Motivo |
|---|---|
| `index.android.bundle` (JS de la app, prompts de personajes, catálogo) | Código propietario; además es bytecode Hermes sin fuente |
| Módulos Kotlin propios (`expo.modules.layla*`, `ai.layla.*` en código) | Propietario |
| `libphonon_engine.so`, `libbashkit_core.so`, `libneodragon*`, `liblython*` | Sin fuente upstream identificable = propietario/desconocido |
| Archivos `.riv` (annie, eleonora, butterfly) | Assets propietarios |
| Modelos de CivitAI | **Casi siempre licencia no-comercial o restricciones**; nunca redistribuir en el repo. Tu app debe descargarlos en runtime y que el usuario acepte la licencia de cada modelo |
| Modelos de personajes/avatares de Live2D | Licencia de Live2D por modelo |

## Modelos que sí puedes redirigir (upstream, licencias correctas)

Los mismos que Layla usaba, pero desde sus fuentes oficiales:

- **LLM**: `ggml-org/gemma-4-E2B-it-GGUF`, `gemma-4-E4B-it`, InternVL3-1B, SmolVLM2-500M — Hugging Face (licencias Gem/open). Tu Box ya los lista.
- **LiteRT**: `huggingface.co/litert-community` — la comunidad que tu README ya agradece.
- **TTS**: Piper (MIT) via sherpa-onnx — voces `vits-piper-*` descargables en runtime.
- **Imagen**: los `-qnn`/`-htp` de Layla eran conversiones propias para Qualcomm/mediatek; tu Box ya tiene la ruta LiteRT/QNN oficial (QAIRT 2.47).

## Dónde enchufar cada cosa en tu repo (rutas reales)

| Nueva capacidad | Archivos/módulo a crear o tocar |
|---|---|
| Memoria de personaje | Nuevo `com.google.ai.edge.gallery.customtasks/charactermemory` + capa de persistencia (DataStore/SQLite; tu fork **no usa Room**, persiste con DataStore + prefs cifradas) + `sqlite-vec` + embedding MiniLM nuevo |
| Streaming TTS | Nuevo módulo de audio junto a `runtime/` (sherpa-onnx) + consumer del stream de tokens en `ui/common/chat` |
| Mensajes programados | `runtime/` o `data/` (WorkManager + AlarmManager) + `ui/` para configurar horarios |
| Música | Nuevo engine junto a los submódulos existentes (`Android/src/`), siguiendo el patrón JNI de `llama.cpp`/`whisper.cpp` |
| Asistente de sistema | `AndroidManifest.xml` de la app (`Android/src/app`) + servicio en `runtime/` |
| Personajes (datos) | `data/local/entities` (CharacterEntity, PersonaEntity...) + `ui/` de ficha de personaje |

## Orden de trabajo recomendado

1. **Fase 1 — Voz del personaje** (mínimo viable: TTS del sistema; luego sherpa-onnx + Piper para calidad offline). Es lo que más cambia la sensación de roleplay y tu fork tiene el hueco.
2. **Fase 2 — Memoria de personaje** (sqlite-vec + embeddings MiniLM desde cero: hay que añadir el modelo de embeddings, la tabla vectorial y la inyección en el prompt).
3. **Fase 3 — Mensajes programados + ficha de personaje** (formato de persona propio, no el de Layla — p. ej. JSON propio con `name`, `persona`, `memories`, `voice`).
4. **Fase 4 — (opcional) ACE-Step / asistente de sistema / animaciones Rive**. Solo cuando 1–3 funcionen.
5. **Nunca**: desempaquetar el APK de Layla para "tomar" piezas — todo lo anterior se consigue desde upstream con licencias limpias.

## Resumen ejecutivo

- Tu fork ya tiene el 80% de los motores, en versiones mejores que las de Layla.
- La ventaja real de Layla para roleplay son **3 patrones de arquitectura**: memoria vectorial de personaje, TTS en streaming y programación de mensajes — todos replicables con librerías open source en tu propia arquitectura Compose.
- El 20% restante (música ACE-Step, Live2D, Python) es opcional y con trampas de licencia; se decide más adelante.
