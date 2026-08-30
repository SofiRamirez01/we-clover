# Pendientes

Lista de trabajo pendiente real: lo que quedó afuera a propósito, decisiones sin confirmar
con el negocio, y riesgos/bugs conocidos sin corregir. Se mantiene corta a propósito — el
detalle de lo ya construido (el "por qué" de cada decisión ya tomada) vive en
[tareas-realizadas.md](tareas-realizadas.md). Cuando algo de acá se resuelva, mover el
detalle a ese archivo y borrarlo de este.

Última actualización: 2026-08-29.

## Catálogo `TipoTela`: sin pantalla de administración

Solo hay lectura (`GET /api/tipos-tela`) — falta una pantalla de alta/edición del catálogo
(agregar un tipo nuevo, ej. "Corderito", sin tocar código; editar
`esPorPeso`/`telaCuerpo`/`gramosSugerido`/`activo`). También falta mostrar `nombre`
(humano-legible, ej. "Piqué") en vez de `codigo` ("PIQUE") en la UI de Tela del modal de
gotero, una vez que el front deje de depender del string crudo para comparaciones.

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

"Precio Unitario" en la tabla es un promedio (`precioTotal / cantidad total de unidades`),
no un precio real de un artículo puntual — revisar si esa aproximación alcanza.

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
