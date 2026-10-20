# Servicio de Geometría de Piezas

Microservicio FastAPI que calcula el contorno de una Pieza (área/ancho/largo/perímetro) a
partir de una definición por segmentos (recta / arco / círculo), y escala un contorno ya
calculado a un talle objetivo.

Este módulo reutiliza y adapta código desarrollado en conjunto con Romulo Hernanndez
Otaño y Martin Crespo para la materia Inteligencia Artificial, tag v1.0.0 del
repositorio https://github.com/RomaHerot/OptimizacionCorteTextil.

El código de ese repositorio vive en `vendor/OptimizacionCorteTextil` (traído con
`git subtree` desde el tag `v1.0.0`). **El vendor fue modificado respecto de v1.0.0** dentro
de este repo (commits con prefijo `vendor:`; nunca se hace `git subtree push`). Los cambios
están todos en `algorpatronsnap.py` (más una línea de `OptCorteTextil.py`):

- El espejo (`flipped`) se reemplazó por una rotación de 180° sobre el centroide
  (`gen["rotacion"]` ∈ {0, 180}), validada como las demás mutaciones. El hilo sigue en el
  eje vertical de la pieza y ninguna pieza se espeja. El parámetro conserva el nombre
  `mutation_rate_flip`.
- Piezas simétricas: modo `MITAD` (la mitad con el eje sobre el doblez, x fija, solo muta
  en y, y al rotar pasa al otro doblez) y modo `RANURA` (rinde 1 entera o 2 mitades, el
  algoritmo elige y alterna con `mutation_rate_modo`). `create_half_polygons` corta la pieza
  por su eje y recorta el margen por el semiplano del eje, para que no haya margen sobre el
  doblez. `place_polygon` aplica a la pieza sin margen la transformación de un gen.
- Parámetros opcionales: `doblez`, `on_generation(gen, total, mejor_fitness, mejor_largo)`,
  `cancelado` (`threading.Event`, revisado al inicio de cada generación y al armar la
  población inicial; lanza `CorteCancelado`) y `verbose` (la línea por generación).
- Los genes llevan la clave `simetrico`, así se activa el bonus de tocar el borde, que en
  v1.0.0 nunca se aplicaba.
- `matplotlib`, `shapely.plotting`, `numpy` y `polyorder` se importan adentro de las
  funciones que los usan: el servidor no los carga (numpy igual llega como dependencia de
  shapely).

Lo anterior está en `e372ec2`. Además, el comportamiento es distinto de v1.0.0 en estos
puntos, cada uno en su propio commit y con su test en `tests/test_vendor_algorpatronsnap.py`
(`test_cambioN_…`):

| # | Cambio | v1.0.0 | Commit |
|---|---|---|---|
| 1 | Cada mutación se valida contra las posiciones **ya mutadas** de los genes anteriores | Contra las posiciones previas: dos piezas que mutaban en la misma generación podían quedar superpuestas | `9318d71` |
| 2 | Rotación inicial al azar (0° o 180°) en la población | Todas arrancaban en orientación 0 | `28b237b` |
| 3 | Si una ranura no entra en el modo sorteado al armar la población, prueba el otro | No existía | `8c70184` |
| 4 | Tolerancia de 1e-9 en los límites del lienzo y del doblez, y de 1e-6 en el bonus de tocar el borde. No se aplica a la superposición, que sigue rechazando un área de intersección > 1e-8 | Comparación exacta: una mitad rotada 180° quedaba 1e-13 afuera del doblez por redondeo y se rechazaba | `4d805d2` |
| 5 | La cancelación también se revisa mientras se arma la población inicial | No existía | `30a3ed0` |
| 6 | `mutation_rate_modo` vale 0,1 por defecto en el algoritmo | No existía | `49e0fb8` |
| 7 | Caché de propiedades de cada polígono **por identidad del objeto**, y traslado sumando el desplazamiento a las coordenadas. Mismo resultado, unas 2,3 veces más rápido | `lru_cache`, que para buscar compara y hashea las coordenadas (40 % del tiempo medido), y `shapely.affinity.translate`, que arma la matriz afín en cada llamada | `2870a8a` |
| 8 | **Validación perezosa**: primero se comparan cajas y el polígono se traslada solo si alguna caja vecina se superpone; el área de intersección se calcula solo si los polígonos se tocan. El criterio de superposición no cambia (área > 1e-8) | Trasladaba el polígono y calculaba la intersección en cada chequeo | `51898cd` |
| 9 | **Población inicial constructiva** en cortes densos (piezas > 30 % del lienzo): cada individuo se arma abajo a la izquierda, con orden, rotaciones y modos al azar; se construye el 20 % y el resto son variantes mutadas. En cortes no densos, si una pieza no entra al azar se usa el mismo respaldo. Una ranura que no entra en mitades se deshace y va entera | Solo al azar: en un corte denso las primeras piezas quedaban desparramadas y las últimas no entraban (dos cortes de la tanda A no arrancaban en 800 cm) | `e32c211` |
| 10 | `limite_tiempo` opcional: instante de `time.monotonic()` a partir del cual no se empieza otra generación (siempre se completa al menos una) y se devuelve el mejor individuo hasta ahí. Distinto de `cancelado`, que lanza `CorteCancelado` y no devuelve resultado | Solo se cortaba por generaciones completas | `c3427f2` |

También `75d8569`: si no se logra armar un individuo inicial, se lanza `SinUbicacionValida`
(subclase de `RuntimeError`, como en v1.0.0) con `indices_entrada`, las entradas que no
encontraron lugar. Así el planificador puede partir el corte o dejar la pieza como no
resuelta, en lugar de que falle el trabajo.

## Optimización de cortes (`app/corte/`)

- `planificador.py`: decide qué piezas van juntas en cada tizada y cuántas capas lleva cada
  color (sin algoritmo genético). `python scripts/planificar.py preparacion.json` lo reporta.
- `tizada.py`: ubica cada corte con el algoritmo del vendor. La frontera de unidades está acá:
  el plan y el resultado van en cm y el vendor trabaja en mm (×10).
- `residuo.py`: optimizador de residuo (sustituciones), con la interfaz preparada; todavía no
  aplica nada.
- `python scripts/medir.py preparacion.json` mide el algoritmo sobre los cortes de una tanda.

## Levantar el servicio

```bash
cd services/pieza-geometria
python -m venv .venv
.venv/Scripts/activate   # Windows
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Swagger UI: http://localhost:8001/docs

## Tests

```bash
pip install -r requirements-dev.txt
python -m pytest tests
```

## Endpoints

- `POST /piezas/calcular-base`: arma el contorno a partir de una lista de segmentos
  (RECTA / ARCO / CIRCULO), tesselando los tramos curvos, y devuelve área/ancho/largo/
  perímetro calculados directamente sobre ese polígono (sin ningún buffer/margen).
- `POST /piezas/escalar`: escala un contorno ya calculado a un ancho/largo objetivo
  (por talle), anclando el escalado en la esquina inferior izquierda del bounding box.
