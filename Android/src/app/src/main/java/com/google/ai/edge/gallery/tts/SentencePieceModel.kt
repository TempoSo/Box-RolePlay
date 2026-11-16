package com.google.ai.edge.gallery.tts

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Lector mínimo de un `tokenizer.model` de SentencePiece (protobuf). Solo saca la lista de piezas con su
 * puntuación para generar el `vocab.json` ({pieza: id}) y el `token_scores.json` ({pieza: puntuación}) que
 * usa sherpa-onnx. Lógica pura.
 */
object SentencePieceModel {
  data class Piece(val text: String, val score: Float)

  private class Reader(val b: ByteArray, var pos: Int = 0, val end: Int = b.size) {
    fun more() = pos < end
    fun varint(): Long {
      var r = 0L; var shift = 0
      while (true) {
        require(pos < end) { "fin inesperado" }
        val x = b[pos++].toInt() and 0xFF
        r = r or ((x and 0x7F).toLong() shl shift)
        if (x and 0x80 == 0) return r
        shift += 7
        require(shift < 64) { "varint demasiado largo" }
      }
    }
    fun skip(wire: Int) {
      when (wire) {
        0 -> varint()
        1 -> pos += 8
        2 -> { val n = varint().toInt(); pos += n }
        5 -> pos += 4
        else -> throw IllegalArgumentException("tipo de campo $wire")
      }
      require(pos <= end) { "fin inesperado" }
    }
  }

  fun parse(bytes: ByteArray): List<Piece> {
    val out = ArrayList<Piece>()
    val r = Reader(bytes)
    while (r.more()) {
      val tag = r.varint().toInt()
      val field = tag ushr 3
      val wire = tag and 7
      if (field == 1 && wire == 2) {
        val len = r.varint().toInt()
        require(r.pos + len <= r.end) { "pieza truncada" }
        out.add(readPiece(Reader(bytes, r.pos, r.pos + len)))
        r.pos += len
      } else r.skip(wire)
    }
    require(out.isNotEmpty()) { "El archivo no parece un tokenizer.model de SentencePiece" }
    return out
  }

  private fun readPiece(r: Reader): Piece {
    var text = ""; var score = 0f
    while (r.more()) {
      val tag = r.varint().toInt()
      val field = tag ushr 3
      val wire = tag and 7
      when {
        field == 1 && wire == 2 -> {
          val len = r.varint().toInt()
          text = String(r.b, r.pos, len, Charsets.UTF_8); r.pos += len
        }
        field == 2 && wire == 5 -> {
          val bits = (r.b[r.pos].toInt() and 0xFF) or ((r.b[r.pos + 1].toInt() and 0xFF) shl 8) or
            ((r.b[r.pos + 2].toInt() and 0xFF) shl 16) or ((r.b[r.pos + 3].toInt() and 0xFF) shl 24)
          score = Float.fromBits(bits); r.pos += 4
        }
        else -> r.skip(wire)
      }
    }
    return Piece(text, score)
  }

  fun vocabJson(pieces: List<Piece>): String =
    buildJsonObject { pieces.forEachIndexed { i, p -> put(p.text, JsonPrimitive(i)) } }.toString()

  fun scoresJson(pieces: List<Piece>): String =
    buildJsonObject { pieces.forEach { put(it.text, JsonPrimitive(it.score)) } }.toString()
}
