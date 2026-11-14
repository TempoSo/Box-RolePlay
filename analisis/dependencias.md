# Dependencias y librerías

> Lista de todo lo que la app usa y que no fue escrito por su desarrollador. Sacado del manifest, del `app.config`, de los `.so`, del smali y de cadenas dentro del bundle JS.

## Framework principal

| Componente | Versión | Evidencia |
|---|---|---|
| React Native | **0.81.x (SDK 57 de Expo)** | Expo SDK 57 en `app.config`; `libreactnative.so` |
| Expo SDK | **57.0.0** | `app.config` |
| Kotlin | **2.3.20** | `kotlin-tooling-metadata.json` |
| Gradle | **9.3.1** | `kotlin-tooling-metadata.json` |
| Java (compilación) | 17 | `kotlin-tooling-metadata.json` |
| Hermes | motor JS con bytecode compilado | cabecera del bundle |
| Nitro Modules (Margelo) | presente | `libNitroModules.so`, `libNitroIap.so` |

## Librerías JavaScript detectadas (evidencia en el bundle)

- **Navegación/UI:** React Navigation, react-native-screens, react-native-safe-area-context, react-native-gesture-handler, react-native-reanimated, react-native-worklets, react-native-svg, RNCSlider, rnpicker.
- **Contenido:** react-native-webview, mammoth (Word), rss-parser, date-fns, @actualwave/react-native-codeditor (editor de código con CodeMirror en `assets/codeditor/`).
- **Medios:** expo-video, expo-audio, expo-image, react-native-image-colors, @kesha-antonov/react-native-background-downloader.
- **IA:** onnxruntime-react-native, react-native-auth0, @react-native-firebase/app + crashlytics + messaging.
- **Utilidades:** react-native-permissions, expo-share-intent, process-phoenix (reiniciar app), react-native-static-server (servidor HTTP local, birdofpreyru), escodegen/estraverse (generación de código JS).

## Librerías nativas Android (del manifest)

| Librería | Uso |
|---|---|
| **Firebase** (messaging, crashlytics NDK, installations, sessions, datatransport) | notificaciones push y telemetría |
| **Google Play Billing 9.1.0** | compras dentro de la app |
| **Play Services**: auth, cast, location, tasks, basement, fido, iid, mlkit barcode | varios |
| **ML Kit** barcode-scanning | lector de códigos de barras |
| **Auth0** (com.auth0.android.provider) | inicio de sesión OAuth |
| **Media3 / MediaSession** (androidx.media3) | reproductor de audio con sesión de medios |
| **CameraX** (androidx.camera) | cámara |
| **Room** (androidx.room) + sqlite | bases de datos |
| **WorkManager** (androidx.work) | tareas en segundo plano |
| **Credentials** (androidx.credentials) | biometría/credenciales |
| **Coil/Glide** (bumptech.glide okhttp module) | carga de imágenes |
| **CanHub Cropper** | recortar imágenes |
| **Tasker plugin library** (com.joaomgcd.taskerpluginlibrary) | integración con Tasker |
| **WebRTC** (com.oney.WebRTCModule + libjingle) | llamadas de vídeo/áudio P2P |
| **PDFBox** (com.tom_roush) | leer PDFs |
| **PDF de imágenes** | sevenzipjbinding (abrir archivos 7z de modelos) |
| **MMKV** | almacenamiento clave-valor rápido |
| **Rive** | animaciones (archivos `.riv`) |
| **ExoPlayer/Video** | expo.modules.video FullscreenPlayerActivity |
| **DataStore** | preferencias |

## Motores IA nativos (lib/arm64-v8a)

| Motor | Archivos | Función |
|---|---|---|
| llama.cpp | libllama, libllama-common, libggml* (CPU armv8.x variantes, OpenCL, Vulkan), libmtmd | LLM local multimodal |
| ONNX Runtime | libonnxruntime, libonnxruntimejsi | Stable Diffusion y modelos de voz |
| LiteRT (TF Lite) | libLiteRt, libLiteRtClGlAccelerator, liblitert_jni, liblitertlm_jni, liblitertimggen-jni | generación de imágenes local |
| sherpa-onnx | libsherpa-onnx-jni | TTS/ASR (Piper vits, parakeet, l2arctic…) |
| ACE-Step | libacestep_engine | música IA |
| Phonon | libphonon_engine | TTS propio |
| CTranslate2 | (paquete ai.layla.ctranslate2) | traducción/voz |
| Python 3.14 | libpython3.14, libcrypto/ssl/sqlite_python, liblython_* | scripting Python en la app |
| BashKit | libbashkit_core, libpcre2-8, libglob | shell de Linux en la app |
| Cubism (Live2D) | libcubism-jni | personajes animados |
| Rive | librive, librive-android | animaciones |
| WebRTC | libjingle_peerconnection_so | P2P audio/vídeo |
| P2P | liblayla_p2p_core (8 MB) | conexión entre dispositivos (posible compartir modelos) |

## Modelos de IA descargables (URLs en el bundle)

- **LLM:** Gemma 4 E2B/E4B (ggml-org), Gemma-2-2b, Gemmasutra-Mini-2B, Impish LLAMA 3B, InternVL3-1B, SmolVLM2-500M (visión).
- **Voz:** Piper (vits) en_US/en_GB (ljspeech, l2arctic, joe, alba, jenny, northern english…), parakeet, arctic-medium.
- **Imagen:** decenas de modelos de CivitAI (SD1.5/SDXL/Pony/Flux), versiones "-qnn" y "-htp" para procesadores Qualcomm/mediatek.

## Resumen de tamaño de las nativas

~300 MB de las nativas son motores de IA. La app **solo incluye binarios ARM64** (`buildArchs: ["arm64-v8a"]`): los móviles de 32 bits no la pueden usar.
