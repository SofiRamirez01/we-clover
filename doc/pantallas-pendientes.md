# Pendientes de las pantallas de Login / Nuevo Pedido

Lista de lo que quedó afuera a propósito al construir el login y la carga de pedidos
(módulo M1 - Seguridad y CRM), para retomar más adelante. Última actualización: 2026-08-19.

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

Cuando se implemente esto, revisar también los puntos 3 y 4 de abajo, porque cambian
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

## 5. Otros detalles menores que quedaron sueltos

- `Producto.imagenDisenoUrl` existe en el modelo pero la pantalla no tiene forma de
  subir/adjuntar una imagen todavía (solo se podría mandar una URL a mano).
- El formato de Nº de ficha se validó como `AAAA-NN` (ej: `2026-01`) por ser lo que se
  pidió, pero no está confirmado si ese es el formato real que va a usar el equipo de
  ventas en producción.
- `Colegio.provincia` no tiene ninguna validación de formato ni una lista cerrada de
  provincias (es un input de texto libre); se podría reemplazar por un `<select>`.
- No hay forma de ver/listar/editar pedidos ya creados desde el frontend todavía —
  esta pantalla solo cubre el alta.
