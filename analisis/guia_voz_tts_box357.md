# Guía técnica: voz y memoria de Box 3.5.7 (y cómo implementarlas tú mismo)

> Análisis propio (13/11/2026) del APK Box 3.5.7 (`versionCode 51`, minSdk 33, targetSdk 37).
> Este documento contiene **arquitectura y plan de implementación con fuentes oficiales** — no código del autor. Todo lo que necesitas está disponible en proyectos públicos con licencia Apache 2.0/MIT, así que esta guía y el código que escribas a partir de ella son publicables en tu fork.
> Contexto: tu fork (base 1.0.1) no tiene TTS propio, ni ASR offline en el chat, ni RAG — estos son los tres huecos que la 3.5.7 resolvió.

## 1. Qué usa Box 3.5.7 (verificado en el APK)

### Motor de voz: sherpa-onnx (k2-fsa)
- Librerías nativas: `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, sobre `libonnxruntime.so`.
- **105 clases wrapper Kotlin** en el paquete `com.k2fsa.sherpa.onnx` (son las clases oficiales del proyecto sherpa-onnx, tal cual se publican en su repo).
- Componentes de voz que instancia:
  - **TTS offline**: `OfflineTts` con configs para **Supertonic** (`OfflineTtsSupertonicModelConfig`), **Kokoro**, **Matcha** y modelos Kitten — 4 motores TTS alternativos.
  - **ASR offline**: `OfflineRecognizer` con **SenseVoice** (`OfflineSenseVoiceModelConfig`).
  - **Denoiser de voz** para mejorar la entrada de micrófono: `OfflineSpeechDenoiser` con modelos GTCRN y DPDFNet.
- **espeak-ng-data incluido en assets** (carpetas `lang/`, `voices/`, diccionarios `*_dict`) — es el fonemizador que Piper necesita. Piper usa `vits` via sherpa-onnx con espeak-ng.

### Memoria / RAG
- Modelo de embeddings: **`all-MiniLM-L6-v2.tflite` (21,7 MB) + `vocab.txt`** en `assets/embedding/`.
- Persistencia con **Room**: `RagDocumentEntity` + `RagChunkEntity` (documento → trozos `ordinal` → texto + `embedding` guardado como `byte[]` = array de floats serializado) y sus DAOs.
- Búsqueda: consulta también se embebe con MiniLM y se compara por similitud (coseno) contra los chunks.

### Otros assets de IA que trae (por si te interesan más adelante)
`bgremoval_models` (U²-Net), `upscale_models` (EDSR/Real-ESRGAN/XLSR), `restore_models` (NAFNet), `migan_models` (borrar objetos), `dmcount_models` (conteo de multitudes), `plantrecognise_models`, `mobilenet_models`, `facerec_models`, OCR de ML Kit, y difusión `bonsai/`, `fluxklein/`, `zimage/`.

## 2. Cómo implementarlo en TU fork (fuentes oficiales, publicable)

### 2.1 Añadir sherpa-onnx (el único "motor" nuevo que necesitas)

```kotlin
// Android/src/app/build.gradle.kts
dependencies {
    // sherpa-onnx publica el AAR en Maven Central (Apache 2.0)
    implementation("com.k2fsa.sherpa:sherpa-onnx-android:1.x.x") // usa la última
}
```

Si prefieres máxima control (como Box): compila los `.so` desde el repo `k2-fsa/sherpa-onnx` (tiene scripts Android listos: `build-android-arm64-v8a.sh`) y añade las **clases wrapper Kotlin oficiales** (están en `sherpa-onnx/kotlin-api/` del repo — son Apache 2.0, copiarlas es legítimo).

### 2.2 Módulo TTS del personaje (Kotlin, tu arquitectura)

Diseño del pipeline (el mismo patrón que usa la 3.5.7, escrito por ti):

```
stream de tokens del LLM  →  separador de frases (cola)  →  OfflineTts.generate(texto_frase)  →  cola de AudioTrack/Player
```

Estructura sugerida en tu repo:

```
Android/src/app/src/main/java/com/google/ai/edge/gallery/
  voice/
    VoiceModule.kt        (Hilt: provee OfflineTts + OfflineRecognizer)
    SentenceQueue.kt      (acumula tokens, corta en frases, emite para TTS)
    VoicePlayer.kt        (AudioTrack en streaming; interrumpible)
    VoiceModels.kt        (descarga/gestión de modelos TTS en archivos del app)
  memory/
    CharacterMemory.kt    (API: remember(text), recall(query, k))
    Embedder.kt           (MiniLM-L6-v2: tokeniza vocab.txt + tflite)
    MemoryStore.kt        (Room: CharacterEntity / MemoryEntity con embedding byte[])
```

Detalles prácticos verificados en la 3.5.7 (aplicables a tu código):

1. **Voces**: no bundled — se descargan a primera vez (carpeta de archivos del app). Modelos y URLs oficiales:
   - Piper (`vits-piper-*`): releases de `github.com/k2-fsa/sherpa-onnx` (tarballs listos, con `espeak-ng-data` incluido).
   - Kokoro / Supertonic / Matcha: mismos releases + Hugging Face (`csukuangfj`, `k2-fsa`).
   - Formato: cada voz es una carpeta con `*.onnx` + `tokens.txt` (+ `espeak-ng-data/` para Piper).
2. **Config típica** de `OfflineTtsConfig`: `model` (vits/kokoro/matcha/supertonic), `ruleFsts`, `maxNumSentences` para streaming, `sid` para elegir voz, `speed`.
3. **ASR del chat**: SenseVoice-Small (multilingüe ES/EN/PT/FR/DE/JA/ZH/KO/YU incluido español) — modelo ~200 MB cuantizado, se descarga igual. Tu fork ya tiene Whisper para el modo dedicado; SenseVoice va mejor para conversación en vivo (5× más rápido).
4. **Denoiser** (opcional, mejora mucho el ASR con ruido): GTCRN ~2 MB vía `OfflineSpeechDenoiser`.
5. **Embeddings**: MiniLM-L6-v2 en TFLite (tu fork ya tiene `libs.tflite` y `libs.tflite.gpu`). Tokenizer WordPiece con el `vocab.txt`. Embedding = 384 floats → guárdalo como `ByteArray` (FloatArray→ByteBuffer LE) como hace la 3.5.7.
6. **Similitud**: cosine sobre los 384 floats; los chunks de texto se trocean por oraciones/párrafos (campo `ordinal` para orden).
7. **Inyección en el prompt**: los top-k recuerdos se anteponen al system prompt del personaje ("Sobre ti / Lo que recuerdas...").

### 2.3 Esquema de datos propuesto (tu diseño, no el suyo)

```kotlin
@Entity(tableName = "character_memory")
data class MemoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val characterId: String,
  val text: String,          // el recuerdo
  val createdAt: Long,
  val embedding: ByteArray   // 384 floats LE
)
// búsqueda: cargar memorias del personaje + cosine en Kotlin (con N mil memorias es instantáneo)
```

(Con miles de memorias: `sqlite-vec` — también Apache 2.0 — pero para roleplay personal, cosine en memoria alcanza de sobra.)

### 2.4 Orden de implementación

1. **TTS del personaje** (Piper primero: calidad/esfuerzo ideal) — VoiceModule + SentenceQueue + VoicePlayer.
2. **ASR en vivo** con SenseVoice para el chat de voz.
3. **Memoria**: Embedder (MiniLM tflite) + MemoryStore + recuerdo automático (al final de cada turno: "resume lo importante de esta conversación" con el propio LLM → embedding → guardar).
4. **Extras**: denoiser, voces Kokoro/Supertonic alternativas, idiomas.

## 3. Nota de licencias (para que duermas tranquilo)

| Pieza | Licencia | ¿Publicable en tu repo? |
|---|---|---|
| sherpa-onnx (código + wrapper Kotlin) | Apache 2.0 | ✅ |
| Modelos Piper/Kokoro/Matcha/Supertonic | Apache 2.0 / MIT (revisar ficha de cada voz) | ✅ como descarga en runtime |
| all-MiniLM-L6-v2 | Apache 2.0 (sentence-transformers) | ✅ descarga en runtime |
| onnxruntime / TFLite / espeak-ng | MIT / Apache 2.0 / GPL (espeak-data tiene su licencia propia, separada) | ✅ |
| Código decompilado del APK 3.5.7 | Propiedad del autor (entrega privada) | ❌ **nunca** |

Regla práctica: **tu repo solo contiene código tuyo y dependencias oficiales**. Los modelos siempre se descargan en runtime (los usuarios aceptan su licencia), nunca se comitean.
