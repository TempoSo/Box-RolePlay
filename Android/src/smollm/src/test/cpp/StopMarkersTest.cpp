// Prueba de StopMarkers.h (BUG-029). Compilar y ejecutar: g++ -std=c++17 StopMarkersTest.cpp -o /tmp/t && /tmp/t
#include "../../main/cpp/StopMarkers.h"
#include <cstdio>
static int fails=0;
#define CHECK(c) do{ if(!(c)){ printf("FALLO linea %d: %s\n",__LINE__,#c); fails++; } }while(0)
int main(){
  using namespace stopmarkers;
  CHECK(find("Aqui van cuatro areas:\n\n### 1. Exposicion\ntexto").empty());      // encabezado markdown
  CHECK(find("---\n\n### Composicion\n").empty());
  CHECK(find("#### Titulo\n## Otro").empty());
  CHECK(find("usa ### en el texto").empty());
  CHECK(find("hola\n### User: dime").size()==3);                                 // turno inventado
  CHECK(find("### Instruction:\nhaz").size()==3);
  CHECK(find("hola\n###   assistant :\n")=="###");
  CHECK(find("### User").empty());                                                // aun sin ':'
  CHECK(find("### Usuario: hola").empty());                                       // no es un rol de la lista
  CHECK(find("texto <end_of_turn> mas")=="<end_of_turn>");
  CHECK(find("fin<turn|")=="<turn|");
  CHECK(find("System instruction: sé amable")=="System instruction:");
  CHECK(find("dijo System instruction: x").empty());                              // a mitad de linea no
  CHECK(find("").empty());
  printf(fails?"HAY FALLOS\n":"OK\n"); return fails;
}
