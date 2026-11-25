#include "../../main/cpp/ContextWindow.h"
#include "../../main/cpp/MediaReuse.h"
#include <cstdio>
#include <cstdlib>
using namespace mediareuse;
static int fails = 0;
#define CHECK(c) do { if (!(c)) { printf("FALLO linea %d: %s\n", __LINE__, #c); fails++; } } while (0)

static Chunk text(std::vector<int> t) { Chunk c; c.tokens = std::move(t); return c; }
static Chunk img(int n) { Chunk c; c.image = true; c.nPos = n; return c; }

int main() {
    int ph = -2;
    // Turno A: [10 11 12] [imagen 4] [20 21]  (cache frio)
    std::vector<Chunk> a = {text({10, 11, 12}), img(4), text({20, 21})};
    auto fullA = flatten(a, ph);
    CHECK(fullA.size() == 9);
    CHECK(fullA[3] == -2 && fullA[6] == -5 && ph == -6);
    std::vector<int> cached; // vacio
    CHECK(ctxwin::reusablePrefix(cached, fullA) == 0);
    auto stA = plan(a, 0);
    CHECK(stA.size() == 3 && stA[0].skip == 0);

    // Tras A el KV = fullA + respuesta (2 tokens)
    cached = fullA; cached.push_back(30); cached.push_back(31);

    // Turno B (texto): la imagen es ahora "[imagen]" = tokens reales 99 98; el prefijo llega justo hasta la imagen
    std::vector<int> promptB = {10, 11, 12, 99, 98, 20, 21, 30, 31, 40};
    CHECK(ctxwin::reusablePrefix(cached, promptB) == 3);

    // Turno C: texto + NUEVA imagen despues de la conversacion cacheada (la situacion del log: 2000 tokens previos)
    std::vector<int> hist = {10, 11, 12, 99, 98, 20, 21, 30, 31};
    cached = hist; // KV caliente de un turno de texto
    std::vector<Chunk> c = {text({10, 11, 12, 99, 98, 20, 21, 30, 31, 50}), img(3), text({60, 61})};
    auto fullC = flatten(c, ph);
    size_t common = ctxwin::reusablePrefix(cached, fullC);
    CHECK(common == 9);
    auto stC = plan(c, common);
    CHECK(stC.size() == 3);
    CHECK(stC[0].chunk == 0 && stC[0].skip == 9); // solo se procesa el token 50 del primer trozo
    CHECK(stC[1].chunk == 1 && stC[1].skip == 0);
    CHECK(stC[2].chunk == 2 && stC[2].skip == 0);

    // Imagen vieja en el KV nunca coincide con una nueva (ids unicos)
    cached = fullC; cached.push_back(70);
    int ph2 = ph;
    std::vector<Chunk> d = {text({10, 11, 12, 99, 98, 20, 21, 30, 31, 50}), img(3), text({60, 61})};
    auto fullD = flatten(d, ph2);
    CHECK(ctxwin::reusablePrefix(cached, fullD) == 10); // se detiene al empezar la imagen

    // Plan: trozos enteros ya cacheados se saltan
    auto st = plan(c, 10);
    CHECK(st.size() == 2 && st[0].chunk == 1);
    // Si el prefijo cayera dentro de la imagen, el plan se rechaza
    CHECK(plan(c, 11).empty());

    // Prompt vacio / todo cacheado menos 1 (reusablePrefix deja 1)
    std::vector<int> same = {1, 2, 3};
    CHECK(ctxwin::reusablePrefix(same, same) == 2);

    printf(fails ? "%d FALLOS\n" : "TODO OK\n", fails);
    return fails ? 1 : 0;
}
