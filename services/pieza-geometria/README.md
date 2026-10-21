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
| 11 | **Corte por estancamiento del largo** (`corte_estancamiento`, `generaciones_minimas`, `umbral_estancamiento`, opcionales; apagado si `corte_estancamiento` es `None`): la corrida termina si pasan N generaciones seguidas sin que el **largo** del mejor individuo (`_individual_max_y`) baje al menos el umbral respecto del último largo que reinició la cuenta, una vez hechas las generaciones mínimas, y devuelve el mejor hasta ahí. Usa su propio contador, no el de `stagnation_limit` (que a las N generaciones sin mejorar el fitness agranda la mutación una generación): por eso N tiene que ser mayor que `stagnation_limit`. `resultado` (dict) se completa con `motivo` (`GENERACIONES`, `LIMITE_DE_TIEMPO` o `ESTANCAMIENTO`) y `generaciones`. Se mide el largo y no el fitness porque el fitness (compacidad, huecos) sigue mejorando de a poco aunque el largo ya no baje: una primera versión sobre el fitness nunca cortó | Solo se cortaba por generaciones completas o por tiempo | `8b98c91` (entró junto con otros cambios; la fila y los tests de borde, en el commit `vendor:` siguiente) |

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
- `python scripts/medir.py preparacion.json` mide el algoritmo sobre los cortes de una tanda; con
  `--comparar-estancamiento 50 80 --semillas 1 101` compara cada corte sin y con corte por
  estancamiento, con las mismas semillas.

### Reinicios y corte por estancamiento

Cada corte se ubica con varias corridas del algoritmo (reinicios con semilla, semilla + 1...)
dentro de su presupuesto de tiempo, y gana la tizada más corta que pasa la verificación. Tres
reglas deciden cuántas corridas entran; las tres son configurables por modo con el mismo patrón que
la eficiencia por tela: un valor por modo (`...PorModo`) y el valor explícito de la solicitud
(`ParametrosCorte`, en camelCase por la API), que manda sobre el del modo.

| Parámetro (solicitud / por modo) | RAPIDO | COMPLETO | Qué hace |
|---|---|---|---|
| `generacionesEstancamiento` / `generacionesEstancamientoPorModo` | 50 | 50 | **Corte por estancamiento** (cambio 11 del vendor): la corrida termina si pasan N generaciones seguidas sin que el largo (punto más alto de las piezas) baje al menos el umbral. `null` o `0` = apagado. Tiene que ser mayor que `limiteEstancamientoMutacion` (40, el `stagnation_limit` del vendor): con N ≤ 40 la corrida se cortaría antes del empujón de mutación del vendor |
| `umbralEstancamientoCm` / `umbralEstancamientoCmPorModo` | 0,1 cm | 0,1 cm | Cuánto tiene que acortarse el largo para reiniciar la cuenta del estancamiento (1 mm) |
| `generacionesMinimas` | 60 | 60 | Antes de esa generación no se corta por estancamiento aunque el largo esté plano: con N = 50 evita cortar por una meseta temprana |
| `reiniciosSinMejora` / `reiniciosSinMejoraPorModo` | 2 | 4 | **Corte anticipado**: si los últimos K reinicios seguidos no acortaron la mejor tizada más de `mejoraMinimaReinicio` (1 %), el corte termina aunque quede presupuesto |
| `factorTiempoReinicio` / `factorTiempoReinicioPorModo` | 1,0 | 0,8 | **Otro reinicio** arranca solo si lo que queda del presupuesto alcanza para factor × la duración de la corrida anterior |

Toda corrida cuenta como terminada para seguir reiniciando: la que hace sus generaciones, la que
corta el estancamiento (motivo `ESTANCAMIENTO`, estado `COMPLETO`) y la que corta el **límite duro
del presupuesto**, que no cambió: si una corrida arrancada con menos tiempo que la anterior no llega
a terminar, el límite la corta (motivo `LIMITE_DE_TIEMPO`), su tizada compite igual y no arranca
ninguna otra porque ya no queda presupuesto.

Con N = 0 los resultados con la misma semilla son idénticos a los de antes del cambio 11.

**Qué cambia en la práctica (COMPLETO).** Medido con K = 2 y factor 1,0, el estancamiento dejaba la
tela igual y bajaba el tiempo entre 10 y 17 % (`7c2730a`), pero ese tiempo no se usaba: en cortes
chicos frenaba el corte anticipado (siempre 3 corridas) y en densos la regla de "una corrida como la
anterior". Con K = 4 y factor 0,8 se aprovecha ese tiempo en más reinicios, así que **el tiempo
total de una tanda puede subir hacia el presupuesto por corte** (hasta 200 s × cantidad de cortes).
Lo que se espera mejorar es el **largo de los cortes chicos y medianos**, donde la variación entre
semillas es grande (en el corte 3 de la tanda A, de 176 a 204 cm según la semilla). Los cortes
densos llegan igual al límite de tiempo con una sola corrida. RAPIDO queda con K = 2 y factor 1,0.
Todavía no está medido con estos valores.

## Levantar el servicio

```bash
cd services/pieza-geometria
python -m venv .venv
.venv/Scripts/activate   # Windows
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Swagger UI: http://localhost:8001/docs

**Uvicorn tiene que correr con un solo worker** (es el default; no usar `--workers N`): los
trabajos de corte viven en la memoria del proceso y los corre un único hilo, de a uno. Con varios
workers, un `GET` podría llegar a un proceso que no tiene el trabajo. Si se reinicia el servicio,
los trabajos en curso y los resultados guardados se pierden.

Variable de entorno: `CORTE_VENCIMIENTO_MINUTOS` (60 por defecto): cuánto tiempo se guarda un
trabajo después de terminar (bien, con error o cancelado); pasado ese tiempo, `GET` da 404.

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
- `POST /corte/trabajos` (202): `{"preparacion": <contrato de preparacion-corte>, "parametros": {...}}`.
  Encola la optimización de los cortes de una tanda y devuelve `idTrabajo`, `estado` y
  `posicionEnCola`. Los `parametros` (camelCase) pisan los de la preparación: `modoTiempo`
  (`RAPIDO` 60 s por corte, `COMPLETO` hasta 200 s), `presupuestoSegundosPorCorte`,
  `excedenteMaximoPorItem`, `excedenteMaximoPorTela`, `modoObjetivo`, `semilla`,
  `generacionesEstancamiento`, `reiniciosSinMejora`, `factorTiempoReinicio` (ver "Reinicios y corte por
  estancamiento"), etc.
  422 si la preparación no tiene demanda o un parámetro es inválido.
- `GET /corte/trabajos/{id}`: `estado` (`EN_COLA`, `EJECUTANDO`, `TERMINADO`, `ERROR`,
  `CANCELADO`), `progreso` (corte, corrida y generación actuales, mejor largo y eficiencia del
  corte en curso, segundos transcurridos) y, si terminó, `resultado`: por corte la tela, el ancho,
  el largo usado, la eficiencia, el estado (`COMPLETO` o `LIMITE_DE_TIEMPO`), las corridas, las
  capas por color, las piezas ubicadas (con el polígono final en cm) y la producción frente a la
  demanda; más `noResueltas`, `advertencias` y un `resumen`. 404 si no existe o ya venció.
- `DELETE /corte/trabajos/{id}`: cancela. Si estaba en cola queda `CANCELADO` en el acto; si
  estaba corriendo, en cuanto el algoritmo revisa el indicador (al empezar cada generación o
  cada individuo de la población inicial). 409 si ya terminó.
