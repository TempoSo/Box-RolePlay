# Arquitectura de la aplicación

> Explicación técnica y, entre líneas, una versión "para no programadores".

## Visión general (para no programadores)

Imagina la app como un edificio de tres plantas:

1. **Planta baja (código nativo Android, Kotlin)**: lo que toca directamente el "suelo" del teléfono — abrir la app, permisos, notificaciones, servicios de fondo.
2. **Primer piso (JavaScript, React Native/Expo)**: donde está casi todo lo que ves y usas — pantallas, chat, ajustes, galerías.
3. **Segundo piso (C++, librerías nativas)**: los "motores" pesados — inteligencia artificial, voz, música, imágenes.

Los tres pisos se comunican por "puentes" predefinidos (el sistema de módulos de React Native y Expo, y un sistema moderno llamado "Nitro"). 

## Capa 1: Nativa (Kotlin/Java)

Confirmado por el manifest y el smali:

- **Punto de entrada**: `com.layla.MainApplication` → `com.layla.MainActivity` (una sola pantalla principal; la navegación es interna de React Native).
- **Build**: Kotlin 2.3.20, Gradle 9.3.1, Java 17 (datos del `kotlin-tooling-metadata.json` dentro del APK).
- **Módulos Expo registrados** (lista oficial extraída de `ExpoModulesPackageList`):

  **Estándar de Expo:** application, asset, audio, blur, calendar, calendar-next, camera, clipboard, constants, contacts, contacts-next, documentpicker, filesystem (+ legacy), font, haptics, image, imagemanipulator, imagepicker, keepawake, linking, localization, localauthentication, location, medialibrary (+ next), notifications (completo: canales, programación, tokens), screencapture, shareintent, sharing, sms, speech, speechrecognition, splashscreen, sqlite, storereview, video, webview, fetch.

  **Propios de Layla (a medida):**
  | Módulo | Qué parece hacer |
  |---|---|
  | `expo.modules.laylaassistant` | Asistente de voz del sistema (VoiceInteractionService, actividad flotante) |
  | `expo.modules.laylaaudio` | Audio propio (voz del personaje en conversación) |
  | `expo.modules.laylacompanion` | Automatización + integración Tasker (muchas actividades "tasker.*") + overlay flotante |
  | `expo.modules.laylascreencapture` | Captura de pantalla (MediaProjection) |
  | `expo.modules.nativeutilities` | Utilidades: texto seleccionado → "recordar"/"chatear", servicio de IA en primer plano |
  | `expo.modules.lython` | Intérprete de Python embebido (libpython3.14.so) |
  | `expo.modules.rncubism` | Personajes 2D animados estilo Live2D (libcubism-jni.so) |
  | `expo.modules.rnlitert` / `litertimggen` | TensorFlow Lite: modelos de imagen en el móvil |
  | `expo.modules.rnsherpaonnx` | Voz (TTS/ASR) con sherpa-onnx |
  | `expo.modules.soundgen` | Generación de sonidos |

- **Paquetes nativos propios adicionales** (carpeta `ai/layla` del smali): `llamacpp`, `localdream` (generación de imágenes), `acestep` (música), `ctranslate2` (traducción/voz), `phonontts`, `p2p`, `rnquickjs`, `rnupscaler` (mejora de imágenes), `sdonnxstream` (Stable Diffusion por ONNX), `shellorchestrator`, `tokeniser`, `turbonativeutilities`, `vectordb` (memoria/búsqueda semántica), `neodragon`.

## Capa 2: JavaScript (React Native + Expo)

- **Expo SDK 57**, con **React Compiler** experimental activado (`"reactCompiler": true` en `app.config`).
- El código viene compilado a **bytecode Hermes** en `assets/index.android.bundle` (22,5 MB). Hermes es un motor y formato binario: el JavaScript no está como texto.
- El `app.config` es un archivo de configuración del proyecto Expo incrustado en el build; contiene permisos, plugins de build, identidad de la app y el proyecto EAS (`projectId: eb04d606-a167-4080-8c7a-0850937d9e0a`, owner `layla-network`).
- Módulos JS externos visibles en el bundle: React Navigation, Reanimated (animaciones), Gesture Handler, Screens, Safe Area, WebView, vector icons (FontAwesome, Ionicons, Material...), date-fns, mammoth (lectura de documentos Word), RSS-parser, pdfbox (PDF, vía tom_roush nativo), CodeMirror (editor de código embebido en `assets/codeditor/`), background-downloader, auth0, firebase.

## Capa 3: Motores de IA nativos (.so, ARM64)

86 librerías nativas en `lib/arm64-v8a/`. Agrupadas por función:

| Función | Librerías | Observación |
|---|---|---|
| Conversación (LLM) | `libllama.so`, `libllama-common.so`, `libggml*.so`, `libmtmd.so` | llama.cpp con backend GPU (OpenCL y Vulkan `libggml-vulkan.so` 31 MB) |
| Imágenes (Stable Diffusion) | `libonnxruntime.so` (26,7 MB), `libonnxruntimejsi.so`, `libLiteRt*.so`, `liblitertlm_jni.so` (20,8 MB) | soporte QNN (Qualcomm), modelos "-qnn" y "-htp" vistos en URLs |
| Voz: TTS/ASR | `libsherpa-onnx-jni.so`, `libphonon_engine.so`, `liblaylaaudio.so` | Piper (vits), modelos de voz descargables |
| Música | `libacestep_engine.so`, `libsoundgen-jni.so` | ACE-Step (generación de música) |
| Python embebido | `libpython3.14.so`, `liblython_jni.so`, `libcrypto_python.so`, `libssl_python.so`, `libsqlite3_python.so` | intérprete Python 3.14 completo |
| Bash/terminal | `libbashkit_core.so` (13,6 MB), `libpcre2-8.so`, `libglob.so`, `lib7-Zip-JBinding.so` | shell y compresión dentro de la app |
| Live2D | `libcubism-jni.so` | personajes animados |
| Vídeo/llamadas | `libjingle_peerconnection_so.so` (10,9 MB) | **WebRTC** — WebRTCModule en el manifest |
| UI/animaciones | `librive.so`, `librive-android.so`, `libreanimated.so`, `libworklets.so` | animaciones Rive (archivos `.riv`: annie, eleonora, butterfly) |
| Videojuego/rendimiento | `libNitroModules.so`, `libNitroPlayer.so`, `libNitroIap.so`, `libPerformanceToolkit.so` | módulos Nitro (Margelo) |
| Crashlytics NDK | `libcrashlytics*.so` | reporte de fallos (desactivado en config) |
| Varios | `libmmkv.so` (almacén clave-valor), `libavif_android.so`, `libstatic-webp.so`, `libgifimage.so`, `libimagepipeline.so` | imágenes y persistencia |

## Flujo de la app (deducido de los componentes)

1. El usuario abre la app → `MainActivity` carga el bundle Hermes → React Native monta la interfaz.
2. El chat puede ser **local** (modelos GGUF descargados de Hugging Face, ejecutados con llama.cpp en el móvil) o **en la nube** (servidor `chat.layla-cloud.com`, modelo remoto compatible con OpenAI).
3. El personaje puede "hablar" (TTS local con Piper/sherpa-onnx o ElevenLabs en la nube), escuchar (reconocimiento de voz local con sherpa-onnx o servicio del sistema) y animarse (Rive/Live2D).
4. Generación de imágenes: local con ONNX/LiteRT/QNN (modelos de CivitAI convertidos) o vía servidores.
5. El "asistente" puede registrarse como asistente de voz del sistema, verse en overlay sobre otras apps y ejecutarse desde Tasker (automatización).
6. Persistencia: **expo-sqlite** (base de datos local), MMKV, archivos en almacenamiento privado.

## Seguridad y dureza del build

- `usesCleartextTraffic="true"` en el manifest (plugin `withCleartextTraffic.js`): permite HTTP sin cifrar; típico para conectar con servidores locales (p. ej. `http://127.0.0.1:3340`, servidores locales del usuario como A1111 en `192.168.x.x`).
- `largeHeap="true"`, `allowBackup="true"`, `requestLegacyExternalStorage="true"`.
- Plugin `withAntiTamperDebugCertificate.js` en el `app.config`: **detecta firmas de certificado** — al reconstruir la APK con otra firma, la app probablemente se bloqueará o se comportará distinto. Esto es relevante para el plan de actualización.
- `CHECK_LICENSE` (Google Play License Verification) y compras con Billing v9.
