# Pendientes de las pantallas de Login / Pedidos / Usuarios / Patrones de Corte

Lista de lo que quedó afuera a propósito al construir el login, el listado y la carga de
pedidos, el alta de usuarios corporativos (módulo M1 - Seguridad y CRM) y el alta de
patrones de corte (módulo M2 - Ficha Técnica Digital), para retomar más adelante. Última
actualización: 2026-08-26.

## -2.11. Guardar/Cancelar explícitos en el modal, fix de zoom (2026-08-26)

Ajuste sobre el punto -2.10 (mismo día): Tela y Cierre no se guardaban solos con el cambio
inmediato de antes — el usuario los cambiaba y la única acción disponible era cerrar, sin
feedback de si había quedado guardado. Se corrigió cambiando el modelo a "borrador + guardar
explícito":

- **Tela y Cierre ahora son borrador local** (`telaDraft`/`cierreDraft`) hasta que se aprieta
  "Guardar": no se llama a `PATCH /api/productos/{id}/tipo-tela` ni
  `PATCH /api/productos/{id}/color-cierre` con cada cambio de selector como antes.
- **Footer nuevo, abajo a la derecha:** "Cancelar" (antes "Cerrar", mismo comportamiento: no
  toca el servidor) y "Guardar" (persiste tela y/o cierre si cambiaron, en un solo click, y
  cierra el modal). El flujo de "Confirmar colores" del click a click sigue siendo aparte —
  ese sí sigue guardando al toque, no es parte de este borrador.
- **Excepción:** si se arranca a marcar colores ("Marcar colores"/"Rehacer colores") con una
  tela todavía sin guardar, esa tela se persiste automáticamente antes de abrir el flujo de
  clicks — los `ProductoColor` que se van a guardar quedan atados a la paleta de esa tela, no
  puede quedar como un borrador suelto mientras tanto.
- **Bug de zoom corregido:** al 100% se veía solo aprox. 1/4 de la imagen, porque el `<canvas>`
  se mostraba a resolución nativa del archivo (una foto de celular puede tener miles de px de
  ancho) dentro de un contenedor mucho más chico. Ahora "100%" significa "ajustar la imagen
  completa a la ventana" (`escalaAjuste`, calculado una vez por imagen comparando su tamaño
  natural contra el contenedor) y el zoom del usuario es un multiplicador sobre esa base, de
  10% a 300%.

## -2.10. Modal de ficha rediseñado: ícono de lápiz, dos columnas, zoom (2026-08-26)

Reemplaza el punto -2.9 de más abajo (mismo día, unas horas después): se unificaron Cargar,
Reemplazar y Editar en un solo punto de entrada.

- **Ícono de lápiz** (arriba a la derecha de cada card, position absolute, solo visible con
  permiso) reemplaza el botón de texto "Editar" y también a los botones "Cargar"/"Reemplazar"
  que antes vivían sueltos en la card — ahora es el único disparador del modal
  (`ModalColoresGotero`), tanto para la primera carga de imagen como para reemplazarla o
  editar tela/cierre/colores.
- **Layout de dos columnas dentro del modal** (`grid md:grid-cols-2`): imagen a la izquierda
  (con controles de zoom −/100%/+ hasta 300%, implementado con `transform: scale()` sobre el
  `<canvas>`/`<img>` dentro de un contenedor `overflow-auto` — el cálculo de click-a-pixel del
  gotero sigue funcionando solo porque usa `getBoundingClientRect()`, que ya refleja el
  tamaño escalado); Tela, Cierre y Colores apilados verticalmente a la derecha, siempre en el
  mismo popup.
- **Subir/reemplazar imagen ahora vive adentro del modal:** si el producto no tiene imagen
  todavía, la columna izquierda muestra un dropzone con botón "Cargar imagen"; si ya tiene,
  muestra la imagen con zoom y un link "Reemplazar imagen" debajo. Ambos casos usan el mismo
  `POST /api/productos/{id}/imagen` de siempre.
- ⚠️ **Cambio de comportamiento importante en "Cerrar":** como ya no existe un trigger
  "recién subida vs. edición" (todo pasa por el mismo ícono de lápiz), se eliminó por completo
  la lógica que borraba la imagen del servidor al cancelar (`DELETE /api/productos/{id}/imagen`
  ya no se llama desde el modal). Ahora "Cerrar" **nunca** toca el servidor — tiene sentido
  porque la card de solo lectura (ver punto -2.9) ya sabe mostrar "Sin definir" para
  tela/cierre/colores faltantes, así que una ficha a medio completar dejó de ser un estado
  "inconsistente" a evitar y pasó a ser un estado normal y visible.
- **Si el producto ya tiene colores asignados**, el modal no fuerza a rehacer todo el click a
  click: muestra primero el resumen ya guardado (con gramos) y un botón "Rehacer colores" que
  recién ahí arranca la secuencia de posiciones desde cero (no prellena; ver limitación ya
  anotada en el punto -2.9 de abajo, sigue aplicando).
- **Gramos visibles en la card:** cada línea "Color N" ahora muestra los gramos de esa
  posición del patrón de corte (ej. "Color 1 (450 g): Marino"), tomados de
  `patronCorteColores`, no solo del lado del modal.

## -2.9. Selector de tela/cierre movido adentro del modal, editar ficha existente (2026-08-26)

Ajuste sobre el punto -2.8 (mismo día siguiente): los selectores de "Tela" y "Cierre" se
sacaron de la card de `FichasTecnicasView` (ahí ahora son de solo lectura) y se movieron
adentro de `ModalColoresGotero`, junto con la posibilidad de editar una ficha ya cargada.

- **Card 100% de solo lectura:** siempre muestra "Tela: X" (o "Sin definir"), "Cierre: X"
  (solo si es Campera) y una línea "Color N: X" por **cada posición del patrón de corte**
  (`producto.patronCorteColores`, no solo las ya asignadas) — así se ve de un vistazo qué
  falta cargar aunque el valor todavía no exista. La única forma de cambiar cualquiera de
  estos datos ahora es a través del modal.
- **Nuevo botón "Editar"** (al lado de "Cargar"/"Reemplazar", visible si ya hay imagen y el
  usuario tiene permiso): abre `ModalColoresGotero` sobre la imagen **ya existente**, sin
  volver a subir nada. El modal no prellena las posiciones ya asignadas — hay que volver a
  marcarlas todas por gotero, igual que en la carga inicial (se interpretó así la consigna
  "abriría la misma ventana que cuando cargás la imagen", literal); si en algún momento se
  quiere precargar lo ya guardado para solo corregir una posición puntual, es un cambio
  aparte.
- **`modoEdicion` en el modal:** si se abrió con "Editar" (imagen preexistente), el botón
  "Cancelar" pasa a decir "Cerrar" y **no borra la imagen** (antes, `handleCancelar` siempre
  llamaba a `DELETE /api/productos/{id}/imagen`, lo cual habría borrado una imagen real ya
  guardada). Si se abrió justo después de subir una imagen nueva, el comportamiento viejo se
  mantiene igual (Cancelar sí la descarta).
- **Selector de Tela dentro del modal:** ya no es precondición para abrir el modal — antes,
  si un producto no tenía tela resuelta (ej. Bandera sin elegir) no se podía ni abrir. Ahora
  el modal siempre se puede abrir; si no hay tela, muestra el selector como "paso 0" y
  bloquea el resto (canvas, cierre) hasta elegir una. Cambiar la tela llama al mismo
  `PATCH /api/productos/{id}/tipo-tela` de antes, pero además **reinicia el progreso** de
  colores ya marcados en esa sesión del modal (la paleta cambia con la tela, así que un color
  ya elegido con la tela vieja ya no es válido).
- **Selector de Cierre dentro del modal (solo Campera):** visible desde el arranque, pero se
  autocompleta recién cuando se confirma la posición 1 (busca en `coloresCierre` un color con
  el mismo *nombre* que el Color 1 recién elegido) — se puede corregir a mano en cualquier
  momento después. Al confirmar todo el modal, si hay un cierre seleccionado se persiste con
  un segundo llamado a `PATCH /api/productos/{id}/color-cierre` (además del
  `POST /api/productos/{id}/colores` de siempre), así el valor que ve el usuario en el modal
  es siempre el que termina guardado, sin depender del autocompletado silencioso que ya hacía
  el backend en `ProductoService.asignarColores`.
- **Defaults de tela por tipo de prenda ampliados:** ahora **las 5** prendas del catálogo
  tienen default (antes Campera y Bandera quedaban sin uno): `Campera → FRIZA`,
  `Bandera → SPUM` (tela nueva, agregada como quinto valor del enum `TipoTela` junto a
  FRIZA/JERSEY/PIQUE/CIERRE; `DataInitializer` la siembra sola porque itera
  `TipoTela.values()`, no hizo falta tocar el seed a mano). `Buzo/Remera/Chomba` sin cambios.

## -2.8. Tipo de tela y color de cierre (2026-08-25)

`PaletaColores` ahora tiene un campo `tipoTela` (enum `TipoTela { FRIZA, JERSEY, PIQUE, CIERRE }`):
cada fila del catálogo queda atada a una tela específica (o a "cierre"), así que el mismo
nombre de color existe repetido en varias filas (ej. "Marino" en FRIZA, otra fila "Marino"
en JERSEY, otra en PIQUE, otra en CIERRE) — son insumos de compra distintos aunque el swatch
se vea igual. Esto fue una decisión explícita del usuario (se le preguntó si prefería este
modelo o tener `tipoTela` como campo de `Producto` en vez del catálogo, y eligió esto último
para poder "unificar después tipo de tela y pedido", pensando en el futuro Planificador de
Compras de M3).

- **Migración manual de datos (una sola vez, 2026-08-25):** `paleta_colores` ya tenía 11
  filas reales (10 del seed original + "Natural", creado por el usuario probando el modal de
  gotero). Se migró a mano en vez de dejar que `ddl-auto=update` agregue la columna
  `tipo_tela` como `NOT NULL` directamente (mismo problema de siempre, ver punto de
  toolchain/memoria del proyecto): se agregó nullable, se backfillearon esas 11 filas a
  `FRIZA` (decisión arbitraria pero razonable, ya que eran de antes de que existiera esta
  segmentación), se reemplazó el índice único de `nombre` solo por uno compuesto
  `(nombre, tipo_tela)`, y recién ahí se puso `NOT NULL`. `DataInitializer` siembra las 30
  filas nuevas (los mismos 10 nombres × JERSEY/PIQUE/CIERRE) de forma idempotente por
  combinación `(nombre, tipoTela)`, no por `count()==0` (ya no sirve, la tabla no arranca en
  cero).
- **`Producto.tipoTela`** (mismo enum, nunca debe valer `CIERRE`, se valida en
  `ProductoService.actualizarTipoTela`) se precarga con un default al crear el pedido según
  `TIPO_TELA_POR_DEFECTO` en `PedidoService` (`Buzo→FRIZA`, `Remera→JERSEY`,
  `Chomba→PIQUE`); `Campera` y `Bandera` quedan sin default (no hay tela obvia) y hay que
  elegirla a mano. Editable después desde Ficha Técnica (`PATCH /api/productos/{id}/tipo-tela`,
  mismos roles que cargar la imagen), **no** se agregó a `NuevoPedidoView` — la decisión fue
  que este campo es cosa de Ficha Técnica, no del alta del pedido.
- El modal de gotero (`ModalColoresGotero`) ahora exige la tela resuelta (guardada o el
  default por tipo de prenda) antes de poder abrirse: si el producto no tiene una tela
  determinable (Campera/Bandera sin elegir), al subir la imagen se avisa "elegí el tipo de
  tela primero" en vez de abrir el modal. El `GET /api/paleta-colores?tipoTela=X` que usa el
  modal (y el que crea colores nuevos desde ahí) queda acotado a esa tela específica.
- **Color de cierre:** solo para `Producto` con `tipoPrenda == "Campera"`
  (`ProductoService.esCampera`). Nuevo campo `Producto.colorCierre` (FK a `PaletaColores`,
  debe tener `tipoTela == CIERRE`, si no `409`). Al confirmar los colores del gotero
  (`POST /api/productos/{id}/colores`), si la prenda es Campera y todavía no tiene cierre
  elegido, se le asigna automáticamente el color de tipo CIERRE que tenga el mismo *nombre*
  que el "Color 1" recién elegido (no la misma fila, esa es la de tela) — si no existe un
  color de cierre con ese nombre exacto, queda sin definir. Nunca pisa una elección manual ya
  guardada. Editable aparte con `PATCH /api/productos/{id}/color-cierre`.
- **Filtros de Ficha Técnica:** el filtro de "Color" que ya existía se cambió de filtrar por
  `idPaletaColor` a filtrar por *nombre* de color (deduplicado, excluyendo los de tipo
  CIERRE), porque con el catálogo ahora repetido por tela el mismo nombre podía aparecer 3-4
  veces en el combo con ids distintos. Nuevo filtro de "Tela" (Friza/Jersey/Piqué, sin
  Cierre) que, a diferencia de los demás filtros (que ocultan pedidos enteros), filtra a
  **nivel producto**: si el pedido tiene una Campera Friza y una Chomba Piqué y se filtra por
  Friza, la card del pedido se sigue mostrando pero solo con el slot de la Campera; si
  ningún producto del pedido matchea la tela elegida, el pedido entero desaparece del
  listado.
- ⚠️ Mismo riesgo ya documentado en el punto -2.6/-2.7 (`PedidoService.actualizarPedido`
  recrea todos los `Producto` en cada edición de pedido): ahora también se pierde el
  `tipoTela` elegido a mano (vuelve a recalcularse el default) y el `colorCierre` (vuelve a
  `null`, tendría que re-completarse) cada vez que se edita un pedido desde Pedidos.

## -2.7. Colores por gotero en Ficha Técnica (2026-08-25)

Al cargar/reemplazar la imagen de diseño de un `Producto` (Ficha Técnica), si el producto
tiene un `PatronCorte` asignado, se abre automáticamente `ModalColoresGotero.tsx`: un modal
obligatorio (sin cierre por click afuera, solo botón "Cancelar") donde se hace click sobre
la imagen para asignar, una por una, el color real de cada posición del patrón de corte.

- **Modelo nuevo:** `Producto.patronCorte` (ManyToOne a `PatronCorte`, mismo criterio que
  `tipoPrenda`: nullable en base por productos legacy, obligatorio en
  `ProductoCreateRequest`). `PaletaColores` (catálogo de colores estándar de la empresa,
  sembrado en `DataInitializer` con 10 colores aproximados). `ProductoColor` (el color real
  asignado a una posición puntual: `producto` + `patronCorteColor` (no un `orden` suelto,
  para quedar atado al patrón exacto) + `paletaColor` + `metodoDeteccion` (enum, siempre
  `MANUAL` por ahora) + `coordenadaX/Y` + `rgbDetectado`, estos tres últimos guardados desde
  ya para auditoría y para una futura detección automática por clustering, fuera de alcance
  ahora). `uniqueConstraint` sobre (producto, patronCorteColor).
- **Endpoint `POST /api/productos/{id}/colores`:** reemplaza el set completo de colores del
  producto (borra e inserta). Valida que el array tenga exactamente la cantidad de
  `PatronCorteColor` del patrón del producto y que cada posición pertenezca a ese patrón;
  si no, `409 BusinessRuleException`. Mismos roles que cargar la imagen
  (`ROLE_ADMINISTRATIVO`/`ROLE_VENDEDOR`/`ROLE_DISENADOR`).
- **`GET/POST /api/paleta-colores`:** el `GET` (listado de activos) quedó sin restringir a
  propósito, mismo criterio que `GET /api/tipos-prenda` (catálogo de solo lectura y baja
  sensibilidad). El `POST` (alta de un color nuevo desde el modal, cuando el detectado no
  matchea ninguno existente) sí está restringido a los mismos roles que cargan la imagen.
- **"Cancelar" en el modal descarta la imagen subida:** nuevo endpoint
  `DELETE /api/productos/{id}/imagen` (mismos roles) que limpia `imagenDisenoUrl` a null. El
  archivo en disco no se borra físicamente, mismo criterio ya aceptado para Molderías.
- **Visibilidad de `GET /api/patrones-corte` (listado y detalle) ampliada:** antes exclusiva
  de `ROLE_ADMINISTRATIVO`, ahora también `ROLE_VENDEDOR` (necesita elegir el patrón al
  cargar un pedido) y `ROLE_DISENADOR` (necesita leer las posiciones de color para el
  modal). El alta (`POST`) sigue exclusiva de `ROLE_ADMINISTRATIVO`.
- **Fix de CORS necesario para que el gotero funcione:** `CorsConfig.java` solo cubría
  `/api/**`; sin CORS en `/uploads/**` (donde se sirven las imágenes), dibujar la imagen en
  un `<canvas>` y leer píxeles con `getImageData()` tira `SecurityError` (canvas "tainted")
  porque el navegador la ve como recurso cross-origin sin cabeceras. Se agregó un mapping de
  CORS también para `/uploads/**`.
- **Matching de color desacoplado:** `frontend/src/utils/colorMatch.ts` (RGB del pixel →
  color de paleta más cercano por distancia euclídea) es una función pura, sin dependencia
  del modal, para poder reusarla después desde una sugerencia automática sin duplicar
  lógica. No se implementó ninguna detección automática todavía.
- **Efecto colateral en el alta/edición de pedidos:** al ser `idPatronCorte` obligatorio en
  `ProductoCreateRequest`, `NuevoPedidoView.tsx` ahora tiene una columna "Patrón de Corte"
  por cada prenda, filtrada por el tipo de prenda ya elegido en esa fila (un `PatronCorte`
  tiene un `tipoPrenda` fijo). Si no hay ningún patrón cargado para ese tipo, el combo queda
  deshabilitado con el aviso "Sin patrones para este tipo" y el submit se bloquea.
- ⚠️ **Esto empeora un riesgo ya documentado (ver punto -2.6):** `PedidoService.actualizarPedido`
  sigue borrando y recreando todos los `Producto` de un pedido en cada edición. Como
  `ProductoColor` cuelga de `Producto` con `orphanRemoval`, **editar un pedido desde
  Pedidos ahora también borra los colores del gotero ya cargados** en Ficha Técnica para
  esas prendas, además de la imagen y el estado que ya se perdían. Sigue sin corregirse acá
  (el fix real es matchear productos existentes por id en vez de reemplazar la lista).

## -2.6. Estado de producción por prenda (2026-08-25)

Cada `Producto` (prenda) ahora tiene su propio `estadoActual` (mismo enum `EstadoPedido` que
usa `Pedido.estadoActual`), independiente del estado "general" del pedido — cambiar el de
una prenda no toca el del pedido, tal como se pidió explícitamente. Se ve como un select con
el mismo color que `EstadoBadge` en cada mini-slot de la pestaña Ficha Técnica.

- **Roles habilitados para cambiar el estado de una prenda:** `ROLE_ADMINISTRATIVO` y
  `ROLE_PLANTA` (distinto del set que puede cargar la imagen de diseño —
  `ROLE_ADMINISTRATIVO`/`ROLE_VENDEDOR`/`ROLE_DISENADOR` — decisión explícita: quien produce
  es quien reporta en qué etapa está cada prenda). Nuevo endpoint
  `PATCH /api/productos/{id}/estado`, mismo patrón de autorización que el resto
  (`AutorizacionService.verificarRolPermitido`).
- **Sin historial propio:** a diferencia del estado del pedido (que sí tiene
  `HistorialEstadoPedido`, con quién/cuándo/observaciones), el cambio de estado de una
  prenda **no queda registrado en ningún lado** más que el valor actual — no hay auditoría
  de "quién lo cambió y cuándo" a nivel prenda. Si el negocio necesita esa trazabilidad
  también acá (CLAUDE.md la pide para pedidos en general), habría que agregar una tabla
  análoga a `historial_estado_pedido` pero por producto.
- **Al crear un producto nuevo**, su `estadoActual` arranca igual al estado que tenga el
  pedido en ese momento (`request.estado()`), tanto en alta como en edición de pedido.
- ⚠️ **Riesgo pre-existente que esto vuelve más importante:** `PedidoService.actualizarPedido`
  borra y recrea **todos** los `Producto` de un pedido cada vez que se edita (`clear()` +
  alta de cero), en vez de actualizar los existentes por id. Esto ya perdía
  `imagenDisenoUrl` en cada edición; ahora también resetea `estadoActual` al valor del
  pedido, perdiendo cualquier progreso de producción que se hubiera cargado por prenda.
  No se corrigió en este cambio (es un refactor más grande — habría que matchear productos
  por id en vez de reemplazar la lista), pero es importante tenerlo presente: **editar un
  pedido desde la pantalla de Pedidos borra el trabajo cargado en Ficha Técnica** para esa
  prenda (imagen y estado).

## -2.5. Ficha Técnica (nueva pestaña, 2026-08-25): portfolio de diseños por prenda

Pestaña nueva en el sidebar raíz (visible para todos los roles logueados, sin gate de
Config), `frontend/src/features/fichas-tecnicas/FichasTecnicasView.tsx`. Reutiliza
`GET /api/pedidos` (no hay endpoint nuevo de listado) y muestra una tarjeta por **pedido**
(no por prenda) con Nº de Ficha, Colegio/Localidad y Estado, y adentro un "mini-slot" por
cada `Producto` del pedido (tipo de prenda + cantidad + imagen). A propósito **no muestra
precios** (eso vive en la pestaña Pedidos).

- **Endpoint nuevo:** `POST /api/productos/{id}/imagen` (multipart, campo `imagen`), sube
  a `Producto.imagenDisenoUrl` (columna que ya existía en el modelo pero nunca tuvo UI de
  carga real — antes solo se podía escribir una URL a mano). Reutiliza el mismo
  `AlmacenamientoImagenService` que Molderías (se extrajo de `PatronCorteService` a este
  servicio compartido en este mismo cambio, para no duplicar la validación JPG/PNG + guardado
  en disco). Carpeta: `backend/uploads/fichas-tecnicas/`.
- **Roles habilitados para cargar/reemplazar la imagen:** `ROLE_ADMINISTRATIVO`,
  `ROLE_VENDEDOR` y `ROLE_DISENADOR` (rol nuevo, creado en `DataInitializer` de forma
  idempotente vía `findByNombre` porque los demás roles de este entorno ya existían fuera
  del seed). El resto de los roles logueados (`ROLE_PLANTA`, `ROLE_COBRANZAS`) **ven** el
  portfolio pero no tienen el botón "Cargar"/"Reemplazar" — decisión explícita del usuario,
  distinta de la visibilidad de la pestaña en sí (esa es para todos). Verificado también
  server-side (`AutorizacionService.verificarRolPermitido`, generalizado a partir del viejo
  `verificarRolAdministrativo` para aceptar un `Set<String>` de roles).
- Placeholder **"No cargado"** (ícono + texto) se muestra siempre que `imagenDisenoUrl` sea
  null o la imagen falle al cargar (`onError`), para que todas las prendas sin diseño se
  vean igual.
- Sin filtro de tipo de prenda/color acá (a diferencia de Molderías) — solo Buscar (ficha o
  colegio) y Estado. Se listan **todos** los pedidos sin restringir por estado por defecto
  (se preguntó explícitamente y se eligió esta opción sobre "solo Listo para Producción en
  adelante").
- No hay selector de tamaño de vista (el de Molderías no se pidió acá).
- Un pedido puede tener productos con `tipoPrenda == null` (ver punto 0, productos viejos
  sin tipo indexado) — se muestran como "Sin tipo" en el slot, sigue siendo cargable.

## -2. Patrones de Corte: `PatronCorte`/`PatronCorteColor` no son `Molderia`/`PiezaMolderia`

El diagrama de clases de CLAUDE.md modela la moldería como `Molderia` + `PiezaMolderia`
(piezas con `geometriaPoligono` para el nesting/tizada de M4). El alta de "Patrones de
Corte" (`PatronCorte`/`PatronCorteColor`, gramos de Friza por color, pensado para el
cálculo de compras por color de M3) se agregó como entidades **nuevas y separadas**, no
como reemplazo de `Molderia`/`PiezaMolderia` — resuelven necesidades distintas (compras
por color vs. nesting geométrico). Falta decidir si en algún momento se relacionan entre
sí (por ejemplo, si un `PatronCorte` term extends `Molderia` o si son conceptos
totalmente independientes del negocio) y, si corresponde, reflejarlo en el diagrama.

Otros pendientes de esta pantalla (`frontend/src/features/patrones-corte/`,
`POST/GET /api/patrones-corte`):
- Solo alta y listado/detalle; no hay edición ni baja (el campo `activo` existe en el
  modelo para desactivar sin borrar, pero nada en la API ni en el front lo usa todavía).
- Las imágenes se guardan en disco local (`backend/uploads/patrones-corte/`, configurable
  vía `app.uploads.*` en `application.properties`) y se sirven como recurso estático de
  Spring en `/uploads/**`. No hay backup ni límite de espacio — revisar antes de producción
  o migrar a un storage tipo S3.
- `cantidadColores` está limitado a 1-5 tanto en el front (stepper) como en la validación
  del back (`@Size` en `PatronCorteCreateRequest`), aunque el pedido original mencionaba
  "1 a 3, ampliable a futuro" — se dejó en 5 porque la consigna del formulario pedía ese
  tope explícitamente. Ajustar `MAX_COLORES` (front) y el `@Size` (back) si cambia.
- Esta pantalla se construyó con **Tailwind CSS** (recién instalado, `frontend/src/styles/tailwind.css`,
  solo capa de utilidades, sin "preflight"/reset global a propósito para no romper visualmente
  las pantallas viejas en CSS plano). El resto del front sigue en CSS plano por componente.
  Conviven ambos enfoques; no hay plan de migrar las pantallas viejas a Tailwind todavía.
- **(2026-08-25)** `PatronCorte` ahora tiene `numeroInterno` (`Integer`, obligatorio, `UNIQUE`
  a nivel de base — lo setea el usuario en el alta, no es el `id` autogenerado) y una FK real
  a `TipoPrenda` (`idTipoPrenda`, el mismo catálogo de 5 valores que usa `Producto`, combo en
  el front vía `GET /api/tipos-prenda`). Al agregar estas dos columnas `NOT NULL` se borraron
  las 2 filas de prueba que había en `patrones_corte`/`patron_corte_colores` (eran datos de
  testing de esta misma sesión, no del negocio) para evitar el problema conocido de
  `ddl-auto=update` con columnas `NOT NULL` sobre filas ya existentes (ver punto de
  toolchain/memoria del proyecto). Si en algún momento hay patrones de corte reales cargados,
  cualquier `NOT NULL` nuevo va a necesitar el mismo baile de nullable→backfill→`ALTER`.
- **(2026-08-25)** La pestaña "Molderías" ahora es un portfolio: `MolderiasListView.tsx`
  (grilla de tarjetas con imagen, `#numeroInterno nombre`, tipo de prenda, cantidad de
  colores y gramos por color, con buscador + filtro por tipo de prenda + filtro por
  cantidad de colores, 100% client-side) es la vista por defecto, y "+ Nueva Moldería"
  navega al formulario de alta ya existente (`CargaPatronCorteForm`, que ahora acepta un
  `onCreado` opcional para volver al listado con un banner de éxito, en vez de limpiarse
  in-place). Sigue sin haber edición/baja/detalle — solo alta y listado.
- **(2026-08-25)** El portfolio de Molderías ordena siempre por `numeroInterno` ascendente
  (se aplica en el `useMemo` de filtrado, después de filtrar), y tiene un selector de
  tamaño de vista tipo explorador de Windows (`VistaPortfolioMenu.tsx`: Iconos muy
  grandes/grandes/medianos/pequeños/Lista). La preferencia se guarda en `localStorage`
  (`wc-molderias-tamano-vista`) por navegador, no por usuario ni servidor. En "Iconos
  pequeños" y "Lista" la info (tipo/colores/peso) se muestra compacta en una sola línea en
  vez de las 3 líneas separadas, para que entre en el espacio más chico, pero sigue
  presente en todos los tamaños.
- **Bug real encontrado y arreglado (2026-08-25):** `shared.css` define
  `input, select, textarea { padding, border, border-radius, ... }` **sin** envolverlo en
  un `@layer`. Por el spec de CSS Cascade Layers, una regla sin layer le gana siempre a
  cualquier regla dentro de un `@layer` (acá, las utilidades de Tailwind), sin importar el
  orden de los imports ni la especificidad — por eso `pl-8`, `rounded-lg`, `bg-white`, etc.
  en los `<select>`/`<input>` de esta feature quedaban pisados en silencio por esos valores
  globales (se notó porque el ícono de los filtros quedaba superpuesto con el texto). Fix:
  `frontend/src/styles/tailwind.css` agrega una regla `.tw-scope input/select/textarea { ...:
  revert-layer; }`, y el contenedor raíz de `PatronesCorteView.tsx` lleva la clase
  `tw-scope`. **Cualquier futura pantalla en Tailwind necesita este mismo wrapper
  `tw-scope`** (o una solución equivalente) mientras `shared.css` siga sin estar en un
  layer — de lo contrario sus inputs/selects van a heredar el look de las pantallas viejas
  en vez del de Tailwind, silenciosamente.

## -3. Submenú "Config" (Molderías + Usuarios): acceso restringido a `ROLE_ADMINISTRATIVO`

`Sidebar.tsx` ahora tiene dos "páginas" dentro del mismo panel: el menú raíz y, detrás de un
ítem "Config" con flecha de vuelta, el submenú con "MOLDERÍAS" (`patrones-corte`) y
"USUARIOS". El ítem "Config" y todo el submenú están completamente ocultos para cualquier
usuario cuyo `rol` (`useAuth().usuario.rol`) no sea exactamente `ROLE_ADMINISTRATIVO`
(2026-08-25).

Esto se reforzó también en el backend (no solo ocultando el botón), reusando el mismo patrón
provisorio de header `X-Usuario-Id` que ya validaba editar/eliminar usuarios — se extrajo a
`AutorizacionService.verificarRolAdministrativo(idUsuarioActor)`, inyectado ahora en
`UsuarioService` (las 4 operaciones: crear, listar corporativos, actualizar, eliminar) y en
`PatronCorteService` (las 3: crear, listar, obtener). **Sigue siendo la misma seguridad
"provisoria" de siempre** (ver punto 1): el cliente declara su propio id en el header, nada
está firmado. Cuando exista JWT real, este es uno de los lugares a migrar.

`GET /api/roles/corporativos` (catálogo de roles para el combo del alta de usuario) quedó
**sin restringir** a propósito — es de solo lectura y de sensibilidad baja, igual que
`GET /api/tipos-prenda`.

Pendiente a futuro: cuando se construya el Planificador de Compras (M3), que necesita leer
`PatronCorte`/`PatronCorteColor` para calcular gramos de tela por color, los `GET` de
`/api/patrones-corte` van a tener que dejar de ser exclusivos de `ROLE_ADMINISTRATIVO` (o
ese módulo va a necesitar su propio rol/permiso). No se resolvió ahora porque ese módulo
todavía no existe.
- definir como va a ser esta pestaña que primero se pensó para cargar las fichas técnicas de los colegios como para tener la vista del drive, pero quizás un botón dentro de esta pestaña que puedas cargar o editar molderias, porque eso se configura una vez y no se toca generalmente

## -1. Listado de Pedidos: agrupación de estados en 3 categorías (decisión propia, a confirmar)

La pantalla "Pedidos" (`PedidosListView.tsx`) muestra 4 contadores (Total vendido /
Pendientes / En producción / Entregados) tanto para cantidad de pedidos como para
unidades de Buzo+Campera. El negocio tiene 9 estados posibles (`EstadoPedido`), pero el
mockup solo pedía 3 categorías, así que agrupé así (`BUCKET_POR_ESTADO` en
`frontend/src/types/pedido.ts`):

- **Pendiente**: Presupuestado, Señado, Listo para Producción.
- **En producción**: Cortado, Bordado, Confeccionado, En Control, Terminado.
- **Entregado**: Entregado.

Es una interpretación mía, no algo que el negocio confirmó explícitamente — en particular
dudé si "Terminado" debería contar como "en producción" (ya está confeccionado, pero
todavía no se entregó) o como una cuarta categoría propia. Si no coincide con cómo lo
piensa el equipo, ajustar `BUCKET_POR_ESTADO` (un solo lugar, se usa tanto para los
contadores como para el color del badge de Estado en la tabla).

También: "Colegios" es el nombre de la sección en el mockup, pero los 4 contadores de esa
fila en realidad cuentan **pedidos**, no colegios distintos (un colegio con 3 pedidos
suma 3, no 1). Lo dejé así porque así lo aclaró el pedido original ("cantidad de colegios
que sería la cantidad total de pedidos"), pero el rótulo puede confundir a futuro.

## -0.5. Listado de Pedidos: filtros y datos 100% del lado del cliente

`GET /api/pedidos` trae **todos** los pedidos sin paginar ni filtrar en el servidor; el
buscador, el rango de fechas, el filtro de estado y de año filtran el array ya en el
navegador (`PedidosListView.tsx`). Funciona bien con la cantidad de pedidos actual, pero
no va a escalar — cuando haya muchos pedidos, mover el filtrado/paginado al backend
(`PedidoRepository` con `Specification`/query params en `GET /api/pedidos`).

"Precio Unitario" en la tabla es un promedio (`precioTotal / cantidad total de unidades
del pedido`), no un precio real de un artículo puntual — un pedido con varios tipos de
prenda a distinto costo no tiene un único "precio unitario" real. Es una aproximación
para tener algo que mostrar en esa columna del mockup.

## 0. Productos viejos sin `tipo_prenda` indexado

Al migrar `productos.tipo_prenda` (texto libre) a `productos.id_tipo_prenda` (FK a la
tabla nueva `tipos_prenda`), solo se pudieron mapear automáticamente los valores que
coincidían exactamente con el catálogo nuevo (`Campera`, `Buzo`, `Remera`, `Chomba`,
`Bandera`). Los productos que tenían el texto en plural (`"Buzos"`, `"Camperas"`,
`"Remeras"`) quedaron con `id_tipo_prenda = NULL` a propósito, como se pidió, en vez de
adivinar la correspondencia. Son los productos con id 1, 2, 3 y 4 (pedidos 2 y 3 de esa
fecha). Falta revisarlos a mano y asignarles el tipo correcto.

## 1. Seguridad real (JWT / Spring Security)

Hoy `POST /api/auth/login` valida el usuario y compara la contraseña contra el hash
guardado (BCrypt), pero:

- No emite ningún token (JWT ni de otro tipo).
- No hay ningún filtro de seguridad instalado: **todos los endpoints del backend
  están abiertos**, con o sin login. El frontend solo usa el login para decidir qué
  pantalla mostrar (`AuthContext` guarda el usuario en `localStorage`), no para
  autorizar nada del lado del servidor.
- Falta mapear los roles (Administrador, Operativo, Cortador, Ventas — y los que se
  agreguen) a `GrantedAuthority` como pide CLAUDE.md.

**Parche provisorio (2026-08-19):** para poder validar en el back quién puede editar o
eliminar usuarios (solo `ROLE_ADMINISTRATIVO`, ver punto 6), el frontend manda el id del
usuario logueado en un header `X-Usuario-Id` (interceptor de Axios en
`frontend/src/services/api.ts`), y `UsuarioService` en el backend lo valida contra la
base antes de permitir `PUT`/`DELETE /api/usuarios/{id}`. **Esto no es seguridad real**:
cualquiera puede mandar cualquier id en ese header manualmente (con curl, Postman, etc.)
y hacerse pasar por otro usuario, porque no hay nada firmado ni verificable del lado del
servidor. Es solo una validación de una regla de negocio contra un dato que el cliente
declara, no autenticación. Cuando se implemente JWT, reemplazar ese header por el id que
salga del token verificado (`Authorization: Bearer ...`), no seguir confiando en lo que
mande el cliente.

Cuando se implemente esto, revisar también los puntos 3, 4 y 6 de abajo, porque cambian
en cuanto haya sesión real: quién puede crear un pedido "en nombre de" otro vendedor,
qué puede editar cada rol, etc.

## 2. Reutilización de Colegios existentes

Ahora mismo, cada vez que se guarda un pedido, el backend **crea un Colegio nuevo**
(no busca si ya existe uno con ese nombre). Esto fue una decisión explícita para no
frenar esta pantalla mientras no exista un buscador de colegios.

Falta:
- Un endpoint de búsqueda de colegios (por nombre/localidad) para autocompletar.
- Decidir la regla de "reutilizar vs. crear": ¿por nombre exacto? ¿el usuario elige de
  una lista? ¿se permite editar los datos del colegio existente desde acá?

## 3. Representante de curso: alta automática sin login real

Al crear un pedido, si el email del representante de curso no existe todavía como
`Usuario`, el backend lo da de alta automáticamente con rol `ROLE_CLIENTE` y una
contraseña aleatoria que nadie conoce (no hay flujo de invitación ni de
"recuperar contraseña" implementado). Si el email ya existe, se reutiliza ese usuario.

Falta:
- Un buscador/autocompletado de representantes existentes (similar al de colegios).
- Definir si el representante de curso alguna vez necesita loguearse de verdad (por
  ejemplo, para ver el estado de su pedido), y si es así, un flujo real de invitación
  con seteo de contraseña.
- Confirmar si además de nombre/teléfono/email hacen falta más datos del representante.

## 4. Roles nuevos y permisos por pantalla

CLAUDE.md ya define roles (Administrador, Operativo, Cortador, Ventas) pero todavía no
se definió en detalle qué puede hacer cada uno en estas pantallas. Ejemplos concretos
que surgieron mientras se construía esto:

- Un **Gerente** (rol nuevo, a definir) debería poder crear un pedido "en nombre de"
  otro vendedor, en vez de que el campo Vendedor quede siempre fijo al usuario
  logueado (que es el comportamiento actual, pensado solo para el rol Ventas).
- Falta decidir qué rol(es) pueden editar el Estado de un pedido ya creado, y si hay
  transiciones de estado restringidas (por ejemplo, no debería poder saltar de
  Presupuestado a Entregado directamente) — hoy el campo Estado es 100% libre en la
  creación, sin ninguna regla de transición.
- Falta el módulo completo de "Carga Descentralizada": el link público sin login para
  que alumnos/clientes carguen sus medidas y apodos (mencionado en CLAUDE.md, no
  empezado).

## 5. Alta de usuarios corporativos: contraseña provisoria fija

La pantalla "Usuarios" (`UsuariosView.tsx`, `POST /api/usuarios`) crea usuarios
corporativos (cualquier rol menos `ROLE_CLIENTE`, que se reserva para los
representantes de curso creados desde Pedidos) con una contraseña fija **"123"**
(hasheada con BCrypt igual que cualquier otra) hasta que exista el flujo real.

Falta:
- Enviar un mail al usuario recién creado con un link para que setee su propia
  contraseña (en vez de nacer con "123" conocida por cualquiera).
- Invalidar/expirar ese link después de un tiempo o de un solo uso.
- Forzar el cambio de contraseña en el primer login mientras no exista el mail (hoy
  no hay ninguna restricción: el usuario puede loguearse y quedarse con "123" para
  siempre).
- Pantalla de "olvidé mi contraseña" para cuando ya no sea la primera vez.

## 6. Edición y "eliminación" de usuarios: es un deshabilitado, no un borrado real

La tabla de "Usuarios Existentes" permite editar (lápiz) y eliminar (tacho) usuarios,
pero **solo si el usuario logueado tiene rol `ROLE_ADMINISTRATIVO`** — el resto de los
roles ni siquiera ve esos íconos, y el backend rechaza el `PUT`/`DELETE` con 403 aunque
alguien intente pegarle directo a la API sin pasar por la pantalla (ver el parche
provisorio del header `X-Usuario-Id` en el punto 1).

"Eliminar" en realidad **deshabilita** al usuario (`habilitado = false`), no borra la
fila. Se decidió así porque `Usuario` está referenciado por `Pedido`
(`creadoPor`/`representanteCurso`) y `HistorialEstadoPedido` (`modificadoPor`) — borrar
la fila de verdad rompería esas relaciones (o directamente fallaría por la foreign key)
y perdería la trazabilidad de quién gestionó qué pedido, algo que CLAUDE.md pide
explícitamente. Un usuario deshabilitado no puede loguearse (regla que ya existía en
`AuthService`) y desaparece del listado de usuarios corporativos.

Falta:
- Pantalla o filtro para ver los usuarios deshabilitados y poder reactivarlos (hoy no
  hay forma de deshacer un "eliminar" desde la UI, solo a mano en la base).
- Confirmar si un `ROLE_ADMINISTRATIVO` debería poder editarse/eliminarse a sí mismo
  (hoy puede — no hay ninguna restricción para eso).
- Evaluar si además de nombre/email/teléfono/rol hace falta poder resetear la
  contraseña de un usuario desde esta pantalla (relacionado con el punto 5).

## 7. Otros detalles menores que quedaron sueltos

- `Producto.imagenDisenoUrl` existe en el modelo pero la pantalla no tiene forma de
  subir/adjuntar una imagen todavía (solo se podría mandar una URL a mano).
- El formato de Nº de ficha se validó como `AAAA-NN` (ej: `2026-01`) por ser lo que se
  pidió, pero no está confirmado si ese es el formato real que va a usar el equipo de
  ventas en producción.
- `Colegio.provincia` no tiene ninguna validación de formato ni una lista cerrada de
  provincias (es un input de texto libre); se podría reemplazar por un `<select>`.
- No hay forma de ver/listar/editar pedidos ya creados desde el frontend todavía —
  esta pantalla solo cubre el alta.
