# 🐞 Inventario de bugs — Box RolePlay

> **Triaje 2026-11-25:** 43 cerrados (017, 029 y 045 cerrados el 25/11) · 0 abiertos · 2 herramientas abiertas (043, 044; se mantienen mientras se pule). Los cerrados se sustituyeron por una línea de referencia y su texto íntegro se conserva en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md); este documento sigue siendo el inventario de los activos; empieza por el § Orden sugerido.
>
> **Documento vivo.** Un bug = una entrada. Al cerrar uno: **no se marca ✅ dentro del texto**; se mueve el bloque entero a [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), se deja aquí solo una línea de referencia y se añade una entrada en [`CAMBIOS.md`](CAMBIOS.md) con fecha, archivos tocados y motivo.
> Origen: pruebas del Bloque 2 (2026-11-16, POCO duchamp, HyperOS 3.0.9.0, Android 15 — fingerprint del crash) y de los Bloques 4 y 5 (2026-11-17, Xiaomi 2311DRK48G / POCO duchamp).

---

## 🧭 Orden sugerido (más fácil → más difícil)

> **Regla:** trabaja **un escalón a la vez**. Si un ID ya está en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), sáltalo y avisa. Los pendientes del Escalón 0 ya llevan parche; solo falta tu re-prueba.

| Escalón | IDs | Coste | Qué es |
|---|---|---|---|
| **0 · Verificación gratis** | ✅ completo | 0 parche nuevo | 011, 015, 016, 021, 025, 026, 030 y 037 cerrados (015/021/025/026/030 el 19/11; 011/016 el 20/11; 037 el 21/11) |
| **1 · Rápidos** | ✅ completo | S | 008, 009, 010, 014, 019, 040 y 041 verificados y cerrados entre el 20 y el 21/11 |
| **2 · Medianos con tests** | ✅ completo | 0 | 041 (biblioteca de voces) y 042 (copia inteligente) verificados y cerrados el 21/11 |
| **3 · Duros / nativos** | ✅ completo | L | 017, 029 y 045 cerrados el 25/11 (Visión: el proyector BF16 era el cuello; `###`; caché de prefijo en texto) |

> **Agrupaciones:** 032–035 (escenas), 007/039 (compartir), 041 y 042 quedaron verificados el 21/11. En 041 la parte «L» aparece solo si se decide incluir grabación de audio. BUG-042 nace de la re-prueba de 038: la copia ya funciona y distingue «idéntico» (no toca) de «parecido» (conserva tu versión y añade la de la copia); re-prueba OK el 21/11.
>
> **Mejora futura (anotada, NO implementada):** al duplicar un personaje, vincular sus recuerdos a la copia cuando exista; si el personaje no cambió, añadirlos al original. **Nunca sobrescribir**: se perderían los recuerdos nuevos. Punto de entrada: `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/BackupRepository.kt`.

> **Decisión de formato (21/11):** `[...]` se reserva para **narración y acciones**. El encabezado `Narrador:` se mantiene porque el filtro de escenas identifica hablantes por `Nombre:`; el narrador escribe `Narrador: [texto]` y un personaje puede usar `[acción]` dentro de su bloque. No se aceptan bloques `[...]` sueltos sin encabezado (se perdería el filtrado). Pendiente de implementar en el prompt de escena.

---

## P0 — Críticos (bloquean el roleplay)

> **BUG-001** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-001).

> **BUG-002** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-002).

> **BUG-030** — cerrado y verificado el 2026-11-19. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-030).

## P1 — Correctitud del roleplay

> **BUG-013** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-013).

> **BUG-014** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-014).

> **BUG-003** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-003).

> **BUG-004** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-004).

> **BUG-005** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-005).

> **BUG-006** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-006).

> **BUG-015** — cerrado y verificado el 2026-11-19 (2.1b: calidad en GGUF y LiteRT). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-015). El recorte de respuestas largas en GGUF es otro bug: **BUG-029**.

> **BUG-016** — cerrado y verificado el 2026-11-20 (4.2c: GGUF→GGUF y LiteRT→GGUF sin «El modelo sigue cargando»). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-016).

> **BUG-021** — cerrado y verificado el 2026-11-19 (5.4b: rotar con y sin generación). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-021).

> **BUG-027** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-027).

> **BUG-029** — cerrado y verificado el 2026-11-25. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-029).

> **BUG-031** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-031).

## P2 — UX

> **BUG-007** — cerrado y verificado el 2026-11-21 (se comparte `Nombre.json` con `FileProvider`; el texto sigue disponible). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-007).

> **BUG-008** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-008).

> **BUG-009** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-009).

> **BUG-010** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-010).

> **BUG-011** — cerrado y verificado el 2026-11-20 (ficha y tarjeta con la persona activa). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-011).

> **BUG-018** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-018).

> **BUG-020** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-020).

> **BUG-028** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-028).

> **BUG-022** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-022).

> **BUG-023** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-023).

> **BUG-025** — cerrado y verificado el 2026-11-19 (log 22:02:37: `Cleaning up model` + `Clean up done`, sin `ClassCastException`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-025).

> **BUG-026** — cerrado y verificado el 2026-11-19 (generación de voz con Pocket sin `NoSuchMethodError`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-026).

## P3 — Rendimiento

> **BUG-012** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-012).

> **BUG-017** — cerrado y verificado el 2026-11-25. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-017).

> **BUG-045** — cerrado y verificado el 2026-11-25. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-045).

> **BUG-019** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-019).

> **BUG-024** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-024).

## 🧩 Tanda final — Roleplay de grupo, copias y compartir *(documentado 2026-11-19, pendiente; hacer DESPUÉS de los bugs menores)*

> Origen: re-pruebas del humano del 19/11 (2.4, 2.4b, 4.4b, 5.6b) con capturas del chat de escena (`AI Chat`, LiteRT) y de la pantalla de grupo "Political Pundits". Referencia de diseño: [chats de grupo de SillyTavern](https://docs.sillytavern.app/usage/core-concepts/groupchats).

**Modelo objetivo del chat de escena (lo que el humano quiere):**
1. **Lo que escribe el usuario es solo contexto.** Enviar un texto lo guarda como mensaje (de su persona, de un personaje o del Narrador) y **el modelo NO responde**. Es como si el modelo hubiera escrito ese mensaje y se archivara como contexto.
2. **El modelo solo habla cuando el usuario lo pide:**
   - **🎲 Aleatorio:** elige un personaje al azar y habla **solo por él**, **añadiendo** un mensaje nuevo al hilo (nunca borra ni sustituye el anterior).
   - **Enviar con el campo vacío + personaje seleccionado:** el modelo escribe **por ese personaje** (el usuario decide quién habla, sin azar). Equivale al "forzar respuesta de un miembro" de SillyTavern (icono de burbuja junto al miembro).
3. **El usuario puede interpretar a cualquiera:** su persona, un personaje existente o el Narrador. Si interpreta a KIP, el siguiente turno (Aleatorio) no debe ser KIP.
4. **La persona del usuario se importa como un personaje más** en la escena: debe poder **quitarse** (papelera) cuando el usuario va a interpretar a un personaje ya creado; si se quita, desaparece de la barra de hablantes.
5. **En roleplay solo se usan `nombre`, `descripción` y `personalidad`** de cada ficha (no escenario, saludo, ejemplos de mensajes ni system prompt de la ficha). Así se ahorra contexto y el hilo se mantiene mejor. Distinto del chat 1 a 1, que usa la ficha completa.

**Cómo lo hace SillyTavern (verificado en su documentación):** estrategias de orden de respuesta *Manual* (solo responde el miembro elegido; los mensajes del usuario **nunca** disparan respuesta), *Natural* (menciones → "talkativeness" → un miembro al azar), *List* y *Pooled* (azar entre los que no han hablado desde el último mensaje del usuario). Con entrada vacía en modo manual responde un miembro no silenciado al azar; el botón junto a cada miembro fuerza que hable ese personaje aunque esté silenciado. El historial es **compartido** y por defecto se incluye solo la ficha del que habla (*Swap*); *Join* mezcla todas las fichas y la propia documentación avisa de que puede confundir a los personajes. Campos que une *Join*: descripción, escenario, personalidad, ejemplos. → Para Box RolePlay encaja el modo **Manual + Swap**, con nombre/descripción/personalidad.

> **BUG-032** — cerrado y verificado el 2026-11-21 (cada turno de Aleatorio añade una burbuja nueva). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-032).

> **BUG-033** — cerrado y verificado el 2026-11-21 (enviar en escena guarda contexto y no genera; interruptor por escena). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-033).

> **BUG-034** — cerrado y verificado el 2026-11-21 (enviar vacío con hablante elegido). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-034).

> **BUG-035** — cerrado y verificado el 2026-11-21 (persona opcional y barra que respeta «Incluir narrador»). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-035).

> **BUG-036** — **descartado el 2026-11-21 (falso positivo):** la captura de `[The studio lights…]` era de **Layla**, no de Box, y el prompt de escena ya usaba solo nombre/descripción/personalidad. El único cambio de este supuesto bug era el test `sceneUsesOnlyNameDescriptionPersonality`, que se ha retirado. Decisión: `[...]` se usará para narración/acciones, no para recortar campos de la ficha (ver «Decisión de formato» arriba).

> **BUG-037** — cerrado y verificado el 2026-11-21 (filtro de escenas: alias `Narrador` reconocido). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-037).

> **BUG-038** — cerrado y verificado el 2026-11-21 en su alcance base (copia v2 con selector y fusión por id). La afinación de identidad pasa a **BUG-042**. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-038).

> **BUG-042** — cerrado y verificado el 2026-11-21 (la copia conserva tu edición y duplica solo si cambia el personaje). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-042).

> **BUG-039** — cerrado y verificado el 2026-11-21 (mismo fix que BUG-007: `.json` real con `FileProvider`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-039).

> **BUG-040** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-040).

> **BUG-041** — cerrado y verificado el 2026-11-21 (biblioteca de voces: «todo funciona»). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-041).


### BUG-043 · 🟡 Logs: se pierden al reiniciarse la app (sin diagnóstico de cierres)
**Esfuerzo:** M · **Escalón:** 2

- **Síntoma (humano, 2026-11-22):** el log exportado solo cubre la sesión actual; tras un cierre no hay rastro de lo anterior.
- **Causa:** logcat sin permisos de sistema es solo del proceso propio y se reinicia con él. Los logs de la app de referencia se guardan en archivos.
- **Parche aplicado (2026-11-22, pendiente de re-prueba):** log persistente por sesión, ver CAMBIOS. Re-prueba: (1) usar la app un rato, cerrarla deslizando y reabrirla; (2) Ajustes > exportar log: debe traer «Sesión actual» y «Sesión anterior 1» con líneas de memoria (MEM) y hitos (GGUF); (3) la cabecera de la sesión nueva dice el motivo de la salida anterior; (4) comprobar que no hay ruido de MIUI y que el tamaño del reporte es razonable.
- **Re-prueba 22/11 (log 16:18): OK.** El reporte trae «Sesión actual» + «Sesión anterior 1», el motivo de la salida anterior (`FORCE STOP` de MIUI), `[MEM]` cada 20 s, hitos `[GGUF]` y el ruido de MIUI fuera. Queda tu OK para moverlo a `BUGS_CERRADOS.md`.
- **Archivos:** `data/characters/PersistentLog.kt`, `LogLineFilter.kt`, `LogExporter.kt`, `GalleryApplication.kt`, `engine/LlamaCppEngine.kt`.

### BUG-044 · 🟡 Modo diagnóstico para cazar BUG-029 y BUG-017 (temporal)
**Esfuerzo:** M · **Escalón:** herramienta · Temporal: apagar/quitar cuando 029 y 017 estén cerrados

- **Qué es:** registro por generación (prompt, respuesta cruda, tokens/s, motivo de parada), ver CAMBIOS 2026-11-22.
- **Parche aplicado (2026-11-22, pendiente de re-prueba):** (1) tras el build, Ajustes > «Modo diagnóstico» aparece encendido; (2) conversa con un GGUF y un LiteRT; (3) «Exportar logs»: al final debe haber «Modo diagnóstico» con una entrada por respuesta; (4) provoca una respuesta cortada y mira «MOTIVO DE PARADA»; (5) con el modo apagado no debe guardar nada nuevo. Reenvía ese reporte para atacar 029 y 017.
- **Re-prueba 22/11 (log 16:18): funciona.** Cuatro generaciones registradas con parámetros, prompt completo, respuesta cruda, t/s y motivo de parada. **Limitación encontrada:** el motivo cae en el cajón «fin natural del modelo (EOS)» tanto si el EOG lo emite el modelo como si lo provoca un stop string del nativo, porque `LLMInference.cpp` devuelve un `[EOG]` genérico en los dos casos. Para cerrar BUG-029 hace falta exponer cuál de los dos fue.
- **Archivos:** `data/characters/DiagLog.kt`, `engine/LlamaCppEngine.kt`, `ui/llmchat/LlmChatViewModel.kt`, `LogExporter.kt`, `BoxSettingsScreen.kt`.

## ✅ Lo que ya funciona (Bloque 2, 2026-11-16)

Ficha de personaje (stats, descripción, escenario) · **chat 1 a 1: persona usada, personaje correcto, sin hablar por ti (GGUF y LiteRT, re-prueba OK)** · grupos con chips y persistencia · escenas: crear/guardar/chatear con varios personajes y narrador · barra de escena SOLO en chats de escena (correcto) · memoria: botones, guardado y borrado de recuerdos · cajón por swipe con chats agrupados por personaje · contador de rendimiento en escenas (743 tok @ 5 t/s, gen 3.7 t/s).

## ✅ Lo que ya funciona (Bloque 4 — descargas y modelos, 2026-11-17 · Xiaomi)

Navegador HF: búsqueda, listado con holgura de RAM y errores limpios ("Hugging Face respondió 401", "Este repositorio no tiene archivos .gguf ni .litertlm"); pegar enlace lleva directo al selector de archivos (colores del label → BUG-020) · descarga y cambio de modelo GGUF/LiteRT chateando tras el cambio (carrera del primer mensaje → BUG-016) · visión mmproj funcional, muy lenta (BUG-017) · motor guardado por personaje (GGUF/LiteRT, visión, persona, muestreo) y **fallback al motor general de roleplay al borrar el modelo ligado** (presets sin rendimiento → BUG-018) · benchmark de velocidad en GGUF y LiteRT (métrica LiteRT no comparable → BUG-019). Esta tanda cubre el resto del Bloque 1 (LiteRT).

## ✅ Lo que ya funciona (Bloque 5 — robustez y regresión, 2026-11-17 · POCO)

Segundo plano 10+ min: GGUF y LiteRT aguantan otra app y pantalla apagada y siguen al volver · LiteRT termina los turnos cortos aunque apagues la pantalla · presets de muestreo: guardar con nombre, cargar y borrar con ✕ (falta un pequeño retoque gráfico) · apps sin modelo: "Texto a voz" y el resto abren su UI sin cargar nada · exportación de copia de seguridad .zip y de logs funcionan (importación y crashes nativos → BUG-023/BUG-022) · turno GGUF de referencia: **carga 3.5 s · prompt 105 tok @ 7.9 t/s · gen 210 tok @ 5.06 t/s**; LiteRT por contador: **8.6 frag/s · 1er token 3.1 s**.
