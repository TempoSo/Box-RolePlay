# Plan de actualización (propuesta — requiere tu aprobación)

> Este documento NO ejecuta cambios: es un mapa de ruta. Antes de cada paso marcado con 🔑 pediré tu autorización. Se basa en lo comprobado en el APK.

## Objetivo

Conservar la app funcionando **para uso personal**, aislándola de los servidores del desarrollador desaparecido donde sea posible, y modernizando lo imprescindible.

## Fase 0 — Preparación (sin tocar código)

1. ✅ Hecho: entorno de análisis (Java, JADX 1.5.6, APKTool 3.0.3) y descompilación completa en `jadx/` y `apktool/`.
2. Copiar el APK original a un lugar seguro como "copia maestra" (no modificarlo jamás).
3. Extraer también el bundle Hermes a un archivo aparte para trabajar con él.

## Fase 1 — Recuperar la lógica JavaScript

**Herramienta:** `hermes-dec` (open source) sobre `assets/index.android.bundle`.

1. Descompilar el bytecode Hermes a un JS aproximado.
2. Identificar: pantallas, rutas de la API de Layla, prompts del chat, configuración de suscripciones.
3. Riesgo: la descompilación es aproximada (nombres perdidos, alguna función mal reconstruida). Suficiente para **entender y parchear**, no para recompilar a JS limpio.
4. **Estrategia clave**: parchear el **bytecode Hermes directamente** (los parches sobre `index.android.bundle` no requieren recompilar el APK entero si se cambia solo ese archivo y se vuelve a firmar).

## Fase 2 — Neutralizar bloqueos de arranque 🔑 (pedir autorización)

Prioridad absoluta, porque sin esto la app no arranca tras recompilar:

1. Localizar en el código nativo (JADX) la comprobación del plugin `withAntiTamperDebugCertificate.js` (verificación de firma).
2. Localizar la verificación de licencia `CHECK_LICENSE` y cómo afecta al arranque.
3. Localizar las llamadas al login Auth0 y decidir:
   - Opción A: crear un tenant de Auth0 propio (gratis) y actualizar el dominio.
   - Opción B: parchear el login para modo "sin cuenta" (más trabajo).
4. Desactivar (parcheo) lo que dependa de servidores muertos en el arranque.

## Fase 3 — Reconstruir el APK

1. Modificar recursos/smali en la carpeta `apktool/` (nunca el original).
2. Reconstruir con APKTool y firmar con una clave propia (`apksigner`).
3. Probar en un dispositivo Android 10-16 real (o emulador ARM64).
4. Nota: el **bundle Hermes** y los **.so** no se recompilan: se copian tal cual del APK original. Solo se reensambla el contenedor.

## Fase 4 — Modernización (opcional, por partes)

| Objetivo | Acción | Dificultad |
|---|---|---|
| Quitar dependencias muertas | Parchear JS para no llamar a `api.layla-network.ai` / `chat.layla-cloud.com` si no responden | Media |
| Cuentas propias | Nuevo Auth0 + nuevo `google-services.json` propio | Media |
| Migrar motores IA | Actualizar llama.cpp / onnxruntime / piper con versiones upstream modernas recompilando los `.so` | Alta (requiere NDK y el código fuente upstream; los nombres de símbolos deben coincidir) |
| Android más moderno | La app ya apunta a targetSdk 36 (Android 16): **no es urgente**. Confirmar solo que los permisos de Android 15/16 (notificaciones, alarmas exactas, mediaProjection) se conceden bien | Baja |
| Quitar anti-tamper para desarrollo | Parchear la verificación de firma | Media |

## Fase 5 — Datos del usuario

- La app guarda datos en SQLite/MMKV en el almacenamiento privado. Si el usuario tiene la app instalada en un móvil con sus datos, extraerlos con `adb backup` o desde `/data/data/com.layla/` (con root o `run-as`) **antes** de reinstalar nada.

## Orden recomendado de trabajo

1. Extraer bundle Hermes + hermes-dec → entender la lógica. *(siguiente paso natural)*
2. Parchear arranque (firma/licencia/login) en smali.
3. Reconstruir, firmar, probar.
4. Después de eso, mejoras opcionales.

## Lo que NO haremos (sin tu aprobación explícita)

- Publicar o distribuir la app.
- Eliminar archivos del proyecto.
- Toquear el APK original.
- Iniciar recompilaciones de librerías nativas (lleva horas y requiere decidir versiones).
