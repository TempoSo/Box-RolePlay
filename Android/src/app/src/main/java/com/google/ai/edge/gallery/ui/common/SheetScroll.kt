package com.google.ai.edge.gallery.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Coordina el scroll interno de una ficha con el `ModalBottomSheet` que la contiene.
 *
 * Cuando el contenido cabe justo en la ventana no hay recorrido de scroll, así que un gesto hacia
 * arriba llega entero al sheet, que rebota en su ancla superior ("baila" aunque no pueda expandirse
 * más). Al consumir ese delta cuando el scroll ya está al fondo, el sheet deja de recibirlo. El
 * gesto hacia abajo NO se consume: sigue sirviendo para cerrar la ficha.
 */
fun sheetScrollConnection(scrollState: ScrollState): NestedScrollConnection =
    object : NestedScrollConnection {
        private fun alFondo(): Boolean = scrollState.value >= scrollState.maxValue

        private fun consumirArriba(available: Offset): Offset =
            if (available.y < 0 && alFondo()) Offset(0f, available.y) else Offset.Zero

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            consumirArriba(available)

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = consumirArriba(available)

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            if (available.y < 0 && alFondo()) Velocity(0f, available.y) else Velocity.Zero
    }

/** Atajo para aplicar la conexión antes del `verticalScroll` de la ficha. */
fun Modifier.sheetNestedScroll(scrollState: ScrollState): Modifier =
    nestedScroll(sheetScrollConnection(scrollState))
