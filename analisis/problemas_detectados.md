# Problemas detectados

> Lista de los obstáculos reales para recuperar, mantener o actualizar esta app. Todo lo que aparece aquí está verificado en el APK.

## 1. El código principal (JavaScript) está compilado a bytecode Hermes ❗

**Gravedad: alta.**

La lógica de la app (pantallas, reglas del chat, llamadas a la API, prompts) vive en `assets/index.android.bundle` (22,5 MB) en formato **bytecode Hermes** (cabecera `C6 1F BC 03`), no como JavaScript legible.

- **Qué significa:** con JADX y APKTool se recupera el código nativo (Java/Kotlin), pero **no** el JS de la interfaz. Las cadenas de texto dentro del bundle sí son visibles, pero la estructura del programa no.
- **Herramientas adicionales necesarias:** `hermes-dec` o `hbctool` (descompiladores de bytecode Hermes). Generan una aproximación del JS original, con pérdida de nombres de variables y a veces errores. Es trabajo manual y laborioso.

## 2. El código nativo está ofuscado con R8 ❗

**Gravedad: media-alta.**

El Java/Kotlin pasó por R8 (minificador) con renombrado de clases y métodos: hay paquetes enteros llamados `a`, `b`, `K7`, `W5`, `p8`…

- Las clases de librerías conocidas (Expo, Firebase, OkHttp) conservan nombres.
- **Las clases propias de la app** (`com.layla.*`, los módulos propios de Expo en `expo.modules.layla*` y `ai.layla.*`) están parcialmente renombradas en sus internals: se entiende *qué hace* cada módulo, pero no *cómo* con detalle.
- Los plugins del proyecto (`withR8RenameOnly.js`) confirman que la ofuscación era deliberada pero conservando estructura ("rename only"), lo que ayuda.
- **Verificado con JADX**: de 32.621 archivos Java generados, solo 130 tienen errores de descompilación y casi todos son de librerías (androidx/kotlinx), no de la app. La recuperación del código nativo es prácticamente completa.

## 3. Dependencia de servidores externos que pueden desaparecer ❗

**Gravedad: alta si el proyecto está abandonado.**

Servicios en la nube detectados:

| Servicio | Dominio | Riesgo si se apaga |
|---|---|---|
| API de herramientas | `api.layla-network.ai` | clima, lugares y Spotify dejan de funcionar |
| Chat/contenido | `chat.layla-cloud.com` | parte del chat remoto deja de responder |
| Modelos e imágenes | `r2-assets.layla-cloud.com`, `assets.layla-network.ai` | ya no se pueden descargar los modelos que la app lista |
| Login | Auth0 `dev-64egpog7ds3n4ew5.us.auth0.com` | **el inicio de sesión se rompe** (nota: el dominio es un tenant "dev", señal de que la app usaba una cuenta de desarrollo) |
| Novedades | `changelog.layla-cloud.com`, `apps.layla-cloud.com/app/updates` | avisos y actualizaciones de contenido |
| Licencias/compras | Google Play + Stripe | si la app ya no está publicada, la verificación de licencia (`CHECK_LICENSE`) puede bloquear el arranque |

**Mitigación:** gran parte de la IA funciona **en local** (llama.cpp, ONNX, Piper). Con servidores apagados, el modo local podría seguir funcionando si el login y la verificación de licencia no bloquean la app.

## 4. Anti-tamper / verificación de firma ❗

**Gravedad: alta para reconstruir la app.**

El `app.config` incluye el plugin **`withAntiTamperDebugCertificate.js`**. Esto casi siempre significa que la app comprueba **la firma digital del APK** al arrancar: si recompilamos el APK con otra clave (inevitable, porque no tenemos la original), **la app probablemente se bloqueará al abrir**.

- Hay que localizar esa comprobación en el código (JADX) y neutralizarla o parchearla durante la recompilación.
- Es el primer obstáculo a resolver en cualquier reconstrucción.

## 5. Verificación de licencia de Google Play

**Gravedad: media.**

El manifest declara `com.android.vending.CHECK_LICENSE`. Si la app ya no está publicada en Play Store, la verificación puede fallar y afectar al arranque o a las funciones de pago.

## 6. Solo soporta móviles de 64 bits

**Gravedad: baja (limitación conocida).**

Los 86 archivos `.so` solo existen en `arm64-v8a` (`buildArchs: ["arm64-v8a"]`). Teléfonos muy antiguos (ARM de 32 bits) y emuladores x86 **no funcionan**. Para probar en PC habrá que usar un emulador ARM64 (los emuladores x86 típicos no valen salvo traducción binaria).

## 7. Permisos sensibles y configuración arriesgada

- `usesCleartextTraffic="true"`: permite conexiones HTTP sin cifrar (apostado deliberadamente para soportar servidores locales del usuario, p. ej. Stable Diffusion WebUI en la red local — hay una cadena `192.168.x.x:7851` en el bundle). Reduce la seguridad, pero probablemente es una función (conectar con servidores caseros), no un fallo.
- `allowBackup="true"` + datos del chat en SQLite: los datos del usuario pueden extraerse con `adb backup` (esto es también útil para migrar datos).
- `largeHeap="true"` y `requestLegacyExternalStorage="true"`: asoman deuda técnica con Android moderno, aunque targetSdk 36 sigue aceptándolos degradados.

## 8. APK de 386 MB con 7 archivos dex

**Gravedad: práctica.**

El volumen (7 dex, ~65 MB de bytecode + 300 MB de nativos) hace que JADX tarde bastante y consuma mucha memoria. Es normal, pero hay que tener paciencia y, si hace falta, descompilar dex por dex.

## 9. Componentes potencialmente obsoletos

- **FirebaseInstanceIdReceiver / Firebase IID**: los SDK `firebase-iid-interop` y `play-services-iid` son herencia de APIs antiguas de FCM (hoy es FCM moderno; la presencia es de las librerías internas, no un problema grave).
- **requestLegacyExternalStorage**: obsoleto desde Android 11+ (se ignora en la mayoría de casos).
- **expo-updates** aparece configurado en el manifest pero con `ENABLED=false` — la app **no** tiene auto-actualización de JS (buena noticia: menos dependencia de servidores).
- Deuda menor: `supportsRtl`, múltiples permisos de badge de fabricantes (Huawei, Sony, Oppo...) son patrones antiguos de librerías de notificaciones.

## 10. Lo que NO se pudo recuperar (inventario)

| Elemento | Estado | Alternativa |
|---|---|---|
| Lógica JS de la app (pantallas, prompts, reglas) | Compilada a Hermes | hermes-dec/hbctool (aproximación) |
| Código C++ de los motores | Binario `.so` | Son proyectos de código abierto conocidos (llama.cpp, onnxruntime, piper, ACE-Step, Cubism SDK); se puede sustituir por las versiones modernas upstream |
| Claves/secretos de Auth0 y Firebase | Parcial en recursos (google-services.json compilado) | Regenerar con cuentas propias |
| Mapping de ofuscación R8 (`mapping.txt`) | **No está en el APK** | Reconstruir el significado a mano, leyendo el código |
