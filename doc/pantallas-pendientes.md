# Pendientes de las pantallas de Login / Pedidos / Usuarios

Lista de lo que quedó afuera a propósito al construir el login, el listado y la carga de
pedidos, y el alta de usuarios corporativos (módulo M1 - Seguridad y CRM), para retomar
más adelante. Última actualización: 2026-08-21.

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
