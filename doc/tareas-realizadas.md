# Tareas realizadas

Changelog de lo ya construido en el sistema, con las decisiones y detalles técnicos no
obvios de cada feature (el "por qué", no solo el "qué"). Pensado para que una sesión futura
no tenga que releer el código para entender por qué algo quedó como quedó.

Lo que falta hacer vive aparte, en [pantallas-pendientes.md](pantallas-pendientes.md) — ese
archivo se mantiene corto a propósito, listando solo trabajo pendiente real. Cuando algo de
ahí se termine, se migra el detalle acá y se borra de pantallas-pendientes.

Orden: más reciente primero. Última actualización: 2026-08-30.

## Selector de color con buscador (`ComboboxColor`) (2026-08-30)

Componente nuevo `frontend/src/components/ComboboxColor.tsx`: reemplaza cualquier `<select>`
nativo que liste `PaletaColores` por un combobox con buscador por nombre (input + lista
filtrada + navegación por teclado — flechas, Enter, Escape), sin agregar ninguna dependencia
nueva (la app no traía ninguna librería de combobox, así que es una implementación propia
liviana). Se hizo genérico a propósito para no repetir la lógica de filtrado en cada pantalla:
recibe `colores: PaletaColorResponse[]`, `value: number | ''`, `onChange`, y un flag
`compacto` para filas apretadas (ej. una planilla). Reemplazado en **todos** los lugares del
front que tenían un `<select>` de colores (se revisó todo el código buscando `idPaletaColor`,
no solo los mencionados a mano):
- `ModalColoresGotero.tsx` (Ficha Técnica): el selector de color al confirmar una posición del
  gotero, el selector de "Color de cierre", y el selector de color de `FilaInsumoEditor`
  (compartido por Capucha, Puños y cintura, e insumos libres).
- `features/stock/FilaStockNuevaRow.tsx` y `FilaStockGuardadaRow.tsx` (ver Registro de Stock
  más abajo).

`ModalDetalleColor.tsx` (Carta de colores) y `ModalNuevoColor.tsx` no tenían selector de color
(son la propia carta / el alta de un color nuevo, no una elección entre varios) — no se tocaron.

## Registro de Stock — Fase 1 (2026-08-30)

Pestaña nueva "Stock": auditoría física de telas por color y proveedor. No es un historial de
movimientos — es un "valor actual" por artículo que se pisa en cada carga, igual que
`ArticuloProveedor` (catálogo, no ledger). Ver [[project-planificador-compras-fase3]] para el
contexto de por qué existe la Fase 2 (todavía no implementada, ver pantallas-pendientes.md).

- Entidad nueva `ArticuloStock`: envoltorio 1 a 1 sobre `PaletaColores` (FK única) a propósito
  — el día que se sume stock de insumos indirectos (hilos, friselina, cintas), esta FK pasa a
  nullable y se agrega una segunda FK nullable hacia esa futura entidad, sin migrar `stock`.
- Entidad nueva `Stock`: `articulo` + `proveedor` + `cantidad` + `fechaUltimaActualizacion` +
  `actualizadoPor`, con unique constraint `(id_articulo_stock, id_proveedor)`. **Decisión
  tomada con el usuario** (ver [[feedback-ask-before-schema-decisions]]): `proveedor` es
  **nullable** — permite auditar "hay tanta cantidad de esta tela" sin saber con certeza de
  qué proveedor es (telas viejas en depósito sin trazabilidad). La unique constraint no cubre
  ese caso (MySQL no considera dos `NULL` iguales), así que "a lo sumo una fila sin proveedor
  por artículo" se hace cumplir a mano en `StockService.guardarUnItem` (busca-o-crea antes de
  insertar, mismo mecanismo que para un proveedor puntual).
- `unidadMedida` no se persiste: se deriva en `StockMapper` desde
  `articulo.paletaColor.tipoTela.esPorPeso` (mismo enum `UnidadMedida` que `ArticuloProveedor`).
- Servicio: `listar()` (join fetch de articulo→paletaColor→tipoTela, proveedor y
  actualizadoPor en una sola query, para no pagar N+1 en la grilla) y `guardarCambios` (todo o
  nada, `@Transactional`, upsert por item: resuelve o crea el `ArticuloStock` del color, busca
  la fila de `(articulo, proveedor)` — o la "sin proveedor" — y la actualiza, o crea una
  nueva). Mismo criterio de roles que Proveedores/Planificador: solo `ROLE_ADMINISTRATIVO`,
  tanto lectura como escritura (ver el punto ya abierto sobre esto en pantallas-pendientes.md).
- `DELETE /api/stock/{id}` sin restricciones de estado — se puede borrar una fila en cualquier
  momento (no hay concepto de "movimiento" que proteger).
- Endpoints: `GET /api/stock`, `POST /api/stock/guardar-cambios` (batch), `DELETE /api/stock/{id}`.

Frontend `StockView` — **rediseñado tras probar la primera versión** (el usuario la corrió a
mano y volvió con feedback concreto, ver más abajo): pestañas por tipo de tela, igual patrón
que `CartaColoresView` (misma lista `ORDEN_PESTANAS_PRIORITARIO`), en vez de una sola planilla
larga agrupada con `<h3>`. Cada pestaña filtra tanto las filas de stock como el selector de
colores de "+ Agregar fila" a la tela activa.

Guardado **por fila, no en batch**: se sacó por completo el botón global "Guardar cambios" y
el modal de resumen previo — cada fila tiene su propio tilde (✓) que guarda solo esa fila
llamando a `guardarCambios` con un array de un elemento (el backend no cambió, ya soportaba
esto). Una fila ya persistida (`FilaStockGuardadaRow`) muestra color/proveedor/cantidad de solo
lectura con un lápiz al lado; recién al tocar el lápiz se desbloquea **la fila completa**
(color, proveedor y cantidad, los tres editables — no solo la cantidad, por si al auditar se
dieron cuenta de que cargaron mal el proveedor y lo quieren corregir ahí mismo en vez de borrar
y recrear la fila) — así "última actualización"/"actualizado por" quedan atadas a una acción
explícita del usuario, no a cada tecla tipeada. El tilde dispara un `ModalConfirmacion` puntual
de esa fila mostrando "antes → después" (color · proveedor · cantidad) antes de guardar (mismo
aviso de sobreescritura que pedía el negocio, ahora por fila en vez de en un resumen agrupado);
si no cambió nada, no llama a la API.

Cambiar color y/o proveedor de una fila existente es más delicado de lo que parece: `Stock` se
identifica en el backend por `(articulo, proveedor)`, no tiene "renombrar" una fila — así que
`StockView.handleGuardarFilaExistente` primero crea la fila con la identidad nueva
(`guardarCambios`) y **recién después** borra la vieja (`DELETE`), en ese orden, para no perder
el dato si la creación llegara a fallar. Antes de eso, si la combinación nueva de color+
proveedor ya la usa otra fila de la grilla, se bloquea con un error inline en vez de dejar
guardar (mismo criterio de "no duplicar" que en altas nuevas). El color solo se puede cambiar
dentro de la misma pestaña/tela — para mover una fila a otra tela hay que borrarla y crearla de
nuevo ahí.

"+ Agregar fila" ya no es un popup: agrega una fila nueva (`FilaStockNuevaRow`) al final de la
tabla de la pestaña activa, con selects de color (solo los de esa tela) + proveedor + input de
cantidad + su propio tilde/cruz — cancelar una fila nueva sin guardar solo la saca del estado
local, no llama al backend. El tachito de basura de una fila ya persistida sigue pidiendo
confirmación (`DELETE` + `ModalConfirmacion`, por ser destructivo).

Probado con `curl` sobre datos reales (proveedores/color de prueba, borrados después):
mismo color con 2 proveedores distintos + una fila "sin proveedor" → 3 filas independientes;
pisar la cantidad de un proveedor no afecta a los otros dos; guardado de una fila individual
(alta y sobrescritura) con el mismo payload de 1 elemento que ahora manda el tilde por fila;
`DELETE` de una fila puntual; rechazo con 403 al guardar sin rol `ROLE_ADMINISTRATIVO`; rechazo
con 400 ante cantidad negativa (`@PositiveOrZero` en el DTO, mismo criterio que
`ArticuloProveedor.precioEstimado`). Verificado el DDL generado por `ddl-auto=update`:
`stock.id_proveedor` sin `not null`, `articulos_stock.id_paleta_color` con la unique
constraint. Frontend type-checkea limpio (`tsc --noEmit`) y Vite transforma los componentes
nuevos sin error, pero de nuevo **no se pudo hacer un click-through real en un navegador** en
esta sesión (sin herramienta de automatización de browser disponible) — el primer redondeo de
feedback del usuario vino de que él sí la probó a mano.

## Borradores de Planificación de Compra en base de datos (2026-08-30)

Reemplaza el borrador en `localStorage` de la entrada anterior por borradores reales en la
base — decisión tomada con el usuario (ver [[feedback-ask-before-schema-decisions]]): permite
varios borradores en simultáneo (antes solo uno por navegador), visibles desde cualquier
dispositivo, y que aparezcan en el listado de planificaciones para poder retomarlos.

- `PlanificacionCompra` suma un campo `estado` (`BORRADOR`/`CONFIRMADA`, nuevo enum
  `EstadoPlanificacionCompra`). `nombre`/`fechaDesde`/`fechaHasta` pasaron a nullable — un
  borrador recién creado puede no tener nada todavía; se exigen recién al confirmar. **Nota de
  migración**: como ya pasó antes con este mismo tipo de cambio (ver
  [[project-frontend-pedido-gap]]), `ddl-auto=update` agrega columnas nuevas pero no relaja un
  `NOT NULL` existente — hubo que correr `ALTER TABLE ... MODIFY COLUMN ... NULL` a mano sobre
  esas tres columnas en la base dev.
- Entidad nueva `PlanificacionCompraProductoBorrador` (planificación + producto, sin más
  campos): a propósito **no** reusa `PlanificacionCompraDetalle` para la selección en curso,
  porque calcular cantidad/tipoTela/color (`calcularDetalles`) requiere que el producto tenga
  diseño completo — y un borrador puede tener productos tildados que todavía no lo tienen
  (esa es la idea: el usuario sigue decidiendo). El cálculo real recién ocurre al confirmar,
  momento en el que estas filas se vacían y se reemplazan por `PlanificacionCompraDetalle`.
- Servicio: `guardarBorrador` (crear o actualizar, sin validar nada — se llama seguido, es el
  autoguardado) / `obtenerBorrador` (para "continuar editando") / `confirmar` (ahí sí se
  revalida todo: nombre, fechas, al menos un producto, todos con diseño completo) /
  `eliminarBorrador` (solo mientras `estado=BORRADOR`; una vez confirmada no se puede borrar
  ni editar, sigue igual que antes). El viejo `crear(PlanificacionCompraRequest)` de un solo
  paso se eliminó — ahora todo pasa por guardar-borrador-y-después-confirmar.
- Endpoints: `POST/PUT /api/planificaciones-compra/borradores[/{id}]`,
  `GET /api/planificaciones-compra/{id}/borrador`, `POST .../{id}/confirmar`,
  `DELETE .../{id}` (solo BORRADOR). Mismo criterio de roles que el resto del módulo
  (`ROLE_ADMINISTRATIVO`, sin distinción de dueño — cualquier administrativo puede ver/editar
  el borrador de otro, no hay concepto de "borrador privado" en este sistema).

Frontend: `NuevaPlanificacionView` autoguarda contra la base con demora corta (700ms sin
cambios) en vez de escribir a `localStorage` en cada cambio — `crearBorradorPlanificacion` la
primera vez, `actualizarBorradorPlanificacion` después, usando el id que devuelve la primera
respuesta. El id vive en el componente padre (`PlanificadorComprasView`, no en el hijo) porque
la flecha "Volver" del `AppHeader` —que se renderiza en el padre— necesita saber si hay algo
guardado para decidir si pregunta o no. `PlanificacionesListView` ahora muestra los
`BORRADOR` con borde verde, badge "Borrador" y botón "Continuar editando"; "+ Nueva
planificación" siempre arranca sin id (`idBorradorActual = null`), nunca reengancha un
borrador existente. Se borró `features/planificador-compras/borradorPlanificacion.ts`
(el módulo de `localStorage` de la entrada anterior, ya no hace falta).

Probado con `curl` sobre datos reales: crear borrador vacío, actualizarlo con nombre/fechas/
productos, `GET .../borrador` para "continuar editando", el listado trayendo el borrador con
su `estado`, confirmar (pasa a CONFIRMADA con el detalle calculado), rechazo de `DELETE`/`PUT`
sobre una ya confirmada (409 en ambos), y rechazo de confirmar con un producto de diseño
incompleto (la fila de borrador queda intacta, se pudo eliminar después). Datos de prueba
borrados de la base dev al terminar. `mvnw clean compile` y `npm run build` sin errores — no
se pudo probar la UI en navegador.

## `ModalConfirmacion` reutilizable + flujo del borrador de Planificación (2026-08-30)

`frontend/src/components/ModalConfirmacion.tsx`: popup de confirmación genérico (título +
mensaje + N acciones con variante `primaria`/`secundaria`/`peligro`), mismo estilo que el
resto de los modales del sistema (overlay + tarjeta blanca). Reemplaza los `window.confirm`
que se venían usando en el Planificador de Compras — pensado para reusarse en cualquier otra
pantalla que necesite plantear una decisión de 2+ opciones antes de algo irreversible.

Ajustes al flujo del borrador de "Nueva planificación":
- Si hay una planificación en curso guardada, `PlanificadorComprasView` arranca directo en la
  pantalla de creación (con el borrador ya cargado), no en el listado — antes había que
  clickear "+ Nueva planificación" para volver a verla aunque ya estuviera guardada.
- La flecha "Volver" del `AppHeader` ahora abre un `ModalConfirmacion` de **3 opciones**
  ("Guardar borrador y salir" / "Eliminar borrador y salir" / "Seguir editando") en vez del
  sí/no de antes — "guardar" no hace nada especial más que no borrar (el borrador ya se
  persiste solo en cada cambio), pero se lo ofrece como opción explícita.
- El botón "Cancelar" del formulario sigue siendo binario (sí, borrar y salir / no, seguir
  editando) pero ahora usa el mismo `ModalConfirmacion` en vez de `window.confirm`.

`npm run build` sin errores. No se pudo probar en navegador.

## Planificador de Compras — más filtros y fix del borrador (2026-08-30)

Sobre la pantalla de creación (ver entradas de más abajo):
- Filtro de tipo de prenda: pasó de chips siempre visibles a un desplegable de selección
  múltiple (`FiltroTipoPrenda.tsx`, checkboxes, cerrado por defecto, se cierra solo al
  clickear afuera — mismo criterio que `CambiarEstadoPopover`) porque los chips ocupaban
  demasiado espacio horizontal con varios tipos de prenda.
- Se agregó filtro de "Estado" (mismo patrón que ya usa `PedidosListView`: `<select>` con
  `ESTADOS_PEDIDO`/`ESTADO_PEDIDO_LABELS`) y un tilde "Excluir ya planificados" (oculta los
  productos con `planificacionesQueLoIncluyen.length > 0`).
- Se sacó el botón "Buscar productos": ahora el rango de fechas arranca vacío y la búsqueda
  se dispara sola en cuanto ambas fechas están cargadas — funciona igual que el resto de los
  filtros (reactivo, sin acción explícita).

**Bug real encontrado y arreglado**: al restaurar un borrador con fechas ya cargadas (volver a
la pantalla después de haber tildado productos y navegado a otro lado), la selección se
perdía aunque nombre y fechas sí se restauraban. Causa: el efecto que dispara la búsqueda
usaba un `useRef` como flag de "es la primera búsqueda, preservar selección" que se consumía
(pasaba a `false`) apenas se leía. En desarrollo, `StrictMode` (activo en `main.tsx`) invoca
los efectos del montaje inicial **dos veces seguidas** para detectar justamente este tipo de
problema — la primera invocación preservaba la selección con el flag en `true`, pero como el
`ref` ya había quedado en `false`, la segunda invocación (disparada por el mismo montaje, no
por una acción del usuario) volvía a buscar con `preservarSeleccion=false` y la limpiaba.
Arreglado reemplazando el `ref` por una condición que se recalcula sola en cada invocación
(`elegibles === null`, es decir "todavía no hay resultados de este montaje") en vez de un
flag que se consume una sola vez — en ambas invocaciones de StrictMode da el mismo resultado
(`true`) porque la búsqueda async ni siquiera resolvió todavía, así que ninguna de las dos
pisa la selección. Efecto secundario aceptado: en desarrollo se dispara la búsqueda dos veces
en ese caso puntual (pedido HTTP duplicado, gratis) — mismo comportamiento que ya tienen otros
`useEffect` de fetch-on-mount en el proyecto con `StrictMode`, no es nuevo.

`npm run build` sin errores. No se pudo probar en navegador — el usuario reportó el bug real
al usar la pantalla en desarrollo (`npm run dev`, donde `StrictMode` sí aplica; no se
manifestaría en el build de producción).

## Planificador de Compras — ajustes de UX en la pantalla de creación (2026-08-30)

Sobre la entrega de Fase 3 del mismo día (ver más abajo), a pedido del negocio:
- Filtros: se sacó "Colegio" (no tenía catálogo real detrás, ver pantallas-pendientes.md) y
  se agregaron "Tipo de prenda" (select, valores de los productos ya traídos) y rango de
  "% pagado" (dos inputs numéricos de ingreso libre, sin opciones predefinidas) — todo sigue
  filtrando 100% client-side sobre la respuesta ya cargada, sin volver a pegarle al backend.
- Cada fila de producto ahora muestra: la imagen de ficha técnica a la izquierda (si tiene —
  click abre `ImagenPreviewModal`, el mismo componente que ya usa Ficha Técnica, no uno
  nuevo), moldería + swatches de los colores marcados, `EstadoBadge` del producto (mismo
  componente que Ficha Técnica), y fecha de venta además de la de entrega — importante en
  negro, secundario (colegio, fechas) en gris chico. Requirió sumar `fechaVentaPedido`,
  `nombreMolderia` y `numeroInternoMolderia` a `ProductoElegibleResponse` (antes solo traía
  `fechaEstimadaEntregaPedido`); `producto.estadoActual`/`imagenDisenoUrl`/`colores` ya venían
  en `ProductoResponse`, no hizo falta tocar nada para esos tres.
- **Borrador persistente en `localStorage`** (`features/planificador-compras/borradorPlanificacion.ts`,
  clave `wc-planificacion-compra-borrador`): filtros, nombre y selección se guardan ante
  cualquier cambio, y se restauran solos (relanzando la búsqueda) si el usuario navega a otra
  pantalla y vuelve — antes se perdía todo al desmontar el componente. El borrador se limpia
  al confirmar la planificación o al cancelar. "Cancelar" (tanto el botón del formulario como
  la flecha "Volver" del `AppHeader`, que antes lo esquivaba sin avisar) pide confirmación
  con `window.confirm` — mismo patrón que ya usa `UsuariosView` para eliminar un usuario — solo
  si hay algo cargado (nombre o selección), para no molestar en una pantalla recién abierta.
- Pendiente documentado (no implementado): mostrar si un pedido ya tiene talles/medidas
  cargados por los alumnos, como otra señal para decidir si conviene comprar la tela todavía
  — ese dato no existe en el sistema aún (Carga Descentralizada, ver pantallas-pendientes.md).

Verificado con `curl` que `fechaVentaPedido`/`nombreMolderia`/`numeroInternoMolderia` llegan
bien poblados desde datos reales de la base dev. `mvnw clean compile` y `npm run build` sin
errores — sin probar la UI en navegador. Nota de proceso: la primera compilación después de
este cambio falló en runtime con "Unresolved compilation problem" pese a que `mvnw compile`
decía "Nothing to compile" — es el gotcha ya documentado de corrupción del build incremental
(ver [[project-toolchain-gotchas]]); `mvnw clean compile` lo resolvió, como siempre.

## Planificador de Compras — Fase 3 (2026-08-30)

Entidades nuevas `PlanificacionCompra` (cabecera) y `PlanificacionCompraDetalle` (una fila por
cada (`TipoTela`, `PaletaColores`) que un producto consume). Decisiones tomadas con el negocio
antes de programar (ver [[feedback-ask-before-schema-decisions]]):
- El filtro de fecha (tanto en `GET /api/productos/elegibles-planificacion` como en el
  período que etiqueta la cabecera) es sobre **`Pedido.fechaEstimadaEntrega`**, no
  `fechaVenta` — "qué necesito tener comprado para lo que se entrega en este rango".
- El estimado en pesos del resumen unificado usa el **proveedor preferido** de cada color,
  no el más barato ni un promedio — requirió agregar `ArticuloProveedor.preferido`
  (boolean, Fase 2) con la regla "a lo sumo uno `true` por color", que
  `ArticuloProveedorService` hace cumplir desmarcando cualquier otro al setear uno nuevo
  (no hay unique constraint de base para esto, la regla es "a lo sumo uno", no "exactamente
  uno"). Se agregó el toggle ★/☆ en `ModalDetalleColor.tsx` (Carta de colores) para poder
  marcarlo desde la UI; si nadie marcó un preferido para un color, el estimado queda `null`
  (sin fallback automático a otro precio).

**`InsumoFijoTipoPrenda` no se creó como entidad nueva**: el pedido original asumía que
existía un catálogo con ese nombre para "insumos esperados por tipo de prenda", pero lo que
ya existía era `ProductoService.CODIGOS_INSUMOS_SUGERIDOS_POR_PRENDA` — un `Map` hardcodeado
que hasta ahora solo se usaba para *sugerir* un default al confirmar colores (nunca se
exigía). Se reusó tal cual como la lista de "esperados" para
`ProductoService.motivoDisenoIncompleto(Producto)` (nuevo método público), que además
verifica: `tipoTela` asignado (necesario para saber la unidad de la tela de cuerpo, no estaba
en el pedido original pero es imprescindible para el cálculo), `patronCorte` asignado, y
todas las posiciones de `PatronCorteColor` con su `ProductoColor` cargado. Este método es la
única fuente de verdad de elegibilidad: lo usan tanto el endpoint de productos elegibles como
`PlanificacionCompraService.crear` (revalida server-side por si el front quedó desactualizado).

Cálculo de cada `PlanificacionCompraDetalle` (`PlanificacionCompraService.calcularDetalles`):
gramos de cada posición ya marcada (`ProductoColor.patronCorteColor.gramos`) más cada
`ProductoInsumoSecundario.cantidad` ya cargado (sin recalcular desde ningún default), todo
× `producto.cantidadTotal`, fusionado en una sola fila por (tipoTela, color) dentro de ese
producto. Es una foto real: los valores quedan copiados en la fila, no se recalculan al leer
(`obtenerResumen`/`obtenerDetallePorProducto` leen `detalle.getCantidad()`/`getUnidadMedida()`
directo, sin tocar `Producto` de nuevo) — verificado por inspección de código, no con una
mutación en vivo sobre datos reales de la base dev.

Roles: **todo el módulo** (lectura incluida: elegibles, resumen, detalle, listado) restringido
a `ROLE_ADMINISTRATIVO`, mismo criterio no confirmado explícitamente que ya se aplicó en
Proveedores/Fase 2 (ver esa entrada más abajo) — el ítem "Planificador Compras" del Sidebar
ahora solo se muestra a ese rol (antes era un link inerte visible a todos, sin `view` asignada).

Filtro de colegio en la pantalla de creación: **100% client-side**, a partir de los
`nombreColegio` que ya trae la respuesta de elegibles — no se construyó un
`GET /api/colegios` nuevo ni se agregó `idColegio` como filtro real desde el front (el
backend sí acepta el query param `idColegio` opcional, sin usar por ahora). Se decidió así
para no adelantar el trabajo de "Reutilización de Colegios existentes" que ya está pendiente
aparte (ver pantallas-pendientes.md) — cuando ese catálogo exista, conviene revisar si el
filtro debería pasar a ser server-side.

Frontend: `frontend/src/features/planificador-compras/` (`PlanificadorComprasView` con
sub-vistas listado/nueva/detalle, mismo patrón que `PatronesCorteView`).
`utils/consumoProducto.ts` reimplementa en TypeScript el mismo cálculo de
`calcularDetalles` del backend (mismo criterio de agrupación) para el contador en vivo de la
pantalla de creación al tildar/destildar productos — es solo una previsualización, el cálculo
autoritativo sigue siendo el del backend al confirmar.

Probado con `curl` contra el backend real sobre datos reales de la base dev (no datos
sintéticos): productos con diseño completo/incompleto detectados correctamente (Buzo con
insumos faltantes, Remera sin colores marcados), rechazo 409 al incluir un producto
incompleto, creación con productos de distintos pedidos/colegios, reinclusión de un producto
ya usado en otra planificación sin bloqueo (solo badge), resumen unificado agrupando
correctamente por (tipoTela, color), y el estimado en pesos vía proveedor preferido
(14850 kg × $2500 = $37.125.000) incluyendo que un segundo "preferido" para el mismo color
desmarca al primero. Todos los datos de prueba se borraron de la base dev al terminar.
`mvnw clean compile` y `npm run build` (tsc + vite) sin errores — sin probar la UI en
navegador (no hay herramienta de browser en esta sesión).

## Catálogo de Proveedores + pantalla "Carta de colores" (2026-08-30)

Entidad `Proveedor` nueva, fuera del diagrama de clases de CLAUDE.md (ahí `MateriaPrima.proveedor`
era solo un string libre, y `MateriaPrima` en sí no está implementada — es de M3, sin empezar).
Se decidió con el negocio antes de programar (ver también
[[feedback-ask-before-schema-decisions]]):
- `Proveedor.cuit` único a nivel global, se guarda **solo los 11 dígitos** (sin guiones); el
  formato `XX-XXXXXXXX-X` es responsabilidad de la UI al mostrarlo (`formatearCuit` en
  `types/proveedor.ts`). Sin dígito verificador real, solo formato/longitud.
- `ArticuloProveedor` vincula un `Proveedor` con un color de `PaletaColores`, con
  `UnidadMedida` (`KG`/`UNIDAD`, derivada de `TipoTela.esPorPeso`, no elegible por proveedor),
  precio estimado opcional y `activo`. Único por **(proveedor, color)**, no global — puede
  haber varios proveedores cargados para el mismo color a propósito, para comparar precio.
- Sin pantalla de administración de `Proveedor` separada: se crea al vuelo desde el selector
  de proveedores al cargar un color nuevo (nombre + CUIT), quedando disponible para reusar
  después. El backend sí expone `PUT /api/proveedores/{id}` y
  `PATCH /api/proveedores/{id}/activo` (editar/dar de baja), pero **sin ninguna UI que los
  llame todavía** — quedan para cuando haga falta una pantalla de gestión de proveedores.
- **Lectura también restringida a `ROLE_ADMINISTRATIVO`** (no solo alta/baja): a diferencia de
  `TipoTela`/`PaletaColores` (catálogos de lectura libre), acá se restringió también el `GET`
  porque el catálogo incluye precios de proveedores, un dato comercialmente sensible. Fue una
  decisión propia al implementar, no confirmada explícitamente con el negocio — revisar si en
  algún momento otro rol (ej. Compras/Planta) necesita leer este catálogo sin poder editarlo.

Frontend: `frontend/src/features/carta-colores/` (`CartaColoresView`, `ModalNuevoColor`,
`ModalDetalleColor`, `ColorGoteroInput`), nueva pestaña en el submenú Config (Sidebar), solo
`ROLE_ADMINISTRATIVO`.
- **Pestañas dinámicas por `TipoTela`**: se arman desde `GET /api/tipos-tela` (ya filtra
  `activo=true`), no de una lista fija — un tipo de tela nuevo aparece con su propia pestaña
  sin tocar código frontend. Esto incluye a "Cierre" como una pestaña más (ya es una fila del
  catálogo `tipos_tela`), a pedido explícito del negocio.
- El "gotero" (imagen de referencia → click → leer pixel → hex) es una implementación nueva y
  standalone en `ColorGoteroInput.tsx`, **no** una extracción compartida con el gotero que ya
  existía en `ModalColoresGotero` (ese está fuertemente acoplado a Producto/Moldería/
  posiciones) — mismo mecanismo (canvas + `getImageData`), componente aparte. La imagen se
  carga con `URL.createObjectURL` solo en el navegador y se descarta (`revokeObjectURL`); nunca
  se sube al backend. Alternativa: `<input type="color">` nativo como selector RGB a ojo.
- `services/cartaColoresService.ts` tiene su propio `crearColorCarta` con `tipoTela: string`
  (no el union `TipoTela` acotado de `types/paletaColores.ts`, usado por el modal viejo) —
  necesario porque acá se puede crear un color para cualquier tipo de tela del catálogo
  dinámico, incluyendo uno que ese union todavía no conozca.
- **Simplificación consciente**: la card de cada color en la grilla no muestra un badge de
  "N proveedores" (para eso habría que traer el conteo de todos los colores de la pestaña de
  una sola vez, y no hay endpoint para eso todavía) — hay que abrir el color para ver sus
  proveedores cargados. Agregar un endpoint de conteo agrupado si hace falta el dato a simple
  vista.
- Enganche dejado para el Planificador de Compras (M3, sin empezar):
  `ArticuloProveedorRepository.findByPaletaColorAndActivoTrue` resuelve, dado un color, qué
  proveedores activos y precios existen.

Probado con `curl` contra el backend real (alta/duplicado de CUIT, alta/duplicado de artículo,
edición de precio y baja, guardia de "proveedor inactivo no admite artículos nuevos", 403 sin
`X-Usuario-Id`) — sin probar la UI en navegador (no hay herramienta de browser disponible en
esta sesión). `npm run build` (tsc + vite build) y `mvnw clean compile` sin errores.

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
