# Resumen general del análisis

> Fichas de datos sacadas directamente del APK (`original/obsoleto.apk`) y de sus archivos internos. Todo lo escrito aquí está comprobado; nada es suposición.

## Ficha del APK

| Dato | Valor | ¿De dónde sale? |
|---|---|---|
| Nombre de la app | **Layla** | `assets/app.config` |
| Paquete (identificador) | `com.layla` | `AndroidManifest.xml` |
| Versión | **7.7.0.1-direct** (versionCode 309) | `apktool/apktool.yml` |
| Versión interna del proyecto | 7.7.0 | `assets/app.config` |
| Tamaño del APK | 386,5 MB | sistema de archivos |
| Android mínimo | **9 (API 28)** | `apktool.yml` |
| Android objetivo | **16 (API 36)** | `apktool.yml` |
| Compilado con SDK | 36 (Android 16) | `AndroidManifest.xml` |
| Plataformas | Android e iOS | `assets/app.config` |
| Fecha del análisis | 13/11/2026 | — |

## ¿Qué es esta aplicación?

Layla es una **app de compañía de IA (AI companion)**: chat con personajes virtuales generados por inteligencia artificial, generación de imágenes y de música/voz, y un "asistente" que puede funcionar como asistente de voz del sistema (como Siri o Google Assistant, pero propio).

Lo deducimos de:

- El manifest registra un **servicio de asistente de voz** (`LaylaAssistantService` con `VoiceInteractionService`), servicios de reconocimiento de voz y una actividad de asistente flotante.
- La configuración (`app.config`) lista módulos propios para chat, audio, captura de pantalla y "companion".
- El bundle JavaScript contiene rutas como `chat.layla-cloud.com`, galerías de modelos de imagen (Stable Diffusion), motores de música (ACE-Step), voces (ElevenLabs, Piper, sherpa-onnx) y modelos de conversación (llama.cpp/Gemma).
- Los dominios propios: `layla-cloud.com`, `layla-network.ai` (API de backend), `chat.layla-cloud.com` (servidor de chat estático).

## ¿Cómo está construida? (explicación sencilla)

Una app Android se puede hacer de varias maneras. Esta usa un enfoque "híbrido con capa nativa":

1. **La interfaz y la lógica principal están en JavaScript/TypeScript** con el framework **React Native** (y su plataforma **Expo**, versión SDK 57). Ese código viene **compilado a "bytecode Hermes"** dentro del archivo `assets/index.android.bundle` (22,5 MB). Es decir: no es JavaScript legible; está en un formato binario que la app ejecuta con el motor Hermes de Facebook.
2. **Una capa nativa Kotlin/Java** se encarga de la plataforma Android: actividades, servicios, notificaciones, permisos.
3. **Una capa de IA en C++** (archivos `.so` nativos) ejecuta los modelos en el móvil: llama.cpp (modelos GGUF), ONNX Runtime, LiteRT (TensorFlow Lite), sherpa-onnx (voz), ACE-Step (música) y hasta un **intérprete de Python 3.14 embebido** (`libpython3.14.so`, módulo "lython").

## La app está ofuscada

El código nativo de Java/Kotlin pasó por **R8 (minificación)**: los nombres de clases y métodos se cambiaron por letras (`a`, `b`, `K7`, `W5`...). El propio `app.config` confirma que usaban el plugin `withR8RenameOnly.js`. Esto significa que:

- El código de la app recuperable con JADX tendrá nombres incomprensibles en gran parte.
- Los nombres de las clases de **librerías bien conocidas** (Expo, Firebase, OkHttp, Kotlin) sí se conservan, porque las librerías tienen reglas que impiden renombrarlas.

## Componentes principales detectados

- **1 actividad principal**: `com.layla.MainActivity` (pantalla única tipo React Native).
- **Más de 40 actividades** adicionales, casi todas generadas por librerías y por la integración con **Tasker** (automatización).
- **Servicios** de reproducción de audio, descargas en segundo plano, asistente de voz, captura de pantalla, ubicación, automatización.
- **~20 módulos Expo nativos**, de los cuales **al menos 10 son propios de Layla** (desarrollados a medida).
- **86 archivos `.so`** de código nativo para ARM64 (`arm64-v8a`): la app **solo soporta procesadores de 64 bits** (el `app.config` lo confirma: `buildArchs: ["arm64-v8a"]`).

## Servicios externos que usa

| Servicio | Dominio | Uso probable |
|---|---|---|
| API propia de Layla | `api.layla-network.ai` | herramientas MCP: clima, búsqueda de lugares, Spotify |
| Servidor de chat | `chat.layla-cloud.com` | contenido/estático del chat |
| Almacén de archivos | `r2-assets.layla-cloud.com` (Cloudflare R2) | modelos de IA e imágenes |
| Autenticación | **Auth0** (`dev-64egpog7ds3n4ew5.us.auth0.com`) | inicio de sesión |
| Notificaciones | **Firebase Cloud Messaging** | notificaciones push |
| Pagos | **Google Play Billing 9.1.0** y **Stripe** | compras dentro de la app y suscripciones |
| OpenAI | `api.openai.com` | API compatible con OpenAI (el usuario puede conectar claves) |
| ElevenLabs | `api.elevenlabs.io` | voces de IA |
| Hugging Face | `huggingface.co` | descarga de modelos GGUF y de imagen |
| CivitAI | `civitai.com` | descarga de modelos de generación de imágenes |
| ML Kit (Google) | servicios de Play | lectura de códigos de barras |

**Punto crítico para el futuro:** el backend (`layla-network.ai` / `layla-cloud.com`) puede dejar de funcionar en cualquier momento si el desarrollador apaga los servidores. Varios componentes dependen de él.

## Estado de la recuperación del código

| Parte | ¿Recuperada? | Estado |
|---|---|---|
| Recursos, imágenes, fuentes | ✅ Completa | vía APKTool (`apktool/`) |
| AndroidManifest.xml | ✅ Completa | vía APKTool |
| Código nativo Java/Kotlin | ✅ Recuperada con ofuscación | **26.108 clases → 32.621 archivos Java** en `jadx/sources/`; legible pero con nombres ofuscados por R8 |
| Código JavaScript de la app | ❌ Compilado | es **bytecode Hermes**, requiere herramientas específicas (`hermes-dec`, `hbctool`) para reconstruir una versión aproximada |
| Código C++ (motores IA) | ❌ Binario | solo análisis de símbolos/strings; los `.so` son bibliotecas de código abierto conocidas en su mayoría |

### Calidad de la descompilación JADX (verificada)

- 130 archivos con error de descompilación de 32.621 (**~99,6% recuperado**).
- Los errores se concentran en **librerías**, no en el código de la app: `androidx` (72), `kotlinx` (21), `com` (12), `expo` (9), `org` (6).
- Las clases clave de la app (`com.layla.MainApplication`, `MainActivity`, los 14 paquetes nativos en `ai.layla.*`) **sí se descompilaron** y son legibles.
- La lista de errores está guardada en `analisis/errores_jadx.txt`.
