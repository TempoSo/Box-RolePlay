package com.google.ai.edge.gallery.tts

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Limpieza de la muestra de voz de referencia de Pocket TTS (que clona la voz tal cual suena, con su
 * ruido, silencios y volumen). Lógica pura, sin Android. Pasos: quitar el nivel DC y los graves de
 * ruido (<80 Hz), cortar el hiss por encima de 7,5 kHz, reducir el ruido de fondo por sustracción
 * espectral, recortar silencios (inicio, final y pausas largas), elegir el mejor tramo de hasta
 * 12 s (el límite real de SherpaPocketEngine), igualar el volumen, suavizar los bordes y entregar
 * siempre WAV mono a 24 kHz (la tasa que espera Pocket TTS).
 */
object VoiceCleaner {
  class Report(
    val inSeconds: Float,
    val outSeconds: Float,
    /** Relación voz/ruido estimada de la muestra original, en dB (null si no se pudo estimar). */
    val snrDb: Float?,
    /** % de muestras saturadas (clipping) en la original. */
    val clippedPct: Float,
    /** Relación voz/ruido estimada después de limpiar, en dB. */
    val snrAfterDb: Float?,
    val notes: List<String>,
  )

  class Result(val samples: FloatArray, val sampleRate: Int, val report: Report)

  private const val MIN_VOICE_S = 3f
  /** SherpaPocketEngine recorta la referencia a 12 s: no sirve guardar más. */
  private const val MAX_REFERENCE_S = 12f
  /** Pocket TTS genera audio a 24 kHz; todas las voces guardadas salen a esa tasa. */
  private const val TARGET_RATE = 24000

  fun clean(input: FloatArray, sampleRate: Int, maxSeconds: Float = MAX_REFERENCE_S): Result {
    val notes = mutableListOf<String>()
    val inSec = input.size.toFloat() / sampleRate
    val clipped = if (input.isEmpty()) 0f else input.count { abs(it) >= 0.999f } * 100f / input.size
    if (clipped > 0.5f) notes.add("La grabación está saturada (%.1f %% de muestras): graba más lejos o con menos volumen.".format(clipped))
    if (input.size < sampleRate / 2) {
      val short = resample(input, sampleRate, TARGET_RATE)
      return Result(short, TARGET_RATE, Report(inSec, short.size.toFloat() / TARGET_RATE, null, clipped, null, notes + "Muestra demasiado corta."))
    }

    var x = bandlimit(removeDcAndRumble(input, sampleRate), sampleRate)
    val snr = estimateSnr(frameRms(x, sampleRate))
    x = denoise(x, sampleRate)
    val snrAfter = estimateSnr(frameRms(x, sampleRate))
    if (snr != null && snrAfter != null && snrAfter > snr + 1f) {
      notes.add("Ruido estimado: %.0f dB → %.0f dB.".format(snr, snrAfter))
    }
    // VAD suavizado: un pico suelto no cuenta como voz y un bache corto no la corta.
    val smooth = smoothRms(frameRms(x, sampleRate))
    val thr = speechThreshold(smooth)
    val frame = frameLen(sampleRate)
    val voiced = BooleanArray(smooth.size) { smooth[it] > thr }
    if (voiced.none { it }) {
      val out = resample(x, sampleRate, TARGET_RATE)
      return Result(out, TARGET_RATE, Report(inSec, out.size.toFloat() / TARGET_RATE, snr, clipped, snrAfter, notes + "No se detecta voz en la muestra."))
    }
    // Recorte: bordes con 150 ms de margen; pausas largas (>0,6 s) acortadas a 0,3 s.
    val margin = (0.15f * sampleRate / frame).toInt().coerceAtLeast(1)
    val first = max(0, voiced.indexOfFirst { it } - margin)
    val last = min(smooth.size - 1, voiced.indexOfLast { it } + margin)
    val maxGap = (0.6f * sampleRate / frame).toInt()
    val keepGap = (0.3f * sampleRate / frame).toInt()
    val out = ArrayList<Float>(x.size)
    var f = first
    val limit = (maxSeconds * sampleRate).toInt()
    while (f <= last && out.size < limit) {
      // Longitud del tramo de silencio que empieza en f
      var g = f
      while (g <= last && !voiced[g]) g++
      val gap = g - f
      val take = min(if (gap > maxGap) keepGap else gap, max(0, (limit - out.size) / frame))
      for (i in 0 until take) appendFrame(out, x, f + i, frame)
      f += max(gap, 0)
      if (f > last || out.size >= limit) break
      // tramo con voz
      var v = f
      while (v <= last && voiced[v]) v++
      for (i in f until v) appendFrame(out, x, i, frame)
      f = v
    }
    var y = FloatArray(out.size) { out[it] }
    if (y.size > limit) {
      y = bestWindow(y, sampleRate, limit)
      notes.add("Se eligió el tramo con más voz (%d s máximo): el motor solo usa %d s.".format(maxSeconds.toInt(), maxSeconds.toInt()))
    }
    y = normalize(y, sampleRate)
    y = resample(y, sampleRate, TARGET_RATE)
    val outSec = y.size.toFloat() / TARGET_RATE
    if (outSec < MIN_VOICE_S) notes.add("Quedan solo %.1f s de voz; con 5-12 s el clon sale mejor.".format(outSec))
    if (snr != null && snr in 1f..15f) notes.add("Había bastante ruido de fondo (%.0f dB); se redujo, pero una grabación en silencio será mejor.".format(snr))
    return Result(y, TARGET_RATE, Report(inSec, outSec, snr, clipped, snrAfter, notes))
  }

  /** Filtro paso bajo de un polo: quita el hiss por encima de ~7,5 kHz sin tocar la voz. */
  fun bandlimit(x: FloatArray, sr: Int, cutoff: Float = 7500f): FloatArray {
    if (cutoff >= sr / 2f || x.isEmpty()) return x
    val a = exp(-2.0 * PI * cutoff / sr).toFloat()
    var y = 0f
    return FloatArray(x.size) { i -> y = a * y + (1f - a) * x[i]; y }
  }

  /** Media móvil de 3 tramos para que el VAD no tiemble con un solo pico. */
  private fun smoothRms(v: FloatArray): FloatArray =
    FloatArray(v.size) { i ->
      val prev = v.getOrElse(i - 1) { v[i] }
      val next = v.getOrElse(i + 1) { v[i] }
      (prev + 2f * v[i] + next) / 4f
    }

  /**
   * Si la muestra limpia pasa del límite, se queda el tramo continuo con más energía de voz en vez
   * de los primeros segundos (que a menudo son «probando, probando»).
   */
  private fun bestWindow(x: FloatArray, sr: Int, maxSamples: Int): FloatArray {
    if (x.size <= maxSamples) return x
    val frame = frameLen(sr)
    val frames = (x.size + frame - 1) / frame
    val winFrames = max(1, maxSamples / frame)
    val rms = frameRms(x, sr)
    val prefix = FloatArray(rms.size + 1)
    for (i in rms.indices) prefix[i + 1] = prefix[i] + rms[i] * rms[i]
    var bestStart = 0
    var best = -1f
    var s = 0
    while (s + winFrames <= frames) {
      val score = prefix[min(frames, s + winFrames)] - prefix[s]
      if (score > best) { best = score; bestStart = s }
      s += max(1, winFrames / 4)
    }
    val from = bestStart * frame
    return x.copyOfRange(from, min(x.size, from + maxSamples))
  }

  /** Interpolación lineal a 24 kHz. La señal ya está limitada a 7,5 kHz, así que no hay aliasing. */
  fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
    if (from == to || x.isEmpty()) return x
    val count = max(1, (x.size.toLong() * to / from).toInt())
    return FloatArray(count) { i ->
      val pos = i.toDouble() * from / to
      val a = pos.toInt().coerceAtMost(x.size - 1)
      val b = min(x.size - 1, a + 1)
      val frac = (pos - a).toFloat()
      x[a] * (1f - frac) + x[b] * frac
    }
  }

  private fun appendFrame(out: MutableList<Float>, x: FloatArray, idx: Int, frame: Int) {
    val s = idx * frame
    val e = min(x.size, s + frame)
    for (i in s until e) out.add(x[i])
  }

  private fun frameLen(sr: Int) = max(1, sr / 50) // 20 ms

  fun frameRms(x: FloatArray, sr: Int): FloatArray {
    val n = frameLen(sr)
    val count = (x.size + n - 1) / n
    return FloatArray(count) { i ->
      val s = i * n
      val e = min(x.size, s + n)
      var acc = 0.0
      for (k in s until e) acc += x[k] * x[k]
      sqrt(acc / max(1, e - s)).toFloat()
    }
  }

  private fun percentile(v: FloatArray, p: Float): Float {
    if (v.isEmpty()) return 0f
    val s = v.sortedArray()
    return s[((s.size - 1) * p).toInt()]
  }

  /**
   * Umbral de "hay voz": por encima del ruido de fondo (percentil 10) pero también por debajo del
   * propio hablante (percentil 90), para no recortar a alguien que habla bajito en una casa silenciosa.
   */
  private fun speechThreshold(rms: FloatArray): Float =
    max(max(percentile(rms, 0.10f) * 3f, percentile(rms, 0.90f) * 0.06f), 0.003f)

  private fun estimateSnr(rms: FloatArray): Float? {
    val noise = percentile(rms, 0.10f)
    val speech = percentile(rms, 0.90f)
    if (noise <= 1e-6f || speech <= noise) return null
    return 20f * log10(speech / noise)
  }

  /** Quita el nivel DC y filtra por debajo de ~80 Hz (biquad paso alto, Butterworth). */
  fun removeDcAndRumble(x: FloatArray, sr: Int): FloatArray {
    if (x.isEmpty()) return x
    val mean = x.average().toFloat()
    val w0 = 2.0 * PI * 80.0 / sr
    val alpha = sin(w0) / (2.0 * 0.7071)
    val c = cos(w0)
    val b0 = (1 + c) / 2; val b1 = -(1 + c); val b2 = (1 + c) / 2
    val a0 = 1 + alpha; val a1 = -2 * c; val a2 = 1 - alpha
    var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
    return FloatArray(x.size) { i ->
      val xi = (x[i] - mean).toDouble()
      val yi = (b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
      x2 = x1; x1 = xi; y2 = y1; y1 = yi
      yi.toFloat()
    }
  }

  /** Sustracción espectral: el perfil de ruido sale de los tramos más silenciosos de la propia muestra. */
  fun denoise(x: FloatArray, sr: Int): FloatArray {
    val n = 1024
    val hop = n / 4
    if (x.size < n * 2) return x
    val win = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / n)).toFloat() }
    val frames = (x.size - n) / hop + 1
    val re = Array(frames) { FloatArray(n) }
    val im = Array(frames) { FloatArray(n) }
    val energy = FloatArray(frames)
    for (f in 0 until frames) {
      val off = f * hop
      for (i in 0 until n) re[f][i] = x[off + i] * win[i]
      fft(re[f], im[f], false)
      var e = 0f
      for (k in 0..n / 2) e += re[f][k] * re[f][k] + im[f][k] * im[f][k]
      energy[f] = e
    }
    val cut = percentile(energy, 0.15f)
    val noise = FloatArray(n / 2 + 1)
    var cnt = 0
    for (f in 0 until frames) if (energy[f] <= cut) {
      for (k in 0..n / 2) noise[k] += sqrt(re[f][k] * re[f][k] + im[f][k] * im[f][k])
      cnt++
    }
    if (cnt == 0) return x
    for (k in noise.indices) noise[k] /= cnt
    val out = FloatArray(x.size)
    val norm = FloatArray(x.size)
    val over = 1.8f
    val floor = 0.08f
    val prevGain = FloatArray(n / 2 + 1) { 1f }
    for (f in 0 until frames) {
      for (k in 0..n / 2) {
        val mag = sqrt(re[f][k] * re[f][k] + im[f][k] * im[f][k])
        var g = if (mag > 1e-9f) max(floor, 1f - over * noise[k] / mag) else floor
        g = 0.6f * g + 0.4f * prevGain[k] // suaviza para evitar el "ruido musical"
        prevGain[k] = g
        re[f][k] *= g; im[f][k] *= g
        if (k in 1 until n / 2) { re[f][n - k] = re[f][k]; im[f][n - k] = -im[f][k] }
      }
      fft(re[f], im[f], true)
      val off = f * hop
      for (i in 0 until n) { out[off + i] += re[f][i] * win[i]; norm[off + i] += win[i] * win[i] }
    }
    for (i in out.indices) out[i] = if (norm[i] > 1e-3f) out[i] / norm[i] else x[i]
    return out
  }

  /** Volumen de la voz a ~-20 dBFS (RMS) y límite suave de picos; con fundido de 10 ms. */
  fun normalize(x: FloatArray, sr: Int): FloatArray {
    if (x.isEmpty()) return x
    val rms = frameRms(x, sr)
    val thr = speechThreshold(rms)
    var acc = 0.0; var cnt = 0
    rms.forEach { if (it > thr) { acc += it.toDouble() * it; cnt++ } }
    val speechRms = if (cnt > 0) sqrt(acc / cnt).toFloat() else rms.average().toFloat()
    val gain = if (speechRms > 1e-6f) (0.1f / speechRms).coerceIn(0.1f, 20f) else 1f
    val y = FloatArray(x.size) { i -> (tanh((x[i] * gain * 0.9f).toDouble()) / 0.9).toFloat().coerceIn(-0.97f, 0.97f) }
    val fade = min(y.size / 2, sr / 100)
    for (i in 0 until fade) { val g = i / fade.toFloat(); y[i] *= g; y[y.size - 1 - i] *= g }
    return y
  }

  /** FFT en el sitio (radix-2); [inverse] divide por n. */
  fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
      j = j xor bit
      if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
    }
    var len = 2
    while (len <= n) {
      val ang = 2.0 * PI / len * (if (inverse) 1 else -1)
      val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
      var i = 0
      while (i < n) {
        var cr = 1f; var ci = 0f
        for (k in 0 until len / 2) {
          val a = i + k; val b = a + len / 2
          val tr = re[b] * cr - im[b] * ci
          val ti = re[b] * ci + im[b] * cr
          re[b] = re[a] - tr; im[b] = im[a] - ti
          re[a] += tr; im[a] += ti
          val ncr = cr * wr - ci * wi
          ci = cr * wi + ci * wr
          cr = ncr
        }
        i += len
      }
      len = len shl 1
    }
    if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
  }
}
