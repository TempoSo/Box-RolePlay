# PROMPT MAESTRO — Box-RolePlay: llevar mi fork al nivel de Box 3.5.7 + roleplay estilo Layla

> **Cómo usar este documento**: cópialo entero como primer mensaje en una sesión nueva (Claude u OpenCode). Contiene todo el contexto verificado del proyecto. Trabaja UNA fase por sesión, marcándola como completada al final del documento (añade ✅ y fecha).

---

## PARTE 1 — CONTEXTO DEL PROYECTO

Soy el autor de **Box-RolePlay** (`https://github.com/TempoSo/Box-RolePlay`), un fork público de `jegly/Box` (Apache 2.0, fork de Google AI Edge Gallery). Mi app es **Kotlin + Jetpack Compose + Hilt**, paquete `com.google.ai.edge.gallery`.

**Mi fork está basado en Box v1.0.1** (no en la 3.5.7). Estructura del repo:
```
Android/src/
  app/                       → la app (paquete com.google.ai.edge.gallery)
    src/main/java/com/google/ai/edge/gallery/
      ui/common/chat/        → arquitectura de chat (36 archivos)
      ui/common/textandvoiceinput/ → input de voz (usa SpeechRecognizer del SISTEMA, no offline)
      customtasks/agentchat/ → agente con skills + MCP (15 archivos)
      data/local/            → dao/, entities/ (persistencia)
      security/              → biometría, saneo de prompts, auditoría
      di/                    → Hilt
  llama.cpp/                 → submódulo (LLM local GGUF con Vulkan)
  stable-diffusion.cpp/      → submódulo (imágenes)
  whisper.cpp/               → submódulo + ui/whisper/ (STT en su pestaña propia)
  smollm/, stablediffusion/, whisper/  → módulos wrapper
```

**Ramas**: `main` = espejo del upstream original; `Prueba1` = mi base de desarrollo (37 commits propios); `fase1-rendimiento` = Prueba1 + 1 commit de rendimiento GGUF (rama activa de trabajo). Las 3 ramas estaban 3 commits detrás de upstream; el upstream main **sí tiene publicado Room + SQLCipher** (chat cifrado) que yo no tengo.

**Verificado sobre mi código real (no el README, que describe la 3.5.7 y engaña)**:
- ✅ Tengo: llama.cpp (GGUF+Vulkan), stable-diffusion.cpp, whisper.cpp, LiteRT-LM (`libs.litertlm`), TFLite + GPU, jniLibs QNN (Qualcomm V69–V75) + dispatch LiteRT Qualcomm/MediaTek/GoogleTensor, CameraX, Hilt, WorkManager, DataStore + `androidx.security.crypto`, appauth, protobuf.
- ❌ NO tengo: **TTS propio** (uso el TTS del sistema), **ASR offline en el chat** (SpeechRecognizer del sistema), **RAG/embeddings**, **Room** (persisto con DataStore), **SQLCipher**, sherpa-onnx.

## PARTE 2 — REFERENCIA: qué tiene Box 3.5.7 (verificado por análisis de su APK) y Layla (por análisis de su APK)

**Box 3.5.7** (versionCode 51, minSdk 33, targetSdk 37):
- **Voz = sherpa-onnx (k2-fsa, Apache 2.0)**: `libsherpa-onnx-jni.so` sobre onnxruntime; 105 clases wrapper Kotlin `com.k2fsa.sherpa.onnx` (las oficiales upstream).
  - TTS offline: Supertonic, Kokoro, Matcha, Kitten + **Piper** (con `espeak-ng-data/` en assets: `lang/`, `voices/`, `*_dict`).
  - ASR: **SenseVoice** (multilingüe, ES incluido, ~5× más rápido que Whisper).
  - Denoiser de micrófono: GTCRN / DPDFNet (`OfflineSpeechDenoiser`).
- **Memoria/RAG**: embeddings con **all-MiniLM-L6-v2.tflite** (21,7 MB) + `vocab.txt` en `assets/embedding/`; persistencia Room: `RagDocumentEntity` → `RagChunkEntity` (texto + `ordinal` + embedding como `byte[]` = 384 floats LE); búsqueda por similitud coseno.
- Otros: upscale (EDSR/Real-ESRGAN/XLSR), restauración (NAFNet), borrado de fondo (U²-Net), MI-GAN, reconocimiento plantas/rostros, OCR ML Kit, difusión Bonsai/FLUX.2 klein/Z-Image.

**Layla** (app propietaria de roleplay, v7.7.0.1, React Native+Expo 57, minSdk 28/target 36): chat con personajes con **memoria semántica de largo plazo (vectordb)**, TTS streaming **frase a frase mientras el LLM genera**, personajes animados (Rive/Live2D), voces Piper/ElevenLabs, **mensajes programados del personaje** (WorkManager + alarmas exactas), asistente de voz del sistema (VoiceInteractionService), overlay flotante, integración Tasker, IA 100% local con llama.cpp (Gemma E2B/E4B) + QNN para chips Qualcomm/MediaTek.

**REGLAS LEGALES (obligatorias)**:
1. El creador de Box me compartió el APK/código de la 3.5.7 **privadamente con condición de no compartirlo**. **NUNCA** subir a mi repo (público) nada derivado de ese APK: ni smali, ni código decompilado, ni `.so`, ni assets, ni modelos. Material de estudio queda fuera del repo.
2. Mi repo solo contiene: código escrito por mí + dependencias oficiales (Apache 2.0/MIT) + submódulos upstream.
3. Modelos (voces Piper/Kokoro, MiniLM, SenseVoice): **descarga en runtime**, nunca comiteados.
4. Layla es propietario: nada de su código; solo replico *capacidades* con piezas open source.
5. Antes de cambios grandes (renombrar paquete, migrar persistencia, subir minSdk): pedirme confirmación.

## PARTE 3 — META Y FASES DE TRABAJO

**Meta**: que mi fork tenga el nivel técnico de Box 3.5.7 y las capacidades de roleplay de Layla: personaje con voz (TTS streaming), escucha (ASR offline), memoria de largo plazo, mensajes programados, fichas de personaje. IA 100% local.

### FASE 0 — Higiene del repo (media sesión)
1. Borrar rama duplicada: `git push origin --delete fix/roleplay-bugs` (idéntica a Prueba1).
2. Traer lo publicado del upstream: `git pull origin main` en `fase1-rendimiento` → gano **Room + SQLCipher** (chat cifrado) sin trabajo propio.
3. Crear `.gitignore` con: `box 3.5.7/`, `jadx*/`, `apktool*/`, `original/`, `analisis/` si contiene material privado. Verificar con `git status` que nada privado entra.
4. Compilar baseline: `./gradlew :app:assembleDebug` (desde `Android/src/`, con `--recurse-submodules`).

### FASE 1 — TTS del personaje (sherpa-onnx) ⭐ prioridad — con escalera de calidad
**Elección de motor (decisión tomada):** Piper como base (día 1) y **PocketTTS como objetivo de calidad**: TTS de Kyutai (`github.com/kyutai-labs/pocket-tts`, MIT) que corre en CPU, streaming ~200ms, multilenguaje con voz española ("lola") y **clonación de voz zero-shot** (pasar un WAV de muestra). **Verificado que sherpa-onnx soporta PocketTTS** con bindings Kotlin/Java — se integra como otra config de `OfflineTts`, sin cambiar la arquitectura (cola de frases/reproductor intactos). Alternativa avanzada: **Pocket-TTS-LiteRT** (`huggingface.co/mlboydaisuke/Pocket-TTS-LiteRT`) — gráficos `.tflite` en GPU Android via LiteRT CompiledModel (~1× RT en Pixel 8a), y el fork ya tiene LiteRT. Otras alternativas: Kokoro-82M, Supertonic (Supertone). **Regla ética de clonación**: solo muestras propias o voces con licencia (ver fichas en `kyutai/tts-voices`); jamás clonar voces de personas reales sin consentimiento (condición de uso de Kyutai). Kokoro/Supertonic como respaldo; F5/CosyVoice descartados (peso no realista en móvil).
1. Añadir dependencia: `implementation("com.k2fsa:sherpa-onnx-android:<última>")` (Maven Central, Apache 2.0) — o compilar los `.so` con `build-android-arm64-v8a.sh` del repo k2-fsa y copiar las clases wrapper Kotlin oficiales (`sherpa-onnx/kotlin-api/`).
2. Crear módulo `voice/` en `com.google.ai.edge.gallery.voice`:
   - `VoiceModule.kt` (Hilt @Module): provee `OfflineTts` (config Piper: `vits`, `tokens.txt`, `espeak-ng-data`, `dataDir`, `sid`, `speed`).
   - `SentenceQueue.kt`: recibe stream de tokens del LLM → acumula → corta por puntuación de fin de frase (`.!?…`) → emite frases (coroutines Flow).
   - `VoicePlayer.kt`: reproduce los PCM con `AudioTrack` (streaming, interrumpible, volumen/speed configurables).
   - `VoiceModels.kt`: descarga (WorkManager) de voces Piper desde releases de `github.com/k2-fsa/sherpa-onnx` (tarballs `vits-piper-es_*` para español, `en_US-amy`, etc.) a `filesDir/voices/`.
3. Conectar al chat de `ui/common/chat/`: toggle 🔊 que activa que las respuestas se hablen **frase a frase mientras el modelo genera** (el patrón clave de Layla).
4. **Criterio de hecho**: conversación donde la respuesta empieza a sonar antes de terminar de generarse, con voz en español.

### FASE 2 — ASR offline del chat (SenseVoice)
1. Reemplazar/aprobar el `SpeechRecognizer` del sistema de `textandvoiceinput/` por `OfflineRecognizer` de sherpa-onnx con SenseVoice-Small (~200 MB cuantizado, descarga en runtime desde releases de k2-fsa).
2. Micrófono → grabar → (opcional denoiser GTCRN, ~2 MB) → transcribir → enviar al chat.
3. **Criterio**: dictado de voz en el chat funcionando **sin Google services** (¡clave para custom ROMs!).

### FASE 3 — Memoria de personaje (MiniLM + Room)
1. Añadir `all-MiniLM-L6-v2.tflite` por descarga en runtime (fuente: sentence-transformers/HF, conversión TFLite) + tokenizer WordPiece con `vocab.txt` (384 dims).
2. Crear módulo `memory/`:
   - `Embedder.kt`: texto → FloatArray(384) con el tflite.
   - `MemoryStore.kt`: Room `@Entity character_memory(id, characterId, text, createdAt, embedding: ByteArray)` + DAO.
   - `CharacterMemory.kt`: `remember(text)`, `recall(query, k=5)` (coseno en Kotlin; con N<10k es instantáneo; opcional luego `sqlite-vec`).
3. Auto-recuerdo: al final de cada turno de chat, prompt al propio LLM: "resume lo que debe recordar este personaje de esta conversación" → embedding → guardar (dedupe por similitud >0.95).
4. Inyección: top-k recuerdos antepuestos al system prompt ("Lo que recuerdas sobre este usuario: ...").
5. **Criterio**: el personaje recuerda datos dichos hace 20+ turnos sin que se los repita el usuario.

### FASE 4 — Ficha de personaje + mensajes programados
1. `CharacterEntity` (Room): nombre, persona (system prompt), voz asignada, tono, recuerdos → UI de ficha (Compose) y selector de personaje en el chat.
2. Formato de ficha **propio** (JSON propio, no copiar el de Layla): `{"name","persona","voice","greeting","memoryEnabled"}` con import/export.
3. Mensajes programados: WorkManager (`PeriodicWorkRequest`/`OneTimeWorkRequest` con flex) + notificación cuando el personaje "decide escribirte" (lógica simple: horarios configurados o probabilidad tras silencio).
4. **Criterio**: personaje con ficha, voz propia y que escribe a una hora programada.

### FASE 5 — Extras (elegir según gusto)
- Voces adicionales: Kokoro / Supertonic / Matcha (mismas infra, configs distintas de sherpa-onnx).
- Denoiser GTCRN para micrófono ruidoso.
- Skills de roleplay en `customtasks/agentchat/` (ej. "narra escena", "dado", "cambiar de escena").
- Overlay flotante tipo Layla (SYSTEM_ALERT_WINDOW) o asistente de sistema (VoiceInteractionService) — grande, opcional.

### FASE 6 — Consolidación
- Sync con upstream `jegly/Box` main (merge selectivo).
- Release con APK en GitHub (Obtainium ya funciona con el patrón del repo).
- Actualizar README del fork con las features nuevas y créditos (Apache 2.0 + NOTICES).

## PARTE 4 — REGLAS PARA TI (IA) AL TRABAJAR CON ESTE PROMPT

1. Trabaja **una fase por vez**; pregunta "¿continuamos con la fase X?" antes de avanzar.
2. Verifica mi repo real antes de afirmar que algo existe: **no confíes en el README del upstream** (describe la 3.5.7; mi fork es 1.0.1).
3. Nunca sugieras subir material decompilado o privado al repo.
4. Código nuevo: Kotlin idiomático, Hilt para DI, coroutines/Flow, adaptado al estilo del repo (Material 3, Compose).
5. Si un dato no está verificado aquí (versiones concretas, URLs exactas), búscalo o dímelo antes de inventarlo.
6. Al terminar cada fase: pídeme marcarla ✅ en este documento con fecha y un resumen de lo cambiado.

## PARTE 5 — ESTADO DE FASES

| Fase | Estado | Fecha | Notas |
|---|---|---|---|
| 0 — Higiene repo | ⬜ | | fix/roleplay-bugs por borrar; upstream SQLCipher por traer |
| 1 — TTS personaje | ⬜ | | |
| 2 — ASR offline | ⬜ | | |
| 3 — Memoria | ⬜ | | |
| 4 — Ficha + programados | ⬜ | | |
| 5 — Extras | ⬜ | | |
| 6 — Consolidación | ⬜ | | |
