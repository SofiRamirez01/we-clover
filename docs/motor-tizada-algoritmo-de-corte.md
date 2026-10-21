# Motor Tizada: del algoritmo genético original a la optimización de cortes de S.G.O.T.

Documento de trabajo para la tesis. Registra cómo era el algoritmo de ubicación de piezas en su
versión original, qué se cambió para integrarlo al sistema de We Clover (Módulo 4 de S.G.O.T.),
por qué se tomó cada decisión, qué se midió y qué queda pendiente.

Fecha de corte de este documento: 20/10/2026. Todo el código está en el repositorio del proyecto,
en `services/pieza-geometria` (servicio Python), `backend` (Java Spring Boot) y `frontend`
(React). Los commits citados se pueden consultar con `git show <hash>`.

---

## 1. El problema

We Clover produce a pedido (Make-to-Order): cada curso encarga buzos, camperas o chombas con
sus colores, talles y bordados. Antes de confeccionar hay que **cortar la tela**, y la forma en que
se acomodan los moldes sobre la tela (la *tizada*) define cuánta tela se gasta.

Características del corte en We Clover que condicionan el problema:

- **Tela tubular de 90 cm de ancho útil.** Cada capa que se extiende sobre la mesa es el tubo
  aplanado, o sea **dos telas**: una pieza entera ubicada libremente sale dos veces por capa. Los
  bordes del tubo son **dobleces**.
- **Mitad al doblez.** Una pieza simétrica (frente, espalda) se puede cortar como media pieza con su
  eje sobre el doblez: al abrir el tubo queda la pieza entera, y sale una vez por capa.
- **Capas por color.** Sobre la misma tizada se extienden capas de distintos colores; cada capa
  produce las piezas dibujadas en ese color.
- **Mesa de hasta 800 cm** de largo (tope, no objetivo) y hasta 20 capas.
- **Sentido del hilo.** Las piezas se pueden girar 180° (el hilo sigue en el mismo eje) pero no 90°,
  y no se pueden espejar libremente.
- **Margen entre piezas** de 0,5 cm para la cuchilla, y un margen al doblez.
- **Demanda por pieza, talle y color**, que sale de los pedidos de una *tanda* (grupo de pedidos que
  se producen juntos). Una prenda tiene piezas de 1 pin (frente, espalda, bolsillo, cintura) y de 2
  pines (mangas, capucha, capucha interior, puños: izquierda y derecha).

El objetivo es cubrir toda la demanda gastando la menor cantidad de tela posible.

## 2. La versión original (v1.0.0)

### 2.1 Origen

El algoritmo de ubicación es el repositorio *OptimizacionCorteTextil*
(https://github.com/RomaHerot/OptimizacionCorteTextil), tag `v1.0.0`, desarrollado en conjunto
con Romulo Hernanndez Otaño y Martin Crespo para la materia Inteligencia Artificial. Se incorporó
al proyecto con `git subtree` en `services/pieza-geometria/vendor/OptimizacionCorteTextil`.

### 2.2 Qué hacía

Era un programa de consola con cuatro partes:

- `polymaker.py` / `polyorder.py`: crear polígonos de patrones, elegir cuántas copias de cada uno y
  generar el "polígono de margen" de cada pieza (buffer de 5 unidades). Usaban `input()` por consola y
  `matplotlib` para graficar.
- `algorpatronsnap.py`: el **algoritmo genético** que ubica los polígonos de margen en un lienzo.
- `OptCorteTextil.py`: menú que une todo y guarda el mejor individuo en `best_individual.json`.

Diseño del algoritmo genético:

| Elemento | Original |
|---|---|
| Cromosoma | Lista de genes, uno por pieza: posición (x, y) del centroide y orientación (`flipped`). |
| Lienzo | Ancho 800–900 (unidades del dibujo, en la práctica mm); alto = suma de los perímetros de las piezas. |
| Población inicial | Al azar: cada pieza en una (x, y) aleatoria con y sesgada hacia abajo (t²), hasta 200 intentos por pieza y 500 por individuo. |
| Selección | Torneo (tamaño 10). |
| Cruce | Mezcla de posiciones con un α al azar entre dos padres (probabilidad 0,9). |
| Mutación | En x (prob. 0,3, escala 200); en y solo hacia abajo, como "gravedad" (prob. 0,99, escala 500 proporcional a la altura); si una pieza toca el piso se corre de costado; espejo de la pieza (prob. 0,15). |
| Validación de una mutación | Contra las posiciones *anteriores* de las demás piezas; si choca, ese eje no se mueve. |
| Elitismo | Los 3 mejores pasan sin mutar. |
| Estancamiento | A las 40 generaciones sin mejorar, la escala de mutación se duplica una generación. |
| Fitness | Penaliza con −3·10¹⁰ superposiciones y salidas del lienzo. Si es válido, premia: piezas cerca del piso (peso 100), menor altura total (300), compacidad (0,2), huecos chicos entre vecinas (15), piezas que tocan el piso (+100) o se tocan entre sí (+10), y piezas simétricas que tocan los costados (+200). |
| Parámetros | Población 100, 200 generaciones. |
| Salida | El mejor individuo y "fotos" de generaciones intermedias para graficar. |

### 2.3 Limitaciones detectadas al integrarlo

1. **Espejo en lugar de rotación**: la mutación espejaba la pieza (horizontal o verticalmente), lo que
   cambia el sentido del hilo o produce la pieza opuesta.
2. **El espejo no se validaba**: se aplicaba después de los chequeos de superposición.
3. **El bonus de piezas simétricas nunca se activaba**: los genes no llevaban la clave `simetrico`.
4. **Validación contra posiciones viejas**: dos piezas que mutaban en la misma generación podían
   terminar superpuestas sin que nadie lo detectara.
5. **Sin mitad al doblez, sin capas, sin colores, sin demanda**: ubicaba una lista fija de polígonos;
   no sabía cuántas piezas hacían falta ni en qué tela o color.
6. **Interfaz de consola y gráficos**: imports de `matplotlib` a nivel de módulo; impresiones en
   cada generación; sin forma de informar avance ni de cancelar.
7. **Población inicial que no escala**: en cortes densos (piezas que ocupan más del ~55 % del
   lienzo) la ubicación al azar no logra armar ni un individuo válido.
8. **Rendimiento**: el 40 % del tiempo se iba en una caché (`lru_cache`) que para buscar comparaba y
   hasheaba las coordenadas de los polígonos.
9. **Margen doble**: el buffer de 5 unidades por pieza dejaba 10 entre piezas.

## 3. Arquitectura de la solución

```
Frontend (React)                Backend (Java Spring Boot)               Servicio de cortes (Python, FastAPI)
"Motor Tizada" ── POST ──▶  /api/tandas/{id}/cortes/optimizar ── arma ──▶  POST /corte/trabajos
  avance cada 1,5 s ─ GET ─▶  /api/tandas/{id}/cortes/trabajos/{id}        GET  /corte/trabajos/{id}
  cancelar ──── DELETE ───▶  (roles, errores, verifica la tanda)           DELETE /corte/trabajos/{id}
                              PreparacionCorteService: demanda por            │
                              tela, pieza, talle y color (paso 2)             ▼
                                                                    Planificador  ──▶  Algoritmo genético
                                                                    (qué va en cada     (vendor modificado:
                                                                     corte y cuántas     ubica las piezas
                                                                     capas por color)    de cada corte)
```

- **Frontera de unidades**: el backend y el resultado trabajan en cm; el algoritmo, en mm (sus
  parámetros estaban afinados para un lienzo de 900). La conversión ×10 / ÷10 se hace en un solo
  lugar (`app/corte/tizada.py`).
- **El algoritmo quedó como "motor"**: no conoce pedidos, capas ni colores. Esa lógica se agregó en
  una capa nueva (el planificador), en `app/corte/`, sin tocar el vendor más que lo necesario.
- **Trabajos en segundo plano**: optimizar una tanda lleva minutos, así que el servicio devuelve un
  id enseguida y el avance se consulta.

## 4. Cambios al algoritmo genético (vendor)

Cada cambio está en su propio commit con prefijo `vendor:` y su test (`tests/test_vendor_algorpatronsnap.py`).
El README del servicio tiene la tabla de diferencias respecto de v1.0.0.

| # | Cambio | Antes (v1.0.0) | Por qué | Commit |
|---|---|---|---|---|
| base | **Rotación de 180°** sobre el centroide (`rotacion` ∈ {0, 180}), validada como las demás mutaciones | Espejo horizontal o vertical, sin validar | Respetar el sentido del hilo | `e372ec2` |
| base | **Modos de pieza**: ENTERA, MITAD (eje pegado a un doblez, x fija, solo muta en y; al rotar pasa al otro doblez) y RANURA (rinde 1 entera o 2 mitades y el algoritmo elige; se alterna con `mutation_rate_modo`) | Solo piezas enteras | Aprovechar la mitad al doblez sin cambiar la producción | `e372ec2` |
| base | **Margen de la mitad**: buffer recortado por el semiplano del eje, sin margen sobre el doblez | — | La mitad tiene que quedar pegada al doblez | `e372ec2` |
| base | **Avance y cancelación**: `on_generation(gen, total, mejor_fitness, mejor_largo)`, `cancelado` (`threading.Event`), `verbose` | Impresión por consola, sin cancelación | Mostrar el avance en la pantalla y poder cancelar | `e372ec2` |
| base | Los genes llevan `simetrico` | El bonus de simétricas no se activaba | Bug | `e372ec2` |
| base | `matplotlib`, `shapely.plotting`, `numpy` y `polyorder` se importan dentro de las funciones que los usan | Imports a nivel de módulo | El servidor no necesita gráficos | `e372ec2` |
| 1 | Cada mutación se valida contra las posiciones **ya mutadas** de los genes anteriores | Contra las posiciones previas | Dos piezas podían terminar superpuestas | `9318d71` |
| 2 | Rotación inicial al azar | Todas en orientación 0 | Más diversidad inicial | `28b237b` |
| 3 | Si una ranura no entra en el modo sorteado, prueba el otro | — | Evitar descartar individuos | `8c70184` |
| 4 | Tolerancia de 1e-9 en los límites del lienzo y del doblez (no en la superposición, que sigue en área > 1e-8) | Comparación exacta | Una mitad rotada quedaba 1e-13 mm afuera del doblez por redondeo y se rechazaba | `4d805d2` |
| 5 | La cancelación también se revisa al armar la población inicial | — | En cortes grandes eso tarda | `30a3ed0` |
| 6 | `mutation_rate_modo` = 0,1 por defecto | — | — | `49e0fb8` |
| — | `SinUbicacionValida` (subclase de `RuntimeError`) indica qué pieza no entró | `RuntimeError` genérico | Permitir partir el corte o informar la pieza | `75d8569` |
| 7 | Caché de propiedades **por identidad** y traslado sumando coordenadas | `lru_cache` que compara coordenadas; `translate` con matriz afín | 2,3 veces más rápido, mismo resultado | `2870a8a` |
| 8 | **Validación perezosa**: primero cajas, después polígonos; el área de intersección solo si se tocan | Trasladar y calcular intersección en cada chequeo | Rendimiento, mismo criterio | `51898cd` |
| 9 | **Población inicial constructiva** en cortes densos (> 30 % del lienzo): abajo a la izquierda con orden, rotaciones y modos al azar; se construye el 20 % y el resto son variantes mutadas | Solo al azar | Dos cortes reales no arrancaban | `e32c211` |
| 10 | `limite_tiempo`: al llegar, se devuelve el mejor encontrado (distinto de cancelar) | Solo terminaba por generaciones | Presupuesto de tiempo por corte | `c3427f2` |
| 11 | Corte por **estancamiento del largo** (N generaciones sin acortar 1 mm, mínimo 60); encendido por defecto con N = 50 | Solo terminaba por generaciones o por tiempo | Liberar tiempo para reinicios | `8b98c91`, `9f07a08` |

Notas sobre el cambio 11: una primera versión medía el estancamiento sobre el *fitness*, que sigue
mejorando de a poco (compacidad, huecos) aunque el largo ya no baje; en la medición no cortó
ninguna corrida. Se redefinió sobre el largo. Medido en la tanda A (`7c2730a`, cortes 3, 4, 6 y 7,
semillas 1 y 101): la tela no cambió (< 0,5 %) y el tiempo bajó 17 % con N = 50 y 10 % con N = 80,
pero ese tiempo no se convertía en más reinicios por dos reglas que mandaban antes: el corte
anticipado de 2 reinicios sin mejora (cortes chicos) y "solo otra corrida si alcanza para una
como la anterior" (cortes densos). Por eso se encendió con N = 50 junto con esas reglas aflojadas en
COMPLETO: 4 reinicios sin mejora y otra corrida si queda 0,8 × la anterior (RAPIDO sigue con 2 y
1,0). Con esos valores el tiempo de una tanda en COMPLETO puede subir hacia el presupuesto por corte;
lo que se espera es acortar los cortes chicos y medianos. Falta medirlo.

## 5. La capa nueva: el planificador de cortes

El algoritmo genético resuelve *cómo ubicar* un conjunto fijo de piezas. Antes hay que decidir
**qué piezas van juntas en cada tizada y cuántas capas de cada color lleva**. Eso lo hace
`app/corte/planificador.py`, sin correr el algoritmo (es rápido: milisegundos).

### 5.1 Modelo

- Un **corte** = una tizada + un plan de capas L (capas por color).
- Un **ítem** = una pieza en un talle, con su demanda por color d.
- Si en la tizada el ítem rinde u unidades por capa, produce u · L[color] de cada color. El
  planificador elige el **mínimo u** que cubre la demanda en todos los colores.
- Rendimiento por modo: una pieza entera da 2 por capa (las dos telas del tubo); una mitad al doblez,
  1. Una pieza simétrica verificada se arma con "ranuras" (1 entera o 2 mitades) y una mitad suelta si
  u es impar. Una pieza asimétrica sale de a pares (izquierda y derecha, por el tubo).
- **Excedente inevitable**: una pieza asimétrica con demanda impar siempre deja 1 de más; se acepta y
  se informa aparte.
- **Compatibilidad**: solo comparten corte los ítems con el **mismo conjunto de colores**.
- **Simetría verificada**: una pieza marcada simétrica se ofrece como mitad solo si su desvío
  (diferencia simétrica con su espejo / área) es ≤ 1 % (configurable); si no, solo entera y con aviso.
- Una pieza que no entra en el lienzo (ni entera ni como mitad) queda en **no resueltas** con el
  motivo, sin frenar al resto.

### 5.2 Objetivo

Restricciones duras: nunca producir de menos; excedente ≤ tope por pieza, talle y color
(`excedenteMaximoPorItem`, por defecto 0, y también por tela); largo ≤ 800 cm; capas ≤ 20.

Objetivo (modo **TELA**, por defecto): minimizar la tela, Σ(largo × capas). Desempates: menos
cortes, menos prendas partidas entre cortes, menos capas. El modo **COSTO** (minutos: cortes ×
`minutosPorCorteAdicional` + prendas partidas × `costoPrendaPartida`) está implementado y apagado.

### 5.3 Algoritmo

1. Para cada tela compara tres formas de agrupar: por conjunto de colores, por color, y una mezcla.
2. Dentro de cada grupo, **cobertura voraz** con planes de capas candidatos: elige el plan que deja
   menos excedente, después el que cubre más ítems por corte, después el de menos capas.
3. Reparte los ítems de un plan en cortes con **bin packing por área** (first-fit decreasing), con un
   largo estimado = área con margen / (ancho × eficiencia supuesta).
4. Como la cobertura voraz depende de qué planes ve, se la corre con varios topes de búsqueda y los
   dos órdenes de selección, y se queda el mejor resultado **que cumple el tope pedido** (esto corrigió
   un caso donde subir el tope empeoraba el plan, y otro donde se encontraba un plan con un corte
   menos y sin excedente).

### 5.4 Calibración

- **Eficiencia supuesta por tela** (preliminar, de una sola medición): Frisa 0,66, Jersey 0,65,
  Ribb 0,80.
- **Tolerancia de estimación del 10 %**: un corte estimado hasta 880 cm se intenta entero y se parte
  solo si el algoritmo da más de 800 cm o no ubica las piezas. Sin esto, un corte de la tanda A
  estimado en 806 cm se partía en dos, cuando el algoritmo lo resolvía en uno de 725 a 768 cm.

## 6. Integración del planificador con el algoritmo

Implementada en `app/corte/tizada.py`:

- **Entradas del algoritmo**: cada ítem se convierte en entradas ENTERA, MITAD o RANURA según lo
  decidido por el planificador.
- **Márgenes**: buffer de `margen/2` por pieza con esquinas en inglete (0,5 cm entre piezas); las
  enteras quedan a 0,25 cm (`margenBordeCm`) del doblez y del borde inferior; las mitades, con el eje
  sobre el doblez.
- **Simplificación conteniendo**: los polígonos con margen se simplifican a 0,5 mm para el chequeo de
  choques, pero la versión simplificada se agranda para **contener siempre** a la original.
- **Resultado**: a cada pieza sin margen se le aplica la misma transformación del gen (rotación y
  traslación) y se devuelve el **polígono final en cm**, listo para dibujar.
- **Verificación estricta** al final, con los polígonos exactos: ninguna pieza se toca ni sale del
  lienzo, margen entre piezas y al doblez, mitades sobre el doblez. Es independiente del criterio
  que usa el algoritmo.
- **Reinicios con presupuesto de tiempo**: cada corte se corre con semilla, semilla + 1, … mientras
  quede tiempo para una corrida como la anterior, y se queda la tizada más corta que pasa la
  verificación. Presupuesto por corte: RÁPIDO 60 s, COMPLETO 200 s (configurable). Corte anticipado si
  los últimos 2 reinicios no acortan más de 1 %. Si el presupuesto no alcanza para un reinicio completo
  (cortes densos), la primera corrida usa todo el tiempo y devuelve lo mejor encontrado.
- **Compactación final**: sobre la mejor tizada, cada pieza baja (y las enteras se corren a la
  izquierda) mientras siga siendo válida.
- **Partición**: si un corte no se puede ubicar o da más de 800 cm, se parte en dos y se vuelve a
  correr; si una sola unidad no entra, va a no resueltas.
- **Estados**: cada corte termina COMPLETO o LIMITE_DE_TIEMPO; cada corrida registra su semilla,
  largo, generaciones y por qué terminó. La cancelación del trabajo es aparte y no devuelve tizada.

## 7. Servicio, backend y pantalla

- **API de trabajos** (`app/corte/api.py`, `trabajos.py`): `POST/GET/DELETE /corte/trabajos`, un único
  hilo trabajador con cola (el servidor es de 1 vCPU y 2 GB), avance (corte, intento, generación,
  mejor largo y aprovechamiento), cancelación y vencimiento de resultados (60 min). Uvicorn con un
  solo worker.
- **Backend** (`CorteOptimizacionService`, `CorteOptimizadorClient`): arma la demanda con
  `PreparacionCorteService`, lanza, consulta y cancela. Generar y cancelar: ADMINISTRATIVO; consultar:
  también PLANTA. 503 si el optimizador no responde, 409 si no hay demanda, 404 si el trabajo es de
  otra tanda.
- **Pantalla Motor Tizada** (`frontend/src/features/tizadas/`): elegir tanda y tiempo, avance en vivo
  con cancelar, y el resultado: resumen, una tarjeta por corte con capas por color, el **dibujo de la
  tizada** (largo de izquierda a derecha, dobleces arriba y abajo, mitades rayadas, regla y zoom) y
  la **hoja de cortes** (qué pieza sale de qué corte).

## 8. Mediciones

### 8.1 Metodología

- Datos reales: **tanda A** (31 buzos de dos pedidos: 8 en Natural/Francia y 23 en Verde/Blanco,
  talles T1 a T7, telas Frisa, Jersey y Ribb, 8 piezas distintas).
- Script reproducible `scripts/medir.py`: semilla fija (cada reinicio usa semilla + n), CSV por corte
  y por corrida en `scripts/resultados/`.
- Lección aprendida: las mediciones se invalidan si corre otro proceso pesado en paralelo (tiempos
  inflados hasta 3 veces).

### 8.2 Validación v1.0.0 frente a la actual

Mismo corte (8 capuchas interiores, todas enteras), misma semilla, una corrida de 200 generaciones:

| Versión | Individuos inválidos | Tiempo | Largo final |
|---|---|---|---|
| v1.0.0 | 13 603 de 20 100 (**67,7 %**) | 103 s | 136 cm |
| Actual | 327 de 20 100 (**1,6 %**) | 40 s | 146 cm |

La actual casi no genera individuos inválidos y es ~2,6 veces más rápida. El largo final de una sola
corrida depende más de la semilla que de la versión (en otra medición fue 166 cm contra 134 cm a
favor de la actual): por eso se agregaron los reinicios.

### 8.3 Rendimiento

Tiempo de 20 generaciones del mismo corte (8 piezas) al ir aplicando cambios:

| Paso | Tiempo |
|---|---|
| Original | 11,6 s |
| Caché por identidad (cambio 7) | 6,8 s |
| + `intersects` y validación perezosa (cambio 8) | 6,7 s |
| + traslado directo de coordenadas (cambio 7) | 5,1 s |
| + simplificación a 0,5 mm | 4,1 s (+0,5 % de área con margen) |

En los cortes densos, el tiempo por generación bajó de 3,4–4,6 s a ~2,1 s.

### 8.4 Primera medición frente a la final (tanda A)

Primera medición, con el algoritmo integrado y sin optimizaciones (200 generaciones, límite 180 s):

| Corte | Piezas | Resultado |
|---|---|---|
| 1 Frisa Verde | 30 | **No arma la población inicial** en 800 cm |
| 2 Frisa Verde | 8 | 108 s · 183 cm |
| 3 Frisa Verde | 9 | 114 s · 176 cm |
| 4 Frisa Natural | 31 | **No arma la población inicial** |
| 5 Jersey Blanco | 23 | No termina: generación 64 a los 184 s |
| 6 Jersey Francia | 8 | 166 s · 134 cm |
| 7 Ribb Blanco | 43 | No termina: generación 85 a los 183 s |
| 8 Ribb Francia | 14 | No termina: generación 159 a los 181 s |

Medición final (modo COMPLETO, 200 s por corte, reinicios, compactación; 9 cortes porque el corte
Natural se partió con la eficiencia de Frisa en 0,66):

| Corte | Estado | Corridas | 1 corrida | Mejor | Final | Eficiencia real |
|---|---|---|---|---|---|---|
| 1 Frisa Verde (×2 capas) | Límite de tiempo | 1 | 624 | 624 | 624 cm | 71,9 % |
| 2 Frisa Verde (×5) | Completo | 3 | 183 | 183 | 183 cm | 59,3 % |
| 3 Frisa Verde | Completo | 4 | 221 | 183 | **183 cm (−17 %)** | 70,5 % |
| 4 Frisa Natural (×2) | Completo | 3 | 274 | 240 | **240 cm (−12 %)** | 65,6 % |
| 5 Frisa Natural | Completo | 2 | 301 | 301 | 301 cm | 68,3 % |
| 6 Jersey Blanco | Completo | 1 | 416 | 416 | 415 cm | 65,6 % |
| 7 Jersey Francia | Completo | 4 | 145 | 134 | **134 cm (−8 %)** | 69,3 % |
| 8 Ribb Blanco | Límite de tiempo | 1 | 319 | 319 | 319 cm | 78,2 % |
| 9 Ribb Francia | Completo | 3 | 101 | 101 | 101 cm | 82,3 % |

- Todos los cortes pasan la verificación con polígonos exactos.
- Tanda completa: **24,8 min**; largo total 2499 cm frente a 2584 cm con una sola corrida por corte
  (−3,3 %); tela (largo × capas) 4096 cm.
- **Variación por semilla**: el mismo corte dio entre 176 y 221 cm según la semilla (25 %); el
  algoritmo deja de acortar cerca de la generación 80.
- **Eficiencia real por tela**: Frisa 59–72 %, Jersey 64–70 %, Ribb 78–82 %.
- **Estimación del planificador**: error de −18 % a +16 % según el tamaño del corte.

### 8.5 Escala: tandas sintéticas

Con la geometría real y pedidos generados (8 a 35 buzos por pedido, talles con la distribución real,
8 colores de Frisa y 4 de Jersey y Ribb), solo el planificador:

| Pedidos | Cortes | Tiempo estimado de algoritmo |
|---|---|---|
| 15 | 37 | RÁPIDO ~37 min · COMPLETO ~1,7 h |
| 30 | 61 | RÁPIDO ~1 h · COMPLETO ~2,8 h |

Ribb sale con cortes de 80 a 99 piezas en 1 capa (probablemente exagerado por el generador: en la
realidad puños y cintura suelen ser de un solo color).

## 9. Decisiones tomadas y su fundamento

1. **El objetivo es la tela, no la cantidad de cortes.** Se probó primero minimizar cortes: con la
   tanda A daba un corte de 1 capa de 762 cm por tela, y el tope de excedente nunca se usaba. Después,
   con más pedidos, permitir excedente ahorraba 1 corte a cambio de ~5,5 m de tela (≈22 minutos de
   corte por metro de tela). Sin un costo real del tiempo de corte, se decidió optimizar solo tela
   (modo TELA) y dejar el modo COSTO implementado y apagado.
2. **Excedente 0 por defecto, configurable por pedido de optimización y por tela.** El reporte de
   medición compara topes 0, 1, 2 y 4 solo como información.
3. **Se permiten prendas partidas entre cortes** (piezas de un mismo buzo en cortes distintos); se
   cuentan como métrica.
4. **Rotación de 180°, nunca espejo**, por el sentido del hilo.
5. **Piezas simétricas: mitad al doblez solo si la simetría se verifica** (tolerancia 1 %).
6. **Margen de 0,5 cm entre piezas y 0,25 cm al doblez**, configurables.
7. **El algoritmo trabaja en mm** y el resto del sistema en cm, para no reafinar sus parámetros.
8. **Verificación final independiente y estricta** con los polígonos exactos, aunque el algoritmo
   use versiones simplificadas.
9. **Reinicios con presupuesto de tiempo** en lugar de generaciones fijas: con la variación por
   semilla, quedarse con la mejor de varias corridas reduce el desperdicio más que correr más
   generaciones.
10. **Presupuesto por corte: RÁPIDO 60 s, COMPLETO 200 s.** La meta inicial de 30 s por corte se
    relajó porque lo importante es el menor desperdicio.
11. **Cambios al vendor mínimos, separados y documentados**, cada uno con su test que falla si se
    revierte el cambio.
12. **Terminar el MVP antes de seguir optimizando** el algoritmo.

## 10. Limitaciones conocidas

- El resultado **no se guarda**: vive una hora en el optimizador y se pierde si se reinicia.
- En cortes densos, la población inicial no respeta el presupuesto de tiempo (tarda 10 a 27 s).
- La eficiencia por tela sale de una sola tanda.
- El planificador es heurístico (voraz): no garantiza el óptimo de cortes.
- Un corte que termina por límite de tiempo no es exactamente reproducible (la cantidad de
  generaciones depende de la velocidad de la máquina).
- La agrupación de talles que elige el planificador (por divisibilidad de la demanda) no coincide
  con la de planta (T6+T7 y T5+T4+T3); hay que contrastarla con resultados reales.

## 11. Pasos futuros

**Para el MVP:**

1. **Guardar las tizadas.** Propuesta: `OptimizacionCorte` (foto de la demanda de la tanda, parámetros
   efectivos, huella para detectar si quedó desactualizada, resumen) y `Tizada` (cada corte con su
   semilla ganadora, capas, ítems y polígonos). "Desactualizada" se calcula al leer comparando la
   huella. Preguntas abiertas en `doc/pantallas-pendientes.md`.
2. Habilitar la pantalla para planta en solo lectura cuando haya tizadas guardadas.

**Optimización del algoritmo (después del MVP):**

3. Medir el estancamiento (N = 50) con las reglas de reinicio aflojadas en COMPLETO (4 reinicios
   sin mejora, factor de tiempo 0,8) contra los valores anteriores, con las mismas semillas.
4. Cambio 12: respetar el presupuesto de tiempo también en la población inicial.
5. Recalibrar la eficiencia por tela con más tandas reales (quizás por tamaño de corte).
6. Decidir con tandas reales el caso de Ribb (cortes muy grandes de 1 capa).
7. **Optimizador de residuo**: sustituciones automáticas (una pieza cortada con el molde de un talle
   mayor, o con una pieza equivalente) sobre excedentes y cortes de una capa. Ya existe la interfaz y
   el buscador de candidatas; en la tanda A apareció, por ejemplo, el bolsillo T1 con el molde del T2
   (moldes idénticos, desperdicio 0).
8. Acelerar el chequeo de choques (no-fit polygon o distancias cacheadas entre pares que no se
   movieron) y probar programación entera para el planificador.

## 12. Dónde está cada cosa

| Tema | Ubicación |
|---|---|
| Algoritmo genético (vendor) y sus cambios | `services/pieza-geometria/vendor/OptimizacionCorteTextil/algorpatronsnap.py`, README del servicio |
| Planificador | `services/pieza-geometria/app/corte/planificador.py`, `piezas.py`, `parametros.py` |
| Integración, reinicios, compactación, verificación | `services/pieza-geometria/app/corte/tizada.py` |
| Optimizador de residuo (interfaz) | `services/pieza-geometria/app/corte/residuo.py` |
| API de trabajos | `services/pieza-geometria/app/corte/api.py`, `trabajos.py`, `serializacion.py` |
| Medición y tandas sintéticas | `services/pieza-geometria/scripts/medir.py`, `tanda_sintetica.py`, `scripts/resultados/` |
| Backend | `backend/.../service/CorteOptimizacionService.java`, `CorteOptimizadorClient.java`, `TandaController.java` |
| Pantalla | `frontend/src/features/tizadas/` |
| Pendientes | `doc/pantallas-pendientes.md` (sección Módulo 4) |
| Tests | `services/pieza-geometria/tests/` (pytest), `backend/src/test/...` (JUnit5 + Mockito) |
