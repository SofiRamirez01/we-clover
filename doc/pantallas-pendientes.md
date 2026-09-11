# Pendientes

Lista de trabajo pendiente real: lo que quedó afuera a propósito, decisiones sin confirmar
con el negocio, y riesgos/bugs conocidos sin corregir. Se mantiene corta a propósito — el
detalle de lo ya construido (el "por qué" de cada decisión ya tomada) vive en
[tareas-realizadas.md](tareas-realizadas.md). Cuando algo de acá se resuelva, mover el
detalle a ese archivo y borrarlo de este.

Última actualización: 2026-09-10.

## Importación de pedidos desde Excel (Kommo)

`POST /api/pedidos/importar-excel` (botón "Importar Excel" en el listado de Pedidos) lee el
Excel de exportación de leads de Kommo y da de alta un Pedido por fila. El mapeo de columnas se
acordó con el negocio fila por fila (ver `PedidoImportRowService` para el detalle de cada regla).
Puntos a tener presentes:

- **Migración manual pendiente en cualquier entorno nuevo**: `Usuario.email` pasó a ser
  nullable (antes `NOT NULL UNIQUE`) porque el Excel no siempre trae el email del representante
  de curso y no se quiso inventar uno. `spring.jpa.hibernate.ddl-auto=update` **no afloja** un
  `NOT NULL` ya existente en una columna — hay que correr a mano
  `ALTER TABLE usuarios MODIFY email VARCHAR(150) NULL;` en cada base que no sea nueva (ya se
  corrió en la base de desarrollo local). El alta manual de un pedido (`PedidoCreateRequest`)
  sigue exigiendo el email a nivel de DTO — esto solo afecta a representantes creados por
  importación.
- **Posiciones de columna hardcodeadas** (`PedidoImportService`, constantes `COL_*`): se
  matchean por posición (0-based), no por nombre de encabezado, porque la columna "Responsable"
  aparece dos veces con el mismo texto (vendedor y responsable de curso alumno/adulto) y el
  nombre por sí solo es ambiguo. Si Kommo cambia el orden de columnas de esta exportación, hay
  que actualizar esas constantes a mano — no hay ninguna validación que lo detecte automáticamente
  (una columna corrida silenciosamente podría, por ejemplo, leer "Provincia" donde se espera
  "Colegio").
- **Vendedor**: se busca por coincidencia parcial de nombre (`findFirstByNombreContainingIgnoreCaseAndHabilitadoTrue`)
  contra la columna "Responsable" de Kommo (que trae un apodo corto, ej. "Sofi"). Si el apodo
  matchea a más de un usuario habilitado (ej. "Sofia Ramirez" y "Sofia" en la base de prueba),
  se queda con el primero que devuelva la consulta — sin orden definido. No es un problema
  bloqueante hoy (pocos usuarios), pero si crece el equipo puede asignar el pedido al vendedor
  equivocado sin avisar.
- **`curso` no tiene columna de origen** en el Excel: queda siempre como placeholder `"Sin
  curso"` a corregir a mano después de importar (decisión tomada con el negocio: no inventar un
  valor más específico).
- **`cantAlumnos` se aproxima** con la cantidad máxima entre las prendas del pedido (ej. 29
  Buzos + 28 Chombas → 29), no es un dato real de Kommo — puede no coincidir con la cantidad
  real de alumnos si alguno pidió más de una prenda.
- **Costo por prenda y "Precio Unitario" del listado**: si el pedido tiene un solo tipo de
  prenda (sin contar Bandera, que siempre es regalo — cantidad fija 1, costo 0), su costo es el
  "Precio Unitario" real del Excel. Si tiene más de un tipo, no se inventa un costo por prenda:
  los `Producto.costo` quedan en 0, el total real del Excel ("Presupuesto") se guarda aparte en
  `Pedido.montoReferenciaImportado`, y el "Precio Unitario" real del Excel (que en Kommo es un
  precio por alumno/paquete, no por prenda — no se puede derivar dividiendo el total por las
  unidades) se guarda aparte en `Pedido.precioUnitarioReferenciaImportado`. Ninguno de los dos
  participa en ningún cálculo — son puramente de respaldo para no perder el dato.
  `PedidoService.construirRespuesta` calcula, en cada request (no se guarda), dos columnas:
  `precioTotal` (suma de cantidad×costo de cada producto, o `montoReferenciaImportado` si esa
  suma da 0) y `precioUnitario` — el "precio del combo": suma de `Producto.costo` de cada tipo
  de prenda del pedido, **sin** ponderar por cantidad (ej. Buzo $120.000 + Chomba $90.000 =
  $210.000; no calza con `precioTotal / cantAlumnos` y está bien que no calce), o
  `precioUnitarioReferenciaImportado` si esa suma da 0. Es el mismo criterio para pedidos
  cargados a mano y para importados — decidido así con el negocio para no guardar una columna
  redundante que hay que mantener sincronizada a mano en cada edición de producto.
- **Nº de ficha (`codigoInterno`) y detección de duplicados**: se regenera como año de "Promo" +
  el número completo de la Ficha de Kommo, nunca truncado (ej. `323-27` → `2027-323`). Al ser
  una transformación determinística, volver a importar la misma fila de Kommo siempre genera el
  mismo código, y como `codigoInterno` es único, `PedidoImportRowService.generarCodigoInterno`
  usa `existsByCodigoInterno` para detectar que esa fila ya se importó antes y saltearla (se
  reporta en `filasSalteadas` como cualquier otro error de fila, sin necesidad de guardar el
  número de Kommo aparte solo para compararlo). Esto solo es seguro porque el patrón
  `^\d{4}-\d{2,}$` de `PedidoCreateRequest`/`PedidoUpdateRequest` (backend) y
  `FORMATO_NUMERO_FICHA` (`NuevoPedidoView.tsx`, frontend) ya no limita NN a exactamente 2
  dígitos — antes sí lo hacía (`^\d{4}-\d{2}$`), lo cual era un bug real e independiente de esta
  importación: al superar los 99 pedidos de un año, un `codigoInterno` como `2026-100` ya
  hubiera fallado la validación del alta/edición manual también. Se corrigió al mismo tiempo. La
  columna "Promo" en sí tampoco se persiste como campo propio (decisión del negocio) — solo se
  usa de forma transitoria para calcular el año.
- **Pique/Tejido**: si alguna de las dos columnas dice "Sí", se agrega una nota en
  `observaciones` ("Lleva Pique", "Lleva Tejido" o "Lleva Pique y Tejido"); si ambas son "No" no
  se agrega nada. Se concatena con las notas no vacías de "Nota 1" a "Nota 5" del Excel.
- **Campos del Excel que se decidió no guardar**: todo lo que es metadata de CRM/marketing
  (utm_*, gclid/fbclid, Estatus del lead, Embudo de ventas, Fuente, Tareas próximas, fechas del
  embudo de venta previo a la producción como "Fecha de Derivación/Cotizado/Interesado", etc.) —
  ver el análisis completo hecho antes de programar esto, no repetido acá para no duplicar.
- Cada fila se importa en su propia transacción (`PedidoImportRowService.importarFila`,
  `REQUIRES_NEW`): una fila con datos inválidos se saltea y se lista con el motivo en la
  respuesta del endpoint (mostrado en el modal del frontend) — el resto de las filas se importa
  igual.

## Carga de Talles: desglose por talle para producción

La Carga de Talles (link público por Pedido) registra el talle de cada alumno para
consulta, pero no desglosa `Producto.cantidadTotal` por talle (ej. "12 buzos talle 2, 8
buzos talle 3") para que planta sepa cuánto cortar de cada uno. Conectarlo con
producción/patrón de corte es un cambio aparte — probablemente vinculado a cómo cada
talle consume distinta cantidad de tela (hoy el gramaje sale de `PatronCorteColor`,
pensado para un solo talle, no por talle).

## generales
Componentes de añadir, de filtros, algunos son grises o tienen distinta estetica, unificar, elegir libreria de componentes o ver cual usa para dar mejores indicaciones.
Botones de editar, borrar unificar iconos y colores.

## Registro de Stock — insumos indirectos y descuento real por corte

Fases 1 y 2 (ver tareas-realizadas.md) ya cubren el registro de stock por color/proveedor y su
cruce puramente informativo con el Planificador de Compras (`cantidadNecesaria` /
`stockDisponible` / `cantidadAComprar`, sin tocar el `Stock` real). Quedan dos frentes
explícitamente afuera de esa entrega:

- **Stock de insumos indirectos** (hilos, friselina, cintas, etc.) que no son colores de la
  carta de colores. El modelo ya deja lugar para esto (`ArticuloStock` como envoltorio
  genérico sobre el artículo auditado, ver `ArticuloStock.paletaColor`), pero falta definir:
  cómo se catalogan estos insumos (¿tienen color? ¿unidad de medida propia, ej. metros de
  cinta, madejas de hilo?), si tienen proveedor/precio como `ArticuloProveedor`, y si su
  consumo se calcula con un mecanismo parecido al de Planificación de Compra o necesita uno
  propio.
- **Cómo se descuenta el stock real cuando efectivamente se corta un producto.** No está
  resuelto todavía — probablemente hay que engancharlo a algún cambio de estado de
  `Producto`/`Pedido` (ej. al pasar a un estado de "cortado"), pero eso queda para una fase
  futura. Mientras tanto, confirmar una `PlanificacionCompra` sigue sin modificar `Stock` (fue
  una decisión explícita, no un olvido).

  Actualmente si actualizo el stock, me actualiza todas las planificaciones que contengan ese stock, esta en tiempo real, ver si eso no afecta, si la planificacion es un snapshoot del stock en tal momento. 

También sin confirmar con el negocio, mismo tipo de decisión que en Proveedores y
Planificador: se restringió lectura y escritura de `/api/stock` a `ROLE_ADMINISTRATIVO`.

## Planificador de Compras: sin M4/nesting, sin baja/edición, sin dato de talles

Cosas que quedaron explícitamente afuera de la Fase 3 (ver tareas-realizadas.md):
- La pantalla de creación no tiene filtro de colegio (se sacó a pedido del negocio: ya se
  puede filtrar por tipo de prenda y por rango de % pagado). El backend sigue aceptando
  `idColegio` como query param opcional en `/api/productos/elegibles-planificacion`, sin usar
  desde ningún lado del front — si en algún momento hace falta el filtro de vuelta, conviene
  resolverlo junto con "Reutilización de Colegios existentes" (ver más abajo) en vez de
  reagregar el que se sacó.
- Una `PlanificacionCompra` **ya CONFIRMADA** no se puede editar ni borrar (ni
  quitarle/agregarle productos) — si hace falta corregir una, hoy la única forma es crear una
  nueva. Mientras está en `BORRADOR` sí se puede editar/eliminar libremente (ver la entrada de
  borradores en base en tareas-realizadas.md).
- Nada de esto se conecta todavía con Compras real (generar una orden de compra, marcarla
  como comprada/recibida) ni con el Motor de Nesting/Tizada (M4) — el Planificador solo
  calcula cuánto comprar, no optimiza cómo cortarlo.
- `ROLE_ADMINISTRATIVO` como único rol con acceso (lectura incluida) fue una decisión propia,
  igual que en Proveedores — ver el punto de abajo, aplica también acá.
- **No hay dato de si se cargaron los talles de un pedido/producto** (la carga descentralizada
  de medidas/talles de alumnos, mencionada en CLAUDE.md, todavía no está construida — ver
  "Módulo completo de Carga Descentralizada" más abajo). Cuando ese dato exista, la fila de
  producto de esta pantalla debería mostrarlo como un indicador más (ej. un ícono/badge de
  "talles cargados" o "sin talles") — hoy no bloquea nada, es información que el negocio
  quiere ver para decidir si conviene comprar la tela todavía o conviene esperar a tener los
  talles confirmados.

## Catálogo de Proveedores: sin pantalla de edición/baja

`ProveedorService.actualizar`/`cambiarActivo` y sus endpoints (`PUT /api/proveedores/{id}`,
`PATCH /api/proveedores/{id}/activo`) ya existen, pero **ninguna pantalla los llama** — el
único flujo de alta hoy es la creación inline desde el selector de proveedores en "Carta de
colores" (ver tareas-realizadas.md). Falta decidir si hace falta una pantalla de
administración de proveedores aparte (editar nombre/CUIT, reactivar uno dado de baja) o si
alcanza con seguir gestionándolo así.

También sin confirmar con el negocio: se restringió la **lectura** de `/api/proveedores` y
`/api/articulos-proveedor` a `ROLE_ADMINISTRATIVO` (no solo el alta/baja), por ser datos de
precios — fue una decisión propia al implementar, no algo que se haya preguntado
explícitamente. Revisar si otro rol (ej. uno de Compras/Planta, cuando exista) necesita leer
este catálogo.

## Catálogo `TipoTela`: sin pantalla de administración

Solo hay lectura (`GET /api/tipos-tela`) — falta una pantalla de alta/edición del catálogo
(agregar un tipo nuevo, ej. "Corderito", sin tocar código; editar
`esPorPeso`/`telaCuerpo`/`gramosSugerido`/`activo`).

## Seguridad real (JWT / Spring Security)

Hoy **todos los endpoints del backend están abiertos**, con o sin login — la única
protección es el parche provisorio de header `X-Usuario-Id` (ver tareas-realizadas.md), que
no es autenticación real: cualquiera puede mandar cualquier id manualmente (con curl,
Postman, etc.) y hacerse pasar por otro usuario, porque nada está firmado ni verificable del
lado del servidor.

Falta:
- Emitir JWT en `POST /api/auth/login` e instalar un filtro de seguridad real.
- Mapear los roles (Administrador, Operativo, Cortador, Ventas — y los que se agreguen) a
  `GrantedAuthority` como pide CLAUDE.md.
- Reemplazar el header `X-Usuario-Id` por el id que salga del token verificado en todos los
  endpoints que hoy lo usan.
- Revisar en simultáneo: quién puede crear un pedido "en nombre de" otro vendedor, qué puede
  editar cada rol (ver "Roles nuevos y permisos" más abajo, están relacionados).
- Reestablecer contraseña en login o dentro del sistema.

## Reutilización de Colegios existentes

Hoy cada pedido nuevo crea un `Colegio` nuevo, nunca busca uno existente por nombre. Falta:
- Endpoint de búsqueda de colegios (por nombre/localidad) para autocompletar.
- Decidir la regla de "reutilizar vs. crear": ¿por nombre exacto? ¿el usuario elige de una
  lista? ¿se permite editar los datos del colegio existente desde acá?

## Representante de curso: sin buscador ni login real

Falta:
- Buscador/autocompletado de representantes existentes (similar al de colegios).
- Definir si el representante de curso alguna vez necesita loguearse de verdad (ej. para ver
  el estado de su pedido), y si es así, un flujo real de invitación con seteo de contraseña.
- Confirmar si además de nombre/teléfono/email hacen falta más datos del representante.

## Roles nuevos y permisos por pantalla

CLAUDE.md define roles (Administrador, Operativo, Cortador, Ventas) pero falta definir en
detalle qué puede hacer cada uno:
- Rol **Gerente** (a crear) que pueda crear un pedido "en nombre de" otro vendedor, en vez de
  que el campo Vendedor quede siempre fijo al usuario logueado.
- Qué rol(es) pueden editar el Estado de un pedido ya creado, y si hay transiciones
  restringidas (hoy el campo Estado es 100% libre, sin reglas de transición).
- Módulo completo de "Carga Descentralizada": el link público sin login para que
  alumnos/clientes carguen sus medidas y apodos (mencionado en CLAUDE.md, no empezado).

## Listado de Pedidos: agrupación de estados a confirmar con el negocio

`BUCKET_POR_ESTADO` en `frontend/src/types/pedido.ts` agrupa los 9 `EstadoPedido` en 3
categorías (Pendiente / En producción / Entregado) — es una interpretación propia, no algo
que el negocio confirmó. En particular, dudé si "Terminado" debería contar como "en
producción" (ya confeccionado, pero no entregado) o como una cuarta categoría propia.

También: la sección "Colegios" del listado en realidad cuenta **pedidos**, no colegios
distintos (un colegio con 3 pedidos suma 3, no 1) — así lo aclaró el pedido original, pero el
rótulo puede confundir a futuro.

## Listado de Pedidos: filtros y datos 100% del lado del cliente

`GET /api/pedidos` trae todos los pedidos sin paginar ni filtrar en el servidor. Funciona
bien con el volumen actual, pero no va a escalar — cuando haya muchos pedidos, mover el
filtrado/paginado al backend (`PedidoRepository` con `Specification`/query params).

## Productos viejos sin `tipo_prenda` indexado

Los productos con id 1, 2, 3 y 4 (pedidos 2 y 3, migración de 2026-08-19) quedaron con
`id_tipo_prenda = NULL` porque su texto libre estaba en plural (`"Buzos"`, etc.) y no se
adivinó la correspondencia. Falta revisarlos a mano y asignarles el tipo correcto.

## Nº de ficha: confirmar formato con ventas

Se validó como `AAAA-NN` (ej: `2026-01`) por ser lo que se pidió, pero no está confirmado si
ese es el formato real que va a usar el equipo de ventas en producción.

## `Colegio.provincia` sin validación

Es un input de texto libre, sin lista cerrada de provincias — podría reemplazarse por un
`<select>`.

## Alta de usuarios corporativos: contraseña fija "123"

Falta:
- Mail al usuario recién creado con un link para que setee su propia contraseña.
- Invalidar/expirar ese link después de un tiempo o de un solo uso.
- Forzar el cambio de contraseña en el primer login mientras no exista el mail (hoy no hay
  ninguna restricción).
- Pantalla de "olvidé mi contraseña" para cuando ya no sea la primera vez.

## Usuarios deshabilitados: sin pantalla de reactivación

Falta:
- Pantalla o filtro para ver los usuarios deshabilitados y poder reactivarlos (hoy no hay
  forma de deshacer un "eliminar" desde la UI, solo a mano en la base).
- Confirmar si un `ROLE_ADMINISTRATIVO` debería poder editarse/eliminarse a sí mismo (hoy
  puede, sin restricción).
- Evaluar si hace falta poder resetear la contraseña de un usuario desde esta pantalla.

## Patrones de Corte / Molderías: sin edición ni baja

Solo hay alta y listado/detalle — el campo `activo` existe en el modelo para desactivar sin
borrar, pero nada en la API ni en el front lo usa todavía. Tampoco la posibilidad de editar existentes. Definir si se deberia poder o no.

Otros pendientes de este módulo:
- Decidir si `PatronCorte`/`PatronCorteColor` se relacionan en algún momento con
  `Molderia`/`PiezaMolderia` del diagrama de clases de CLAUDE.md (hoy son entidades
  totalmente separadas) y, si corresponde, reflejarlo en el diagrama.
- Imágenes en disco local sin backup ni límite de espacio — revisar antes de producción o
  migrar a un storage tipo S3.
- `cantidadColores` está limitado a 1-5 (front y back) aunque el pedido original mencionaba
  "1 a 3, ampliable a futuro" — ajustar `MAX_COLORES` (front) y el `@Size` (back) si el
  negocio confirma que debe ser otro número.
- Definir cómo va a ser a futuro la pestaña de Ficha Técnica: ¿carga de fichas técnicas de
  colegios tipo drive, con un botón para cargar/editar molderías ahí mismo? (se configura una
  vez y no se toca generalmente).
- Cuando exista el Planificador de Compras (M3), que necesita leer `PatronCorte`/
  `PatronCorteColor` para calcular gramos de tela por color, los `GET` de
  `/api/patrones-corte` van a tener que dejar de ser exclusivos de `ROLE_ADMINISTRATIVO` (o
  ese módulo va a necesitar su propio rol/permiso).

## Ficha Técnica: el modal de gotero no prellena al editar

`ModalColoresGotero` no prellena las posiciones ya asignadas cuando se abre para editar una
ficha existente — hay que volver a marcarlas todas por gotero desde cero (se interpretó así
la consigna original, literal). Si en algún momento se quiere poder corregir una sola
posición puntual sin rehacer todo el click a click, es un cambio de UX aparte.

## Estado de producción por prenda: sin historial propio

A diferencia del estado del pedido (que sí tiene `HistorialEstadoPedido`, con
quién/cuándo/observaciones), el cambio de estado de una prenda no queda registrado en
ningún lado más que el valor actual. Si el negocio necesita esa trazabilidad también a nivel
prenda (CLAUDE.md la pide para pedidos en general), habría que agregar una tabla análoga a
`historial_estado_pedido` pero por producto.
