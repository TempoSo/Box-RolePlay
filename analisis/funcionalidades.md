# Funcionalidades de la aplicación

> Lo que la app puede hacer, deducido del manifest, de los componentes registrados y de las cadenas de texto del bundle. Marcaré con ⚠️ lo que dependa de servidores externos.

## Funciones principales

### 1. Chat con personajes de IA
- Personajes virtuales con los que conversar. El motor puede ser:
  - **Local**: modelos GGUF (Gemma 4 E2B/E4B, Gemma-2-2b, Gemmasutra, Impish LLAMA 3B…) descargados de Hugging Face y ejecutados con llama.cpp en el propio móvil (con aceleración GPU vía Vulkan/OpenCL).
  - **Remoto**: conectado a un servidor compatible con la API de OpenAI (`api.openai.com/v1/chat/completions` y rutas propias de Layla en `chat.layla-cloud.com`). El usuario puede configurar claves API propias (hay cadenas tipo "myOpenAI", "api-key").
- El asistente de IA tiene herramientas tipo **MCP** (Model Context Protocol): clima (`/mcp/weather-forecast`), lugares (`/mcp/search-places`), Spotify (`/mcp/search-spotify`).
- Sistema de **memoria**: paquete `vectordb` (base de datos vectorial para recuerdos semánticos) y funciones "remember text", "get memories" (visibles en las actividades de Tasker).

### 2. Generación de imágenes
- **Local**: Stable Diffusion con ONNX Runtime / LiteRT, con modelos convertidos para procesadores **Qualcomm (QNN/HTP)** y **mediatek (APU)**. Modelos descargables desde CivitAI y R2 de Layla (`r2-assets.layla-cloud.com`). Paquetes nativos: `localdream`, `sdonnxstream`, `rnupscaler` (re-escalado/mejora).
- Acepta modelos SD1.5 safetensors importados por el usuario (cadena "how-to-import-sd1-5-safetensor-models-from-civit-ai-into-layla").
- Catálogo con modelos tipo Flux (flux2-klein-4b, flux2-klein-9b), Qwen-Image, Moss-image, etc.

### 3. Voz del personaje (TTS)
- **Local**: Piper (vits) vía sherpa-onnx con muchas voces (ljspeech, joe, alba, jenny, arctic…), motor Phonon propio, CTranslate2, motor de voz "PocketTTS".
- **Nube**: ElevenLabs (`api.elevenlabs.io`, streaming con sample rate configurable).
- El usuario puede elegir voces y generar archivos de audio (actividad Tasker "GenerateSpeechFile").

### 4. Escucha del usuario (ASR)
- Reconocimiento de voz local (sherpa-onnx: modelos parakeet, l2arctic) o usando el servicio del sistema Android (Google TTS/Speech, Sayboard — paquetes declarados en `<queries>`).

### 5. Música generada por IA
- Motor **ACE-Step** (`libacestep_engine.so`, rutas `ACE_STEP_GEN_PATH`, actividad Tasker "GenerateMusic", dominio `music-api.layla-cloud.com` ⚠️).
- Generación de sonidos cortos (módulo soundgen).

### 6. Personajes animados
- **Rive** (archivos `.riv`: annie, eleonora, butterfly) y **Live2D Cubism** (libcubism-jni). El personaje puede mostrar emociones y movimiento.

### 7. Asistente del sistema ("Layla Assistant")
- Registro como **asistente de voz de Android** (`VoiceInteractionService` + `RecognitionService`): podría sustituir a Google Assistant si el usuario lo configura.
- **Overlay flotante** sobre otras apps (`CompanionOverlayService`, SYSTEM_ALERT_WINDOW).
- Puede **capturar la pantalla** (`ScreenCaptureService`, MediaProjection, permiso DETECT_SCREEN_CAPTURE) para que la IA "vea" lo que pasa en el móvil.
- Actividades de "texto seleccionado": al seleccionar texto en cualquier app, ofrecer "Recordar" o "Chatear" (PROCESS_TEXT).

### 8. Automatización con Tasker
- Más de 20 actividades para **Tasker** (app de automatización): generar imagen, pedir a Layla, hablar texto, recordar datos, consultar historial de chat, listar personajes/voces, generar música, analizar sentimiento, programar mensajes del personaje, peticiones API crudas, etc.
- Servicios de automatización propios (AutomationService, alarmas exactas con receiver de reinicio BOOT_COMPLETED).

### 9. Terminal / scripting
- **BashKit** (libbashkit_core, 13,6 MB): shell real dentro de la app.
- **Python 3.14 embebido** (módulo lython): ejecutar código Python en el móvil.
- **Editor de código** con CodeMirror (assets/codeditor) — el usuario puede editar scripts.

### 10. Descarga y gestión de modelos
- Descargas en segundo plano (ResumableDownloadService, WorkManager), pausables.
- Importación de archivos comprimidos 7z (7-Zip-JBinding) — típico para paquetes de modelos.

### 11. Llamadas en tiempo real (WebRTC)
- `WebRTCModule` + `libjingle_peerconnection_so` + servicio MediaProjection: capacidad de audio/vídeo P2P en tiempo real (llamadas con el personaje o P2P entre usuarios; el paquete `liblayla_p2p_core` apunta a compartir/comunicación directa).

### 12. Cuenta, compras y pagos
- **Auth0** para autenticación (⚠️ el dominio registrado es un entorno "dev-…": si el desarrollador lo apaga, el login se rompe).
- Compras Google Play (Billing 9.1.0, verificación de licencia CHECK_LICENSE).
- **Stripe** (varios enlaces `buy.stripe.com`) y **Google Web Payments** (intents org.chromium.intent.action.PAY).
- Productos detectados en cadenas: `layla.products.gold`, `layla.products.silver` (suscripciones/niveles).

### 13. Utilidades de sistema
- Notificaciones (Expo + FCM), badges en icono (multitud de fabricantes), calendario (leer/escribir), contactos (leer), ubicación, cámara, micrófono, biometría (desbloqueo con huella/cara), Tasker, duplicado de pantallas.
- **WebRTC + Cast**: soporte para reproducir en Chromecast (NitroCastOptionsProvider).
- Lectura de códigos de barras (ML Kit) — probablemente para vincular dispositivos o pagos.

## Funciones que no pude confirmar

- Detalles de la suscripción (precios, niveles exactos) — viven en el bundle Hermes y en el servidor.
- Comportamiento exacto del P2P (liblayla_p2p_core) — está en binario.
- Contenido de los prompts de IA (la "personalidad" del personaje) — dentro del bytecode Hermes; puede extraerse con herramientas especializadas si es necesario.
