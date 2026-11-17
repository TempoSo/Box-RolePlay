# 🧠 Memoria a largo plazo — Layla vs Box RolePlay (análisis de diseño)

> **Documento vivo de diseño.** No describe bugs: describe cómo funciona la memoria de Layla (referencia), cómo funciona la de Box hoy (verificado en código) y las modificaciones propuestas para acercarla a Layla. Actualizado: 2026-11-16.

---

## 1. Cómo funciona la memoria de Layla (referencia)

Fuentes: blog oficial de Layla Network (Layla 7, jul 2026), changelog v4.9 y los dumps de prompt del usuario.

- **Ingesta en tiempo real, durante la conversación.** Desde Layla 7 las memorias se procesan al vuelo mientras se chatea (antes se hacía en segundo plano, tarde y lento).
- **Procesador por defecto: un modelo BART entrenado a medida** que genera resúmenes y un **grafo de conocimiento** a la vez. Está entrenado con datos de roleplay destilados de modelos uncensored (Cyndonia, Bloodmoon) y es tan rápido que no estorba al chat (~5 s por ingesta desde la v4.9, que sustituyó el modelo LTM antiguo de 1–5 min).
- **Alternativa con LLM** disponible, y el SDK permite que una mini-app sustituya el procesador por completo.
- **Recall refinado:** top-memorias ponderadas por **frecuencia + recencia**, y evita sacar memorias creadas en la **misma sesión** para prevenir bucles de retroalimentación.
- **Estructura temporal:** memorias "temporales" agrupadas en clústeres y visualizadas como grafo/nodos con enlaces de tiempo.
- **Coherencia con el historial:** borrar o regenerar un mensaje **elimina o actualiza** la memoria ligada a él.
- **Inyección al prompt:** en el system aparece el bloque `remembers previously:` con el contenido resumido (siempre poblado si ya hubo conversaciones previas), seguido de la identidad ("The User is X. You ARE …").

**Clave del diseño Layla:** la memoria "básica" no es opcional ni manual — es un resumen continuo que se mantiene solo mientras hablas, generado por un modelo pequeño y rápido aparte del LLM principal.

## 2. Cómo funciona la memoria de Box hoy (verificado en código)

Pipeline en tres piezas, todas en `data/characters/`:

| Pieza | Archivo | Qué hace |
|---|---|---|
| Notas automáticas | `AutoMemory.kt` | **Sin modelo**: de cada intercambio extrae las primeras frases clave literales (máx. ~150–200 car.) y las guarda como recuerdo **sin procesar**. Instantáneo, no inventa nada. |
| Resumen de ventana | `MemorySummarizer.kt` | **Fase 4, sin modelo**: cuando los mensajes salen de la ventana de contexto, los resume de forma extractiva y lo añade al prompt del sistema (`withSummary()`). Vive solo durante esa conversación. |
| Absorción | `MemoryAbsorber.kt` | **Con modelo, manual** (botón "Absorber clústeres"): junta las notas sin procesar y genera UN recuerdo procesado. Hoy crashea con GGUF / da timeout con LiteRT (**BUG-002**). |
| Inyección | `MemoryPromptBuilder.kt` → `CharacterPromptBuilder.kt` | Arma el bloque **"{char} remembers previously:"** con los recuerdos **procesados** más recientes y su antigüedad ("[2 hours ago] …"). Estilo Layla, pero solo se llena tras absorber. |

**Diferencia esencial:** en Layla el "remembers previously" siempre tiene contenido (resumen continuo); en Box queda **vacío hasta que el usuario absorbe manualmente**. Además el resumen de Fase 4 no sobrevive a la conversación.

## 3. Comparación directa

| Aspecto | Layla | Box hoy |
|---|---|---|
| Cuándo se crea la memoria | En tiempo real mientras se chatea (modelo BART aparte) | Notas literales por intercambio (sin modelo); memoria "real" solo al absorber a mano |
| "remembers previously" | Siempre poblado | Vacío hasta la absorción |
| Estructura | Grafo de conocimiento con nodos temporales y clústeres | Lista plana (sin procesar / procesadas) |
| Recall | Pondera frecuencia + recencia; evita ecos de la misma sesión | Las N más recientes con "hace cuánto" |
| Borrar/regenerar mensaje | Actualiza/elimina su memoria ligada | La nota queda (borrado manual) |
| Coste de CPU del resumen | Bajo (modelo pequeño dedicado) | Cero mientras no se absorbe; la absorción usa el LLM principal (BUG-002) |

## 4. Modificaciones propuestas (priorizadas)

1. **Absorción automática en tiempo real** (lo que más acerca a Layla): cuando las notas sin procesar lleguen a N (p. ej. 8–12), lanzar el absorber en segundo plano tras una respuesta, sin botón. **Depende de arreglar BUG-002** (absorción por tandas + watchdog de LiteRT).
2. **Consolidación sin modelo** (barata, independiente del BUG-002): cada X notas, fusionar en Kotlin las notas viejas parecidas en una sola condensada (dedupe por similitud simple de líneas), reduciendo el tamaño del lote que luego absorberá el LLM.
3. **Persistir el resumen de Fase 4**: guardar el resumen de lo que sale de la ventana ligado al chat/persona e inyectarlo en el bloque "remembers previously" de los **chats nuevos**. Así el bloque nunca empieza vacío, igual que Layla, sin gastar CPU extra.
4. **Recall con recencia + frecuencia** y guardia anti-eco: no reinyectar memorias generadas en la misma sesión que el turno actual.
5. **Vincular borrado/regeneración**: al borrar o regenerar un mensaje, eliminar su nota sin procesar ligada.
6. *(Largo plazo)* Estructura en clústeres temporales para la visualización, alineada con la vista de memoria actual.

**Recomendación inicial:** 3 + 2 primero (sin dependencias, sin coste de CPU), y 1 cuando BUG-002 esté arreglado.

---

**Historia relacionada:** [`BUGS.md`](BUGS.md) (BUG-002 absorción, BUG-015 plantilla de turnos — Layla también inyecta la memoria dentro del system turn) · [`CAMBIOS.md`](CAMBIOS.md) (registro).
