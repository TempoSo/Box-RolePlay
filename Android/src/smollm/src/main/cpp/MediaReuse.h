#ifndef MEDIAREUSE_H
#define MEDIAREUSE_H

#include <cstddef>
#include <vector>

// Reutilizacion del cache de KV en turnos con imagenes (BUG-045, caso imagenes).
// El prompt de un turno con imagen llega de mtmd como trozos: texto, imagen, texto... Cada posicion
// de imagen se representa con un id negativo UNICO, de modo que nunca coincide con nada de otro
// turno: el prefijo reutilizable llega hasta donde empieza la imagen y no mas alla.
namespace mediareuse {

struct Chunk {
    bool image = false;
    std::vector<int> tokens; // texto: ids reales
    int nPos = 0;            // imagen: posiciones que ocupa en el KV
};

// Posiciones del prompt completo (tokens reales + un id negativo unico por posicion de imagen).
inline std::vector<int> flatten(const std::vector<Chunk> &chunks, int &nextPlaceholder) {
    std::vector<int> out;
    for (const Chunk &c: chunks) {
        if (!c.image) {
            out.insert(out.end(), c.tokens.begin(), c.tokens.end());
        } else {
            for (int i = 0; i < c.nPos; i++) out.push_back(nextPlaceholder--);
        }
    }
    return out;
}

struct Step {
    size_t chunk; // indice del trozo a procesar
    size_t skip;  // tokens de texto de ese trozo que ya estan en el KV
};

// Trozos que faltan por procesar si las primeras `common` posiciones ya estan en el KV.
// Devuelve vacio si `common` cae dentro de una imagen (no se puede partir): el llamador debe usar 0.
inline std::vector<Step> plan(const std::vector<Chunk> &chunks, size_t common) {
    std::vector<Step> steps;
    size_t pos = 0;
    for (size_t i = 0; i < chunks.size(); i++) {
        const size_t len = chunks[i].image ? (size_t) chunks[i].nPos : chunks[i].tokens.size();
        const size_t end = pos + len;
        if (end > common) {
            const size_t skip = common > pos ? common - pos : 0;
            if (chunks[i].image && skip > 0) return {};
            steps.push_back({i, skip});
        }
        pos = end;
    }
    return steps;
}

} // namespace mediareuse

#endif // MEDIAREUSE_H
