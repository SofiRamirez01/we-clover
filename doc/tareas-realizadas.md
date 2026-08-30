# Tareas realizadas

Changelog de lo ya construido en el sistema, con las decisiones y detalles técnicos no
obvios de cada feature (el "por qué", no solo el "qué"). Pensado para que una sesión futura
no tenga que releer el código para entender por qué algo quedó como quedó.

Lo que falta hacer vive aparte, en [pantallas-pendientes.md](pantallas-pendientes.md) — ese
archivo se mantiene corto a propósito, listando solo trabajo pendiente real. Cuando algo de
ahí se termine, se migra el detalle acá y se borra de pantallas-pendientes.

Orden: más reciente primero. Última actualización: 2026-08-30.

## Frontend de insumos secundarios: Capucha/Puños/libres en el modal de gotero (2026-08-30)

Se construyó la UI que faltaba en `ModalColoresGotero` para insumos secundarios genéricos
(el backend ya existía, ver la entrada de Producto Insumo Secundario más abajo). Nueva
sección "Insumos secundarios" en el modal, después de "Colores del diseño":
- **Capucha y Puños y cintura** (solo Buzo/Campera): cada una es una **lista** de filas
  (tipo de tela + color + cantidad), no una fila única — hace falta porque un mismo insumo
  puede necesitar más de un color (ej. un puño de un color y el otro de otro, o puños de un
  color y cintura de otro). Cada sección tiene su "+ Agregar color". Se precargan con lo que
  el backend haya sugerido al confirmar colores (`ProductoService.sugerirInsumoSecundario`,
  mismo nombre que el Color 1) y quedan vacías si no sugirió nada. Al agregar una fila nueva
  a mano con "+ Agregar color", el tipo de tela arranca precargado en Jersey (Capucha) o Ribb
  (Puños y cintura) — son las telas habituales para eso, pero el selector queda editable
  igual que cualquier otra fila, por si hace falta otra (corderito, estampado, etc.).
- **"+ Agregar insumo"** (cualquier tipo de prenda): filas totalmente libres, con un campo
  de texto para que el usuario escriba a mano de qué se trata (ej. "Cuello", "Botones") —
  sin ese campo no había forma de identificar para qué era cada insumo.
- **Cierre** sigue con exactamente la misma UI de siempre (sin cambios de UX), pero ahora se
  guarda junto con el resto en el mismo `PUT /api/productos/{id}/insumos-secundarios`, en vez
  de su `PATCH /color-cierre` dedicado (ese endpoint sigue existiendo, pero el modal dejó de
  llamarlo).
- Nuevo `services/tipoTelaService.ts` (`GET /api/tipos-tela`) y `types/tipoTela.ts` en el
  frontend — no existían, a pesar de que el backend ya los tenía.

**Cambio de modelo necesario a mitad de camino:** la primera versión de este fix agregó un
campo `descripcion` a `ProductoInsumoSecundario` con **unique(producto, descripcion)**, para
poder tener Capucha y un insumo libre compartiendo la misma tela (Jersey) sin chocar con la
vieja constraint `unique(producto, tipoTela)`. Servía para eso, pero seguía sin permitir dos
filas *dentro de la misma sección* (ej. dos "Puños y cintura" de distinto color) — exactamente
el caso real que motivó todo esto. Se sacó esa constraint por completo: hoy
`producto_insumos_secundarios` no tiene ninguna restricción de unicidad más que el id. La
identidad de cada fila es el conjunto (descripcion, tipoTela, color), no un campo por sí
solo, y esa combinación la arma el frontend.
- Migración manual en la base dev (dos pasos, en sesiones separadas): alta de la columna
  `descripcion` (`VARCHAR(100)`, backfill desde el `codigo` de cada fila existente según
  CIERRE→"Cierre"/JERSEY→"Capucha"/RIBB→"Puños y cintura", después `NOT NULL`), y más tarde
  se borró el unique que se había agregado. En ambos pasos hubo que agregar el índice nuevo
  *antes* de borrar el viejo — el viejo era el único índice que cubría `id_producto` como
  columna líder, y borrarlo primero rompe la FK `fk_pis_producto` (`Cannot drop index ...:
  needed in a foreign key constraint`). Mismo gotcha las dos veces.
- **Bug real encontrado y arreglado en el camino:** `ProductoService.actualizarInsumosSecundarios`
  reusaba `upsertInsumoSecundario` (que matchea por `descripcion`, pensado para el up-sert de
  Cierre/Capucha/Puños contra el estado ya persistido) para insertar también los items nuevos
  del `PUT`. Como esa función busca una fila existente por `descripcion` **dentro de la misma
  colección que se está armando en el loop**, al mandar dos items con la misma descripcion
  (ej. dos "Puños y cintura") el segundo pisaba al primero en vez de agregarse aparte — se
  guardaba solo uno de los dos colores, sin error. Se arregló haciendo que ese método
  construya las `ProductoInsumoSecundario` directamente con `.add(...)`, sin pasar por el
  upsert (que sigue existiendo tal cual para sus otros dos usos: la sugerencia automática al
  confirmar colores y `actualizarColorCierre`, ninguno de los dos arma listas con posibles
  descripciones repetidas en la misma llamada).
- Verificado con un producto real (Campera, id 20): Cierre + Capucha + dos filas "Puños y
  cintura" de distinto color + una fila libre "Cuello" compartiendo Jersey con Capucha, todo
  en el mismo `PUT`, sobrevivió completo. Se restauró el estado original del producto después.

## `numeroInterno` de moldería: unicidad por tipo de prenda, no global (2026-08-29)

Al principio (ver más abajo, "Patrones de Corte / Molderías") `numeroInterno` era `UNIQUE` a
nivel de columna en MySQL — pero en la práctica el negocio numera las molderías por
"familia" de tipo de prenda: la moldería #1 de Buzo/Campera y la moldería #1 de Chomba/Remera
son numeraciones independientes que no deberían chocar entre sí.

- Se sacó el `unique = true` de `PatronCorte.numeroInterno` y se borró a mano el índice único
  en la base dev (`ALTER TABLE patrones_corte DROP INDEX UKdu0384lky2tg86xs5373kojkb`) — 
  `ddl-auto=update` no borra constraints solo, hay que hacerlo manual (mismo patrón que otras
  migraciones de este proyecto).
- La validación real quedó en el service (`PatronCorteService.validarNumeroInternoDisponible`):
  dado el número pedido y los tipos de prenda de la moldería nueva, busca si ya existe alguna
  moldería con ese mismo número que comparta al menos un tipo de prenda
  (`PatronCorteRepository.findDistinctByNumeroInternoAndTiposPrenda_IdIn`). Si hay
  intersección, `409` con el detalle de para qué tipo(s) de prenda ya está tomado ese número;
  si no hay ningún tipo de prenda en común, se permite repetir el número sin problema.
- Como `PatronCorte`↔`TipoPrenda` es `ManyToMany` (una misma moldería puede aplicar a varios
  tipos a la vez), la unicidad no se puede modelar con un constraint simple de base — por eso
  quedó como validación de negocio en el service, no en el esquema.
- Verificado con la moldería real "#1 Clasica" (Buzo/Campera): crear una nueva moldería con
  número 1 para Chomba funcionó (sin superposición de tipos); crear una con número 1 para
  Campera fue rechazada con 409 (`"Ya existe una moldería con el número 1 para: Campera"`).
- Sin cambios de contrato para el frontend: `CargaPatronCorteForm.tsx` no valida unicidad del
  lado del cliente, solo formato numérico — el mensaje de error de negocio ya viaja igual que
  cualquier otro (`extraerMensajeError`).

## `PedidoService.actualizarPedido` matchea productos por id en vez de recrearlos (2026-08-29)

Fix del riesgo documentado más abajo (y agravado por la feature anterior de Moldería): editar
un pedido desde `NuevoPedidoView` recreaba **todos** sus `Producto` de cero en cada guardado
(`pedido.getProductos().clear()` + alta nueva), lo que borraba en cada edición del pedido
todo lo que se carga aparte desde Ficha Técnica — moldería, tela, imagen, estado de
producción y colores marcados con el gotero.

- **`ProductoCreateRequest` ahora tiene un campo `id` (nullable)**, ausente para una prenda
  nueva. Solo lo usa `actualizarPedido`; `crearPedido` lo ignora porque ahí todos los
  productos son nuevos por definición.
- **`actualizarPedido` matchea por ese id**: si viene, actualiza in-place el `Producto`
  existente (solo los campos editables desde este formulario: tipo de prenda, cantidad,
  costo, observaciones) sin tocar moldería/tela/imagen/estado/colores. Si no viene, crea un
  producto nuevo igual que antes. Un producto solo se borra si su id deja de estar en el
  payload — es decir, si el usuario lo quita explícitamente con el botón "×" de la Sección 3
  del formulario (`quitarPrenda`) — y ese borrado sí se lleva todo lo que dependía de él
  (moldería, colores, insumos secundarios) porque el `Producto` en sí ya no existe. Es el
  comportamiento pedido: la moldería solo se pierde si se borra el producto al que estaba
  asignada.
- **Frontend:** `PrendaRow` ahora guarda el `idProducto` real (distinto del `id` local
  `crypto.randomUUID()` usado como key de React) para poder mandarlo de vuelta al editar; se
  completa desde `pedido.productos[].id` al abrir el formulario de edición y viaja como `id`
  en cada producto del payload de `PUT /api/pedidos/{id}`. En el alta (`POST`) simplemente no
  se manda (siempre `undefined`), sin necesidad de ramificar el código entre alta y edición.
- Verificado con un pedido real con dos productos (uno con moldería, tela, colorCierre,
  colores marcados e imagen): editar cambiando solo la cantidad de un producto dejó intacto
  todo lo demás en ambos productos; quitar uno de los dos productos del formulario lo borró
  junto con su moldería y colores sin afectar al que quedó, sin errores de FK.
- Elimina también la necesidad de la advertencia de pantallas-pendientes.md sobre este mismo
  riesgo (ver abajo, esa sección se borra).

## Moldería opcional en el alta del pedido, elegible después desde Ficha Técnica (2026-08-29)

`PatronCorte` ("Moldería" en el frontend — es la jerga que usa el negocio, `PatronCorte` es
solo el nombre interno de la entidad) ya no se elige al cargar el pedido: se saca el
desplegable de `NuevoPedidoView` y se agrega uno nuevo en `ModalColoresGotero` (arriba de
Tela), para elegirla junto con el resto de los datos de Ficha Técnica.

- **Contrato de `POST /api/pedidos` sin cambios, a propósito** (se pidió explícitamente no
  romper nada ahí): `ProductoCreateRequest.idPatronCorte` pasó de `@NotNull` a opcional —
  mismo endpoint, mismo shape, ahora acepta el campo ausente o `null`.
- **Endpoint nuevo:** `PATCH /api/productos/{id}/patron-corte`, body `{ idPatronCorte }`
  (puede ser `null` para desasignarla), mismos roles que el resto de Ficha Técnica. Valida
  que la moldería elegida aplique al tipo de prenda del producto (`PatronCorte.tiposPrenda`
  debe contenerlo), si no `409`.
- **Si la moldería cambia (a otra distinta, o a ninguna), se borran los colores ya
  marcados** (`producto.getColores().clear()`): quedaban atados a `PatronCorteColor` de la
  moldería anterior, que ya no existen para este producto. Verificado con una Campera real:
  asignar moldería A → marcar colores → cambiar a moldería B → los colores vuelven a cero.
- **Frontend:** `ModalColoresGotero` persiste el cambio de moldería al toque (no es
  "borrador hasta Guardar" como Tela/Cierre) porque determina directamente qué posiciones
  existen para marcar colores — dejarlo como borrador mostraría posiciones desactualizadas
  hasta guardar. El mensaje de "no se puede marcar colores" en esa sección se actualizó para
  decir "Elegí la Moldería arriba" en vez de "elegilo desde Pedidos" (ya no aplica).
- El riesgo de que esto se perdiera al editar el pedido (porque `NuevoPedidoView` no manda
  `idPatronCorte`) se resolvió aparte el mismo día — ver la entrada de arriba
  ("`PedidoService.actualizarPedido` matchea productos por id").

## Insumos secundarios genéricos: catálogo de telas y reemplazo completo por producto (2026-08-29)

Continúa el refactor de `TipoTela`/`ProductoInsumoSecundario` de más abajo: generaliza el
mecanismo más allá del Cierre, preparando el terreno para Capucha (Jersey) y Puños/cintura
(Ribb) en Buzo y Campera.

- **`GET /api/tipos-tela`:** catálogo de `TipoTela` activos, mismo patrón sin restricción de
  rol que `GET /api/tipos-prenda` (solo lectura, baja sensibilidad).
- **`PUT /api/productos/{id}/insumos-secundarios`:** reemplaza el set completo de
  `ProductoInsumoSecundario` de un producto (borra e inserta, mismo criterio que
  `asignarColores` para los colores del patrón, incluido el flush intermedio por la unique
  constraint). A propósito **sin** restricción de qué tipo de prenda puede tener qué
  insumo — verificado agregando un Cierre a mano a un Buzo vía este endpoint, que lo acepta
  sin problema; esa regla ("solo Campera tiene Cierre") sigue viviendo nada más en
  `actualizarColorCierre`, que se mantuvo intacto y en paralelo para no duplicarla.
- **Sugerencia automática generalizada en `asignarColores`:** además del default de Cierre
  que ya existía, ahora también sugiere Jersey (Capucha) y Ribb (Puños/cintura) para
  Buzo y Campera, buscando un color con el mismo nombre que el "Color 1" recién elegido
  dentro de cada categoría. La cantidad sugerida sale de `TipoTela.gramosSugerido`
  (Jersey=70, Ribb=60) si `esPorPeso`, o `1` si no (Cierre) — una sola regla de datos, sin
  hardcodear cantidades por tipo. Qué prendas sugieren qué insumos vive en un
  `Map<String, List<String>>` (`Buzo→[JERSEY,RIBB]`, `Campera→[CIERRE,JERSEY,RIBB]`) — es el
  único lugar de código que hay que tocar si el negocio agrega una prenda con insumos
  sugeridos propios. Nunca pisa una elección manual ya guardada (mismo criterio que Cierre).
  Verificado con un Buzo real: al confirmar colores, aparecieron Jersey y Ribb sugeridos
  automáticamente con el color y la cantidad correctos.
- **`ProductoResponse.insumosSecundarios`** (nuevo campo, lista) expone todos los insumos
  del producto con su tipo de tela, color y cantidad — `idColorCierre`/`nombreColorCierre`/
  `hexColorCierre` se mantienen sin cambios en paralelo, por compatibilidad con lo que ya
  lee el frontend.
- **Bug real encontrado y corregido en el camino:** al reemplazar el color de un insumo ya
  existente (mismo `tipoTela`, color distinto) tiraba `500` por la misma razón que ya se
  había resuelto para `producto_colores` — unique constraint `(id_producto, id_tipo_tela)`
  violada porque Hibernate intenta el INSERT antes que el DELETE del huérfano. Mismo fix:
  `clear()` + `saveAndFlush()` antes de reconstruir la lista.
- La **UI del modal para Capucha/Puños/insumos libres todavía no se construyó** — ver
  pantallas-pendientes.md. Esta entrega quedó 100% del lado del backend.

## `TipoTela` pasa de enum a entidad + `ProductoInsumoSecundario` (2026-08-29)

Refactor de base de datos: la empresa va a seguir sumando tipos de tela/insumo con el
tiempo (Ribb ya hacía falta, después va a aparecer Corderito, etc.) y cada alta no debería
requerir tocar código Java ni redeployar. `TipoTela` dejó de ser un enum de Java
(`FRIZA, JERSEY, PIQUE, SPUM, CIERRE`) y pasó a ser una entidad (`tipos_tela`), mismo
criterio que ya existía para `TipoPrenda`.

- **No hay Flyway ni Liquibase en este proyecto** (solo `ddl-auto=update`) — se confirmó
  antes de tocar nada. El criterio de migración sigue siendo el mismo que ya se usó en
  cambios anteriores: SQL manual contra la base de dev (nullable → backfill → constraint),
  documentado acá.
- **Contrato de API preservado a propósito:** el front sigue mandando/recibiendo
  `tipoTela` como string en el mismo formato de siempre ("FRIZA", "JERSEY", ...) en todos
  lados (`GET /api/paleta-colores?tipoTela=X`, `PATCH /api/productos/{id}/tipo-tela`,
  `PaletaColorCreateRequest.tipoTela`, `ProductoResponse.tipoTela`). Para lograrlo sin
  perder la posibilidad de tener nombres humanos-legibles a futuro, `TipoTela` tiene dos
  campos separados: `codigo` (único, formato viejo tipo enum — "FRIZA", "RIBB", "CIERRE" —
  es lo único que ve la API hoy) y `nombre` (humano-legible — "Friza", "Ribb", "Cierre" —
  todavía sin usar en el front, para cuando se toque esa pantalla). Esta separación se le
  planteó explícitamente al usuario antes de implementar (arriesgaba romper en silencio
  todas las comparaciones hardcodeadas del front actual si se exponía `nombre` directo) y
  se confirmó `codigo` como la solución.
- **Nuevos campos de `TipoTela`:** `esPorPeso` (compra por gramos vs. por unidad) y
  `telaCuerpo` (puede ser la tela principal del cuerpo de una prenda vs. solo insumo
  secundario). `ProductoService.actualizarTipoTela` generalizó la regla vieja ("nunca
  CIERRE") a "nunca `telaCuerpo=false`", así que ya rechaza Cierre y Ribb por igual sin
  código nuevo — verificado con ambos.
- **`ProductoInsumoSecundario`** (tabla `producto_insumos_secundarios`, unique en
  `(id_producto, id_tipo_tela)`) reemplaza al viejo `Producto.colorCierre`. El endpoint
  `PATCH /api/productos/{id}/color-cierre` mantiene el mismo contrato de request/response
  de siempre — por dentro hace un upsert de `ProductoInsumoSecundario` con
  `tipoTela=Cierre` y `cantidad=1` en vez de setear un campo directo. El default
  automático que ya existía en `asignarColores` (copiar el nombre del "Color 1" a la
  categoría Cierre) se mantiene igual, ahora sobre la tabla nueva.
- **Bug real encontrado en el camino:** `PedidoMapper` arma `PedidoResponse.productos` vía
  `ProductoMapper` directo (MapStruct puro) — como `idColorCierre`/`nombreColorCierre`/
  `hexColorCierre` ya no son un campo directo de `Producto` sino que salen de
  `ProductoInsumoSecundario`, MapStruct no los podía completar solo. Sin el fix,
  `GET /api/pedidos` (lo que lee `FichasTecnicasView` para mostrar "Cierre: X" en cada
  card) hubiera devuelto esos tres campos siempre en `null`, aunque
  `GET/PATCH /api/productos/{id}` sí los mostrara bien. Fix: `ProductoService` expone
  públicamente su `construirRespuesta(Producto)` (que sí completa esos campos leyendo
  `ProductoInsumoSecundario`), y `PedidoService.construirRespuesta` ahora reconstruye la
  lista de productos llamando a ese método en vez de confiar en el mapeo anidado
  automático de `PedidoMapper`. Se verificó explícitamente con `GET /api/pedidos` antes de
  cerrar el cambio, ya que era justo el flujo que el criterio de aceptación pedía no
  romper.
- **Migración manual de datos (2026-08-29):** se creó `tipos_tela` y se sembraron sus 6
  filas (Friza/Jersey/Piqué/Spum/Ribb/Cierre) antes de tocar nada más. Recién con esa
  tabla poblada se migraron `paleta_colores.tipo_tela` (varchar) y `productos.tipo_tela`
  (enum nativo de MySQL, no varchar — Hibernate lo había mapeado así) a `id_tipo_tela`
  (FK), haciendo `JOIN` contra `tipos_tela.codigo` para resolver el id de cada fila
  existente. Se verificó `0` filas sin migrar antes de poner `NOT NULL`/dropear la columna
  vieja. Para el color de cierre: se creó `producto_insumos_secundarios` a mano (antes de
  que el código Java lo generara vía `ddl-auto=update`) y se migraron ahí los 3 productos
  reales que tenían `id_color_cierre` seteado, recién después se dropeó esa columna. No se
  perdió ningún dato real (4 productos con tela, 3 con cierre).
- **Verificado end-to-end tras el refactor:** cambiar tipo de tela de un producto, ver/
  cambiar el color de cierre de una Campera (con upsert real, sin duplicar filas), rechazo
  de Cierre/Ribb como tela de cuerpo, y alta de un color de paleta nuevo bajo un tipo de
  tela (`RIBB`) sin haber tocado el código Java para ese tipo — la prueba concreta de que
  el objetivo del refactor (sumar tipos sin redeploy) funciona.

## `PatronCorte` ↔ `TipoPrenda`: de uno a muchos-a-muchos (2026-08-28)

Un mismo patrón de corte puede aplicar a varios tipos de prenda a la vez (ej. "Clásica" es
la misma moldería y el mismo consumo de tela para Buzo y para Campera — lo único que las
distingue, si lleva cierre o no, ya lo define el tipo de prenda elegido en el `Producto`, a
propósito **no** se agregó un campo booleano separado para eso).

- **`PatronCorte.tiposPrenda`** pasó de `@ManyToOne` a `@ManyToMany` con tabla intermedia
  `patron_corte_tipo_prenda` (`id_patron_corte`, `id_tipo_prenda`, PK compuesta).
  `PatronCorteCreateRequest.idsTipoPrenda` (antes `idTipoPrenda` suelto) exige al menos un id
  (`@NotEmpty`); el service dedupea (`.distinct()`) y valida que cada id exista.
- **`GET /api/patrones-corte?idTipoPrenda=X`** (nuevo query param opcional) devuelve los
  patrones donde ese tipo está entre los aplicables
  (`findByActivoTrueAndTiposPrenda_IdOrderByNombreAsc`, derived query de Spring Data sobre la
  relación ManyToMany). Si un patrón aplica a Buzo y Campera, aparece en el filtro de ambos
  por igual — verificado con un patrón de prueba real contra la base.
- **Migración manual de datos (2026-08-28):** había 6 filas reales en `patrones_corte` con
  `id_tipo_prenda` seteado (5 Camperas + 1 Chomba). Se creó la tabla intermedia, se
  copiaron esas 6 asociaciones (`INSERT ... SELECT id, id_tipo_prenda FROM patrones_corte`),
  y recién ahí se borró la FK y la columna vieja `id_tipo_prenda` de `patrones_corte` — sin
  este orden (migrar antes de dropear) se perdía la asociación existente. Ninguna fila se
  descartó.
- **Frontend:** `PatronCorteResponse.tiposPrenda` es ahora un array (`TipoPrendaOption[]`,
  reusa el tipo que ya existía en `types/pedido.ts`) en vez de `idTipoPrenda`/`tipoPrenda`
  sueltos. `CargaPatronCorteForm.tsx` cambió el `<select>` único por checkboxes (una fila
  puede tildar varios tipos). `MolderiasListView.tsx` muestra los tipos unidos con `" / "`
  (`formatearTipos`) y el filtro por tipo usa `.some()` en vez de comparación directa.
  `NuevoPedidoView.tsx`: el filtro de Patrón de Corte por tipo de prenda de cada fila (ver
  entrada de "Colores por gotero en Ficha Técnica" más abajo) sigue funcionando igual que
  antes para quien lo usa — solo cambió el `.filter()` interno a chequear membership en el
  array en vez de comparar un id suelto.

## Guardar/Cancelar explícitos en el modal de ficha, fix de zoom (2026-08-26)

Tela y Cierre dejaron de guardarse solos con el cambio inmediato que tenían antes — el
usuario los cambiaba y la única acción disponible era cerrar, sin feedback de si había
quedado guardado. Se cambió el modelo a "borrador + guardar explícito":

- **Tela y Cierre son borrador local** (`telaDraft`/`cierreDraft` en `ModalColoresGotero`)
  hasta que se aprieta "Guardar": no se llama a `PATCH /api/productos/{id}/tipo-tela` ni
  `PATCH /api/productos/{id}/color-cierre` con cada cambio de selector.
- **Footer del modal, abajo a la derecha:** "Cancelar" (nunca toca el servidor) y "Guardar"
  (persiste tela y/o cierre si cambiaron, en un solo click, y cierra el modal). El flujo de
  "Confirmar colores" del click a click sigue siendo aparte — ese sí guarda al toque.
- **Excepción:** si se arranca a marcar colores ("Marcar colores"/"Rehacer colores") con una
  tela todavía sin guardar, esa tela se persiste automáticamente antes de abrir el flujo de
  clicks — los `ProductoColor` que se van a guardar quedan atados a la paleta de esa tela, no
  puede quedar como un borrador suelto mientras tanto.
- **Bug de zoom corregido:** al 100% se veía solo aprox. 1/4 de la imagen, porque el
  `<canvas>` se mostraba a resolución nativa del archivo (una foto de celular puede tener
  miles de px de ancho) dentro de un contenedor mucho más chico. Ahora "100%" significa
  "ajustar la imagen completa a la ventana" (`escalaAjuste`, calculado una vez por imagen
  comparando su tamaño natural contra el contenedor) y el zoom del usuario es un
  multiplicador sobre esa base, de 10% a 300%.

## Modal de ficha rediseñado: ícono de lápiz, dos columnas, zoom (2026-08-26)

Se unificaron Cargar, Reemplazar y Editar en un solo punto de entrada.

- **Ícono de lápiz** (arriba a la derecha de cada card, `position: absolute`, solo visible
  con permiso) reemplaza el botón de texto "Editar" y también a los botones
  "Cargar"/"Reemplazar" que antes vivían sueltos en la card — es el único disparador del
  modal (`ModalColoresGotero`), tanto para la primera carga de imagen como para
  reemplazarla o editar tela/cierre/colores.
- **Layout de dos columnas dentro del modal** (`grid md:grid-cols-2`): imagen a la izquierda
  (con controles de zoom −/100%/+ implementados con `transform: scale()` sobre el
  `<canvas>`/`<img>` dentro de un contenedor `overflow-auto` — el cálculo de click-a-pixel
  del gotero sigue funcionando solo porque usa `getBoundingClientRect()`, que ya refleja el
  tamaño escalado); Tela, Cierre y Colores apilados verticalmente a la derecha, siempre en
  el mismo popup.
- **Subir/reemplazar imagen vive adentro del modal:** si el producto no tiene imagen
  todavía, la columna izquierda muestra un dropzone con botón "Cargar imagen"; si ya tiene,
  muestra la imagen con zoom y un link "Reemplazar imagen" debajo. Ambos casos usan el mismo
  `POST /api/productos/{id}/imagen` de siempre.
- Como ya no existe un trigger "recién subida vs. edición" (todo pasa por el mismo ícono de
  lápiz), se eliminó por completo la lógica que borraba la imagen del servidor al cancelar
  (`DELETE /api/productos/{id}/imagen` ya no se llama desde el modal). Cerrar/Cancelar
  **nunca** toca el servidor — tiene sentido porque la card de solo lectura ya sabe mostrar
  "Sin definir" para tela/cierre/colores faltantes, así que una ficha a medio completar dejó
  de ser un estado "inconsistente" a evitar y pasó a ser un estado normal y visible.
- **Si el producto ya tiene colores asignados**, el modal no fuerza a rehacer todo el click a
  click: muestra primero el resumen ya guardado (con gramos) y un botón "Rehacer colores" que
  recién ahí arranca la secuencia de posiciones desde cero (no prellena — ver pendiente
  relacionado en pantallas-pendientes.md).
- **Gramos visibles en la card:** cada línea "Color N" muestra los gramos de esa posición del
  patrón de corte (ej. "Color 1 (450 g): Marino"), tomados de `patronCorteColores`.

## Selector de tela/cierre movido adentro del modal, editar ficha existente (2026-08-26)

Los selectores de "Tela" y "Cierre" se sacaron de la card de `FichasTecnicasView` (ahí ahora
son de solo lectura) y se movieron adentro de `ModalColoresGotero`, junto con la posibilidad
de editar una ficha ya cargada. (Superado en parte por el rediseño de dos columnas del mismo
día, arriba — se deja este registro por el detalle de las decisiones de esa etapa.)

- **Card 100% de solo lectura:** siempre muestra "Tela: X" (o "Sin definir"), "Cierre: X"
  (solo si es Campera) y una línea "Color N: X" por **cada posición del patrón de corte**
  (`producto.patronCorteColores`, no solo las ya asignadas) — así se ve de un vistazo qué
  falta cargar aunque el valor todavía no exista.
- **Selector de Tela dentro del modal:** ya no es precondición para abrir el modal — antes,
  si un producto no tenía tela resuelta (ej. Bandera sin elegir) no se podía ni abrir. Cambiar
  la tela reinicia el progreso de colores ya marcados en esa sesión del modal (la paleta
  cambia con la tela, así que un color ya elegido con la tela vieja ya no es válido).
- **Selector de Cierre dentro del modal (solo Campera):** se autocompleta recién cuando se
  confirma la posición 1 (busca en `coloresCierre` un color con el mismo *nombre* que el
  Color 1 recién elegido) — se puede corregir a mano en cualquier momento después.
- **Defaults de tela por tipo de prenda ampliados:** las 5 prendas del catálogo tienen
  default: `Buzo → FRIZA`, `Remera → JERSEY`, `Chomba → PIQUE`, `Campera → FRIZA`,
  `Bandera → SPUM` (tela nueva, agregada como quinto valor del enum `TipoTela` junto a
  FRIZA/JERSEY/PIQUE/CIERRE; `DataInitializer` la siembra sola porque itera
  `TipoTela.values()`, no hizo falta tocar el seed a mano).

## Tipo de tela y color de cierre (2026-08-25)

`PaletaColores` tiene un campo `tipoTela` (enum `TipoTela { FRIZA, JERSEY, PIQUE, SPUM,
CIERRE }`): cada fila del catálogo queda atada a una tela específica (o a "cierre"), así que
el mismo nombre de color existe repetido en varias filas (ej. "Marino" en FRIZA, otra fila
"Marino" en JERSEY, etc.) — son insumos de compra distintos aunque el swatch se vea igual.
Esto fue una decisión explícita del usuario (se le preguntó si prefería este modelo o tener
`tipoTela` como campo de `Producto` en vez del catálogo, y eligió esto último para poder
"unificar después tipo de tela y pedido", pensando en el futuro Planificador de Compras de
M3).

- **Migración manual de datos (una sola vez, 2026-08-25):** `paleta_colores` ya tenía 11
  filas reales (10 del seed original + "Natural", creado por el usuario probando el modal de
  gotero). Se migró a mano en vez de dejar que `ddl-auto=update` agregue la columna
  `tipo_tela` como `NOT NULL` directamente: se agregó nullable, se backfillearon esas 11
  filas a `FRIZA`, se reemplazó el índice único de `nombre` solo por uno compuesto
  `(nombre, tipo_tela)`, y recién ahí se puso `NOT NULL`. `DataInitializer` siembra el resto
  de las combinaciones de forma idempotente por `(nombre, tipoTela)`, no por `count()==0`
  (ya no sirve, la tabla no arranca en cero).
- **`Producto.tipoTela`** (mismo enum, nunca debe valer `CIERRE`, se valida en
  `ProductoService.actualizarTipoTela`) se precarga con un default al crear el pedido según
  `TIPO_TELA_POR_DEFECTO` en `PedidoService`. Editable después desde Ficha Técnica
  (`PATCH /api/productos/{id}/tipo-tela`, mismos roles que cargar la imagen); **no** se
  agregó a `NuevoPedidoView` — la decisión fue que este campo es cosa de Ficha Técnica, no
  del alta del pedido.
- **Color de cierre:** solo para `Producto` con `tipoPrenda == "Campera"`
  (`ProductoService.esCampera`). Campo `Producto.colorCierre` (FK a `PaletaColores`, debe
  tener `tipoTela == CIERRE`, si no `409`). Al confirmar los colores del gotero
  (`POST /api/productos/{id}/colores`), si la prenda es Campera y todavía no tiene cierre
  elegido, se le asigna automáticamente el color de tipo CIERRE que tenga el mismo *nombre*
  que el "Color 1" recién elegido — si no existe un color de cierre con ese nombre exacto,
  queda sin definir. Nunca pisa una elección manual ya guardada. Editable aparte con
  `PATCH /api/productos/{id}/color-cierre`.
- **Filtros de Ficha Técnica:** el filtro de "Color" se cambió de filtrar por `idPaletaColor`
  a filtrar por *nombre* de color (deduplicado, excluyendo los de tipo CIERRE), porque con el
  catálogo repetido por tela el mismo nombre podía aparecer 3-4 veces en el combo con ids
  distintos. Filtro de "Tela" (Friza/Jersey/Piqué/Spum, sin Cierre) que, a diferencia de los
  demás filtros (que ocultan pedidos enteros), filtra a **nivel producto**: si el pedido
  tiene una Campera Friza y una Chomba Piqué y se filtra por Friza, la card del pedido se
  sigue mostrando pero solo con el slot de la Campera.

## Colores por gotero en Ficha Técnica (2026-08-25)

Al cargar/reemplazar la imagen de diseño de un `Producto`, si el producto tiene un
`PatronCorte` asignado, se abre `ModalColoresGotero.tsx`: un modal donde se hace click sobre
la imagen para asignar, una por una, el color real de cada posición del patrón de corte.

- **Modelo:** `Producto.patronCorte` (ManyToOne a `PatronCorte`, mismo criterio que
  `tipoPrenda`: nullable en base por productos legacy, obligatorio en
  `ProductoCreateRequest`). `PaletaColores` (catálogo de colores estándar de la empresa).
  `ProductoColor` (el color real asignado a una posición puntual: `producto` +
  `patronCorteColor` (no un `orden` suelto, para quedar atado al patrón exacto) +
  `paletaColor` + `metodoDeteccion` (enum, siempre `MANUAL` por ahora) + `coordenadaX/Y` +
  `rgbDetectado`, estos tres últimos guardados desde ya para auditoría y para una futura
  detección automática por clustering, fuera de alcance ahora). `uniqueConstraint` sobre
  (producto, patronCorteColor).
- **Endpoint `POST /api/productos/{id}/colores`:** reemplaza el set completo de colores del
  producto (borra e inserta). Valida que el array tenga exactamente la cantidad de
  `PatronCorteColor` del patrón del producto y que cada posición pertenezca a ese patrón; si
  no, `409 BusinessRuleException`. Mismos roles que cargar la imagen
  (`ROLE_ADMINISTRATIVO`/`ROLE_VENDEDOR`/`ROLE_DISENADOR`).
- **Bug real encontrado y corregido:** reemplazar el color de una posición ya asignada tiraba
  `500` por violación de la unique constraint (producto, patronCorteColor) — Hibernate
  ejecuta los INSERT de una colección antes que los DELETE de los elementos removidos por
  `orphanRemoval`, así que la fila nueva chocaba con la vieja todavía no borrada. Fix:
  `producto.getColores().clear(); productoRepository.saveAndFlush(producto);` antes de armar
  la lista nueva, para forzar el DELETE antes de los INSERT.
- **`GET/POST /api/paleta-colores`:** el `GET` quedó sin restringir a propósito, mismo
  criterio que `GET /api/tipos-prenda`. El `POST` (alta de un color nuevo desde el modal,
  cuando el detectado no matchea ninguno existente) está restringido a los mismos roles que
  cargan la imagen. Al crear un color nuevo, el selector de hex arranca precargado con el
  color exacto detectado en el pixel (RGB→hex), no en negro por defecto — bug real
  reportado y corregido (antes, si no se tocaba el selector manualmente, se guardaba negro).
- **"Cancelar" en el modal (versión original) descartaba la imagen subida:** endpoint
  `DELETE /api/productos/{id}/imagen` (mismos roles) que limpia `imagenDisenoUrl` a null. El
  archivo en disco no se borra físicamente, mismo criterio ya aceptado para Molderías. (Este
  comportamiento después se eliminó del todo — ver el rediseño del modal más arriba.)
- **Visibilidad de `GET /api/patrones-corte` (listado y detalle) ampliada:** antes exclusiva
  de `ROLE_ADMINISTRATIVO`, ahora también `ROLE_VENDEDOR` (necesita elegir el patrón al
  cargar un pedido) y `ROLE_DISENADOR` (necesita leer las posiciones de color para el modal).
  El alta (`POST`) sigue exclusiva de `ROLE_ADMINISTRATIVO`.
- **Fix de CORS necesario para que el gotero funcione:** `CorsConfig.java` solo cubría
  `/api/**`; sin CORS en `/uploads/**` (donde se sirven las imágenes), dibujar la imagen en
  un `<canvas>` y leer píxeles con `getImageData()` tira `SecurityError` (canvas "tainted")
  porque el navegador la ve como recurso cross-origin sin cabeceras. Se agregó CORS también
  para `/uploads/**`.
- **Matching de color desacoplado:** `frontend/src/utils/colorMatch.ts` (RGB del pixel →
  color de paleta más cercano por distancia euclídea) es una función pura, sin dependencia
  del modal, para poder reusarla después desde una sugerencia automática sin duplicar
  lógica.
- **Efecto colateral en el alta/edición de pedidos:** al ser `idPatronCorte` obligatorio en
  `ProductoCreateRequest`, `NuevoPedidoView.tsx` tiene una columna "Patrón de Corte" por cada
  prenda, filtrada por el tipo de prenda ya elegido en esa fila (un `PatronCorte` tiene un
  `tipoPrenda` fijo). Si no hay ningún patrón cargado para ese tipo, el combo queda
  deshabilitado con el aviso "Sin patrones para este tipo" y el submit se bloquea.

## Estado de producción por prenda (2026-08-25)

Cada `Producto` (prenda) tiene su propio `estadoActual` (mismo enum `EstadoPedido` que usa
`Pedido.estadoActual`), independiente del estado "general" del pedido — cambiar el de una
prenda no toca el del pedido, tal como se pidió explícitamente. Se ve como un select con el
mismo color que `EstadoBadge` en cada mini-slot de la pestaña Ficha Técnica.

- **Roles habilitados para cambiar el estado de una prenda:** `ROLE_ADMINISTRATIVO` y
  `ROLE_PLANTA` (distinto del set que puede cargar la imagen de diseño —
  `ROLE_ADMINISTRATIVO`/`ROLE_VENDEDOR`/`ROLE_DISENADOR` — decisión explícita: quien produce
  es quien reporta en qué etapa está cada prenda). Endpoint `PATCH /api/productos/{id}/estado`,
  mismo patrón de autorización que el resto (`AutorizacionService.verificarRolPermitido`).
- **Al crear un producto nuevo**, su `estadoActual` arranca igual al estado que tenga el
  pedido en ese momento (`request.estado()`), tanto en alta como en edición de pedido.

## Ficha Técnica (nueva pestaña, 2026-08-25): portfolio de diseños por prenda

Pestaña nueva en el sidebar raíz (visible para todos los roles logueados, sin gate de
Config), `frontend/src/features/fichas-tecnicas/FichasTecnicasView.tsx`. Reutiliza
`GET /api/pedidos` (no hay endpoint nuevo de listado) y muestra una tarjeta por **pedido**
(no por prenda) con Nº de Ficha, Colegio/Localidad y Estado, y adentro un "mini-slot" por
cada `Producto` del pedido (tipo de prenda + cantidad + imagen). A propósito **no muestra
precios** (eso vive en la pestaña Pedidos).

- **Endpoint:** `POST /api/productos/{id}/imagen` (multipart, campo `imagen`), sube a
  `Producto.imagenDisenoUrl`. Reutiliza `AlmacenamientoImagenService` (extraído de
  `PatronCorteService` a un servicio compartido en este mismo cambio, para no duplicar la
  validación JPG/PNG + guardado en disco). Carpeta: `backend/uploads/fichas-tecnicas/`.
- **Roles habilitados para cargar/reemplazar la imagen:** `ROLE_ADMINISTRATIVO`,
  `ROLE_VENDEDOR` y `ROLE_DISENADOR` (rol nuevo, creado en `DataInitializer` de forma
  idempotente vía `findByNombre` porque los demás roles de este entorno ya existían fuera
  del seed). El resto de los roles logueados (`ROLE_PLANTA`, `ROLE_COBRANZAS`) **ven** el
  portfolio pero no tienen el botón de carga — decisión explícita, distinta de la
  visibilidad de la pestaña en sí (esa es para todos). Verificado también server-side.
- Placeholder **"No cargado"** se muestra siempre que `imagenDisenoUrl` sea null o la imagen
  falle al cargar, para que todas las prendas sin diseño se vean igual.
- Sin filtro de tipo de prenda acá (a diferencia de Molderías) — solo Buscar (ficha o
  colegio), Estado, Color y Tela (estos dos últimos agregados después). Se listan todos los
  pedidos sin restringir por estado por defecto (se preguntó explícitamente y se eligió esta
  opción sobre "solo Listo para Producción en adelante").

## Patrones de Corte / Molderías (módulo M2, 2026-08-25)

`PatronCorte`/`PatronCorteColor` (gramos de Friza por color, pensado para el cálculo de
compras por color de M3) se agregaron como entidades **nuevas y separadas** del diagrama de
clases de CLAUDE.md (que modela la moldería como `Molderia`/`PiezaMolderia`, con
`geometriaPoligono` para el nesting de M4) — resuelven necesidades distintas (compras por
color vs. nesting geométrico), no son un reemplazo.

- **`PatronCorte` tiene `numeroInterno`** (`Integer`, obligatorio, lo setea el usuario en el
  alta — ⚠️ dejó de ser `UNIQUE` a nivel de base el 2026-08-29, ver la entrada de arriba
  "`numeroInterno` de moldería: unicidad por tipo de prenda, no global") y una FK real a
  `TipoPrenda` (el mismo catálogo de 5
  valores que usa `Producto`, combo en el front vía `GET /api/tipos-prenda`). Al agregar
  estas dos columnas `NOT NULL` se borraron 2 filas de prueba que había en
  `patrones_corte`/`patron_corte_colores` (datos de testing de esa sesión, no del negocio)
  para evitar el problema de `ddl-auto=update` con columnas `NOT NULL` sobre filas
  existentes.
- **Pestaña "Molderías" es un portfolio:** `MolderiasListView.tsx` (grilla de tarjetas con
  imagen, `#numeroInterno nombre`, tipo de prenda, cantidad de colores y gramos por color,
  con buscador + filtro por tipo de prenda + filtro por cantidad de colores, 100%
  client-side) es la vista por defecto; "+ Nueva Moldería" navega al formulario de alta.
  Ordena siempre por `numeroInterno` ascendente y tiene un selector de tamaño de vista tipo
  explorador de Windows (`VistaPortfolioMenu.tsx`), con la preferencia guardada en
  `localStorage` (`wc-molderias-tamano-vista`) por navegador.
- Esta pantalla se construyó con **Tailwind CSS** (recién instalado en ese momento,
  `frontend/src/styles/tailwind.css`, solo capa de utilidades, sin "preflight"/reset global
  a propósito para no romper visualmente las pantallas viejas en CSS plano). El resto del
  front sigue en CSS plano por componente; conviven ambos enfoques.
- **Bug real encontrado y arreglado:** `shared.css` define
  `input, select, textarea { padding, border, border-radius, ... }` **sin** envolverlo en
  un `@layer`. Por el spec de CSS Cascade Layers, una regla sin layer le gana siempre a
  cualquier regla dentro de un `@layer` (acá, las utilidades de Tailwind), sin importar el
  orden de los imports ni la especificidad — por eso las clases de Tailwind en los
  `<select>`/`<input>` de esta feature quedaban pisadas en silencio. Fix:
  `frontend/src/styles/tailwind.css` agrega una regla `.tw-scope input/select/textarea { ...:
  revert-layer; }`, y el contenedor raíz de cada pantalla en Tailwind lleva la clase
  `tw-scope`. **Cualquier futura pantalla en Tailwind necesita este mismo wrapper**
  mientras `shared.css` siga sin estar en un layer.

## Submenú "Config" (Molderías + Usuarios): acceso restringido a `ROLE_ADMINISTRATIVO` (2026-08-25)

`Sidebar.tsx` tiene dos "páginas" dentro del mismo panel: el menú raíz y, detrás de un ítem
"Config" con flecha de vuelta, el submenú con "MOLDERÍAS" y "USUARIOS". El ítem "Config" y
todo el submenú están completamente ocultos para cualquier usuario cuyo rol no sea
exactamente `ROLE_ADMINISTRATIVO`.

Esto se reforzó también en el backend (no solo ocultando el botón), reusando el mismo patrón
provisorio de header `X-Usuario-Id` que ya validaba editar/eliminar usuarios — se extrajo a
`AutorizacionService.verificarRolAdministrativo(idUsuarioActor)`, inyectado en
`UsuarioService` y `PatronCorteService`.

`GET /api/roles/corporativos` (catálogo de roles para el combo del alta de usuario) quedó
sin restringir a propósito — es de solo lectura y de sensibilidad baja, igual que
`GET /api/tipos-prenda`.

## Listado de Pedidos: dashboard con contadores y filtros (2026-08-25)

La pantalla "Pedidos" (`PedidosListView.tsx`) muestra 4 contadores (Total vendido /
Pendientes / En producción / Entregados) tanto para cantidad de pedidos como para unidades
de Buzo+Campera, agrupando los 9 estados posibles (`EstadoPedido`) en 3 categorías
(`BUCKET_POR_ESTADO` en `frontend/src/types/pedido.ts`, ver detalle de la agrupación en
pantallas-pendientes.md — es una interpretación a confirmar con el negocio). El buscador, el
rango de fechas, el filtro de estado y de año filtran el array ya en el navegador (`GET
/api/pedidos` trae todos los pedidos sin paginar).

"PEDIDOS" en el sidebar abre este dashboard/listado; el alta se llega desde su botón
"+ Nuevo Pedido".

## Productos viejos: migración de `tipo_prenda` a `id_tipo_prenda` (2026-08-19 aprox.)

Al migrar `productos.tipo_prenda` (texto libre) a `productos.id_tipo_prenda` (FK a la tabla
nueva `tipos_prenda`), se mapearon automáticamente los valores que coincidían exactamente
con el catálogo nuevo (`Campera`, `Buzo`, `Remera`, `Chomba`, `Bandera`). Los productos con
el texto en plural (`"Buzos"`, `"Camperas"`, `"Remeras"`) quedaron con `id_tipo_prenda =
NULL` a propósito, como se pidió, en vez de adivinar la correspondencia (son los productos
con id 1, 2, 3 y 4, pedidos 2 y 3 de esa fecha — ver pendiente de revisarlos a mano en
pantallas-pendientes.md).

## Seguridad provisoria vía header `X-Usuario-Id` (2026-08-19)

`POST /api/auth/login` valida el usuario y compara la contraseña contra el hash guardado
(BCrypt). Mientras no exista JWT (ver pantallas-pendientes.md), para poder validar en el
back quién puede hacer qué, el frontend manda el id del usuario logueado en un header
`X-Usuario-Id` (interceptor de Axios en `frontend/src/services/api.ts`), y
`AutorizacionService`/`UsuarioService` en el backend lo validan contra la base antes de
autorizar cada acción. **Esto no es seguridad real**: cualquiera puede mandar cualquier id
en ese header manualmente y hacerse pasar por otro usuario, porque no hay nada firmado ni
verificable del lado del servidor — es solo una validación de una regla de negocio contra un
dato que el cliente declara.

## Reutilización de Colegios: decisión de no reutilizar todavía (2026-08-19)

Cada vez que se guarda un pedido, el backend crea un `Colegio` nuevo (no busca si ya existe
uno con ese nombre). Decisión explícita para no frenar esta pantalla mientras no exista un
buscador de colegios (ver pendiente en pantallas-pendientes.md).

## Representante de curso: alta automática sin login real (2026-08-19)

Al crear un pedido, si el email del representante de curso no existe todavía como `Usuario`,
el backend lo da de alta automáticamente con rol `ROLE_CLIENTE` y una contraseña aleatoria
que nadie conoce (no hay flujo de invitación ni de "recuperar contraseña" implementado). Si
el email ya existe, se reutiliza ese usuario.

## Alta de usuarios corporativos: contraseña provisoria fija (2026-08-19)

La pantalla "Usuarios" (`UsuariosView.tsx`, `POST /api/usuarios`) crea usuarios corporativos
(cualquier rol menos `ROLE_CLIENTE`) con una contraseña fija **"123"** (hasheada con BCrypt
igual que cualquier otra) hasta que exista el flujo real de invitación por mail (ver
pendiente en pantallas-pendientes.md).

## Edición y "eliminación" de usuarios: deshabilitado, no borrado real (2026-08-19)

La tabla de "Usuarios Existentes" permite editar y eliminar usuarios, pero solo si el
usuario logueado tiene rol `ROLE_ADMINISTRATIVO` — el resto de los roles ni siquiera ve esos
íconos, y el backend rechaza el `PUT`/`DELETE` con 403 aunque alguien intente pegarle
directo a la API.

"Eliminar" en realidad **deshabilita** al usuario (`habilitado = false`), no borra la fila.
Se decidió así porque `Usuario` está referenciado por `Pedido`
(`creadoPor`/`representanteCurso`) y `HistorialEstadoPedido` (`modificadoPor`) — borrar la
fila de verdad rompería esas relaciones (o fallaría por la foreign key) y perdería la
trazabilidad de quién gestionó qué pedido, algo que CLAUDE.md pide explícitamente. Un
usuario deshabilitado no puede loguearse y desaparece del listado de usuarios corporativos.

## Nº de ficha con formato validado (2026-08-19 aprox.)

El formato de Nº de ficha se validó como `AAAA-NN` (ej: `2026-01`) por ser lo que se pidió
(ver pendiente de confirmar con ventas en pantallas-pendientes.md).
