package com.google.ai.edge.gallery.tts

/**
 * Evalúa la muestra limpia con reglas simples y sin modelos: duración, relación voz/ruido y
 * saturación. Es una guía para decidir si la voz sirve para clonar, no un medidor acústico exacto.
 */
object VoiceQuality {
  enum class Level(val label: String) {
    BUENA("Buena"),
    MEJORABLE("Mejorable"),
    MALA("Mala"),
  }

  data class Assessment(val score: Int, val level: Level, val tips: List<String>)

  fun assess(report: VoiceCleaner.Report): Assessment {
    var score = 100
    val tips = mutableListOf<String>()

    when {
      report.outSeconds < 5f -> {
        score -= 30
        tips.add("Solo quedan %.1f s de voz. Graba entre 5 y 12 s para que el clon tenga suficiente muestra.".format(report.outSeconds))
      }
      report.outSeconds < 8f -> {
        score -= 10
        tips.add("La muestra es algo corta (%.1f s); 8-12 s dan más estabilidad.".format(report.outSeconds))
      }
    }

    val snr = report.snrDb
    when {
      snr == null -> Unit
      snr < 15f -> {
        score -= 25
        tips.add("Había mucho ruido de fondo (%.0f dB). Graba en una habitación silenciosa y prueba la reducción de ruido.".format(snr))
      }
      snr < 25f -> {
        score -= 8
        tips.add("Ruido apreciable (%.0f dB). Se ha reducido, pero una toma más silenciosa saldrá mejor.".format(snr))
      }
    }

    if (report.clippedPct > 0.5f) {
      score -= 25
      tips.add("La original saturó (%.1f %% de muestras). Baja el volumen de entrada o aléjate del micrófono.".format(report.clippedPct))
    }
    if (report.inSeconds > 20f) {
      score -= 5
      tips.add("Grabaste %.0f s pero el motor usa 12 s: se eligió el tramo con más voz.".format(report.inSeconds))
    }
    val after = report.snrAfterDb
    if (snr != null && after != null && after - snr >= 4f) score = (score + 5).coerceAtMost(100)
    if (tips.isEmpty()) tips.add("Duración, nivel y ruido son adecuados: esta muestra sirve para clonar.")

    val level =
      when {
        score >= 80 -> Level.BUENA
        score >= 55 -> Level.MEJORABLE
        else -> Level.MALA
      }
    return Assessment(score.coerceIn(0, 100), level, tips)
  }
}
