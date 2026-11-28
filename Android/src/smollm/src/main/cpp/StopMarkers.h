#pragma once
// BUG-029: paradas por texto del motor. Separado de LLMInference para poder probarlo sin Android.
//
// Antes "###" y "System instruction:" cortaban la respuesta en cuanto aparecian EN CUALQUIER punto
// del texto: un simple encabezado markdown "### 1. Composicion" (muy comun en Gemma) terminaba el
// turno y la respuesta quedaba a medias. Ahora solo cuentan como parada si aparecen como una
// cabecera de turno inventada por el modelo: al inicio de linea y seguidas de un rol y ':'
// ("### User:", "### Instruction:"...), o "System instruction:" al inicio de linea.
#include <cctype>
#include <string>
#include <vector>

namespace stopmarkers {

// Marcadores de turno de las plantillas: cortan donde aparezcan.
inline const std::vector<std::string>& turnMarkers() {
    static const std::vector<std::string> m = {
        "<turn|", "<|turn_end|>", "<turn_end|>", "<start_of_turn>", "<end_of_turn>"
    };
    return m;
}

inline bool iequalsAt(const std::string& s, size_t pos, const char* word) {
    size_t i = 0;
    for (; word[i]; ++i) {
        if (pos + i >= s.size()) return false;
        if (std::tolower((unsigned char) s[pos + i]) != word[i]) return false;
    }
    return true;
}

// Cabecera de turno inventada ("### User:" / "System instruction:") al inicio de una linea.
// Devuelve el texto que la identifica ("###" o "System instruction:") o "" si no hay.
inline std::string roleHeader(const std::string& s) {
    static const char* roles[] = {"user", "human", "instruction", "assistant", "system", "response", "input", "model"};
    size_t pos = 0;
    while (pos < s.size()) {
        size_t lineStart = pos;
        size_t nl = s.find('\n', pos);
        size_t lineEnd = nl == std::string::npos ? s.size() : nl;
        if (s.compare(lineStart, 3, "###") == 0) {
            size_t p = lineStart + 3;
            while (p < lineEnd && (s[p] == ' ' || s[p] == '\t')) ++p;
            for (const char* r : roles) {
                if (iequalsAt(s, p, r)) {
                    size_t q = p;
                    while (r[q - p]) ++q;
                    while (q < lineEnd && (s[q] == ' ' || s[q] == '\t')) ++q;
                    if (q < lineEnd && s[q] == ':') return "###";
                }
            }
        }
        if (s.compare(lineStart, 19, "System instruction:") == 0) return "System instruction:";
        if (nl == std::string::npos) break;
        pos = nl + 1;
    }
    return "";
}

// Primera parada encontrada en el texto acumulado, o "" si no hay.
inline std::string find(const std::string& full) {
    for (const auto& m : turnMarkers()) {
        if (full.find(m) != std::string::npos) return m;
    }
    return roleHeader(full);
}

}  // namespace stopmarkers
