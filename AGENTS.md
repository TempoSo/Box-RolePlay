# AGENTS.md — Instrucciones para agentes IA (OpenCode, Claude, otros)

Proyecto: **Box RolePlay** — fork de [Box](https://github.com/jegly/Box) (base **1.0.1**) reorientado a roleplay con IA 100 % local en Android. Rama activa: `fase1-rendimiento`.

## Reglas obligatorias

1. **Registro de cambios:** tras cualquier cambio de código o documentación, añade una entrada **arriba** en [`analisis/CAMBIOS.md`](analisis/CAMBIOS.md) con: fecha, qué se cambió, **rutas exactas de los archivos** y motivo. No borres entradas antiguas.
2. **Idioma:** commits, comentarios nuevos y documentación en **español**.
3. **No tocar sin preguntar:** `analisis/PRUEBAS.md` (checklist del humano) y los submódulos (`Android/src/llama.cpp`, `stable-diffusion.cpp`, `whisper.cpp`).
4. **Realidad primero:** antes de documentar una función, verifica que existe en el código. La base es Box 1.0.1 — las funciones de Box 3.x (Box Assist, Nano Hub, upscale, MCP, etc.) **no están aquí** aunque aparezcan en históricos upstream.

## Documentos vivos (leer al empezar una sesión)

| Archivo | Contenido |
|---|---|
| [`analisis/CAMBIOS.md`](analisis/CAMBIOS.md) | Registro de cambios + mapa de código + contexto del proyecto |
| [`analisis/PLAN_PRUEBAS.md`](analisis/PLAN_PRUEBAS.md) | Plan de pruebas priorizado con resultados reales en dispositivo |
| [`analisis/BUGS.md`](analisis/BUGS.md) | Inventario de bugs priorizados (P0–P3) con stacks, repros y archivos afectados |
| [`analisis/MEMORIA.md`](analisis/MEMORIA.md) | Análisis de diseño: memoria de Layla vs Box y modificaciones propuestas |
| [`analisis/PRUEBAS.md`](analisis/PRUEBAS.md) | Checklist original completa por fase |

## Compilación

```bash
git clone --recurse-submodules -b fase1-rendimiento https://github.com/TempoSo/Box-RolePlay.git
cd Box-RolePlay/Android && ./gradlew :app:assembleDebug
```

Si falla por sherpa-onnx: el AAR va en `Android/src/app/libs/sherpa-onnx.aar` (ver `Android/src/app/libs/LEEME.txt`).

## Dispositivo de prueba

Xiaomi 2311DRK48G · Android 16 (SDK 36) · arm64. Logs de la app: Ajustes de Roleplay → "Exportar logs"; logcat filtrado por `AGLlamaPerf`. Exportar logs **inmediatamente** tras reabrir la app si hubo crash.
