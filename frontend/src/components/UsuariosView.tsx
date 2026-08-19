import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import './UsuariosView.css';
import AppHeader from './AppHeader';
import { useAuth } from '../context/AuthContext';
import {
  actualizarUsuario,
  crearUsuario,
  eliminarUsuario,
  listarRolesCorporativos,
  listarUsuariosCorporativos,
} from '../services/usuarioService';
import { extraerMensajeError } from '../utils/errores';
import type { RolOption, UsuarioResponse } from '../types/usuario';

const FORMATO_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ROL_ADMINISTRATIVO = 'ROLE_ADMINISTRATIVO';

const EditIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
  </svg>
);

const TrashIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 7h16" />
    <path d="M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7" />
    <path d="M6 7l1 13a1.5 1.5 0 0 0 1.5 1.4h7A1.5 1.5 0 0 0 17 20l1-13" />
    <path d="M10 11v6M14 11v6" />
  </svg>
);

const CheckIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 6 9 17l-5-5" />
  </svg>
);

const CloseIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 6 6 18M6 6l12 12" />
  </svg>
);

interface FormularioUsuario {
  nombre: string;
  email: string;
  telefono: string;
  idRol: string;
}

const formularioInicial: FormularioUsuario = {
  nombre: '',
  email: '',
  telefono: '',
  idRol: '',
};

type EnvioEstado = 'idle' | 'enviando' | 'exito' | 'error';

export default function UsuariosView() {
  const { usuario: usuarioLogueado } = useAuth();
  const puedeGestionar = usuarioLogueado?.rol === ROL_ADMINISTRATIVO;

  const [roles, setRoles] = useState<RolOption[]>([]);
  const [rolesEstado, setRolesEstado] = useState<'cargando' | 'listo' | 'error'>('cargando');

  const [usuarios, setUsuarios] = useState<UsuarioResponse[]>([]);
  const [usuariosEstado, setUsuariosEstado] = useState<'cargando' | 'listo' | 'error'>('cargando');

  const [form, setForm] = useState<FormularioUsuario>(formularioInicial);
  const [estado, setEstado] = useState<EnvioEstado>('idle');
  const [mensaje, setMensaje] = useState<string | null>(null);

  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [edicion, setEdicion] = useState<FormularioUsuario>(formularioInicial);
  const [guardandoEdicion, setGuardandoEdicion] = useState(false);

  const cargarUsuarios = useCallback(() => {
    listarUsuariosCorporativos()
      .then((data) => {
        setUsuarios(data);
        setUsuariosEstado('listo');
      })
      .catch(() => setUsuariosEstado('error'));
  }, []);

  useEffect(() => {
    let cancelado = false;
    listarRolesCorporativos()
      .then((data) => {
        if (cancelado) return;
        setRoles(data);
        setRolesEstado('listo');
        setForm((prev) => (prev.idRol ? prev : { ...prev, idRol: data[0] ? String(data[0].id) : '' }));
      })
      .catch(() => {
        if (!cancelado) setRolesEstado('error');
      });
    return () => {
      cancelado = true;
    };
  }, []);

  useEffect(() => {
    cargarUsuarios();
  }, [cargarUsuarios]);

  function actualizar<K extends keyof FormularioUsuario>(campo: K, valor: FormularioUsuario[K]) {
    setForm((prev) => ({ ...prev, [campo]: valor }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!FORMATO_EMAIL.test(form.email.trim())) {
      setEstado('error');
      setMensaje('Ingresá un email válido.');
      return;
    }

    if (!form.idRol) {
      setEstado('error');
      setMensaje('Seleccioná un rol para el usuario.');
      return;
    }

    setEstado('enviando');
    setMensaje(null);
    try {
      const usuarioCreado = await crearUsuario({
        nombre: form.nombre.trim(),
        email: form.email.trim(),
        telefono: form.telefono.trim() || undefined,
        idRol: Number(form.idRol),
      });
      setEstado('exito');
      setMensaje(
        `Usuario ${usuarioCreado.nombre} (${usuarioCreado.rolDescripcion ?? usuarioCreado.rol}) ` +
          'creado correctamente. Contraseña provisoria: 123.',
      );
      setForm({ ...formularioInicial, idRol: form.idRol });
      cargarUsuarios();
    } catch (error) {
      setEstado('error');
      setMensaje(extraerMensajeError(error, 'No se pudo crear el usuario. Intentá nuevamente.'));
    }
  }

  function iniciarEdicion(u: UsuarioResponse) {
    setEditandoId(u.id);
    setEdicion({
      nombre: u.nombre,
      email: u.email,
      telefono: u.telefono ?? '',
      idRol: String(u.idRol),
    });
    setMensaje(null);
  }

  function cancelarEdicion() {
    setEditandoId(null);
  }

  async function guardarEdicion(id: number) {
    if (!FORMATO_EMAIL.test(edicion.email.trim())) {
      setEstado('error');
      setMensaje('Ingresá un email válido.');
      return;
    }

    setGuardandoEdicion(true);
    try {
      await actualizarUsuario(id, {
        nombre: edicion.nombre.trim(),
        email: edicion.email.trim(),
        telefono: edicion.telefono.trim() || undefined,
        idRol: Number(edicion.idRol),
      });
      setEditandoId(null);
      setEstado('exito');
      setMensaje('Usuario actualizado correctamente.');
      cargarUsuarios();
    } catch (error) {
      setEstado('error');
      setMensaje(extraerMensajeError(error, 'No se pudo actualizar el usuario.'));
    } finally {
      setGuardandoEdicion(false);
    }
  }

  async function handleEliminar(u: UsuarioResponse) {
    if (!window.confirm(`¿Eliminar al usuario ${u.nombre}? Ya no va a poder iniciar sesión.`)) {
      return;
    }
    try {
      await eliminarUsuario(u.id);
      setEstado('exito');
      setMensaje(`Usuario ${u.nombre} eliminado correctamente.`);
      cargarUsuarios();
    } catch (error) {
      setEstado('error');
      setMensaje(extraerMensajeError(error, 'No se pudo eliminar el usuario.'));
    }
  }

  return (
    <div className="usuarios-view">
      <AppHeader title="Usuarios Corporativos" />

      {mensaje && (
        <div className={`alerta alerta--${estado === 'error' ? 'error' : 'exito'}`} role="status">
          {mensaje}
        </div>
      )}

      <form onSubmit={handleSubmit} className="card usuarios-card">
        <h2>Nuevo Usuario</h2>
        <div className="grid grid-4">
          <label>
            Nombre
            <input
              value={form.nombre}
              onChange={(e) => actualizar('nombre', e.target.value)}
              placeholder="Nombre y apellido"
              required
            />
          </label>
          <label>
            Email
            <input
              type="text"
              value={form.email}
              onChange={(e) => actualizar('email', e.target.value)}
              placeholder="usuario@weclover.com"
              required
            />
          </label>
          <label>
            Teléfono
            <input
              value={form.telefono}
              onChange={(e) => actualizar('telefono', e.target.value)}
            />
          </label>
          <label>
            Rol
            {rolesEstado === 'error' ? (
              <select disabled>
                <option>No se pudieron cargar los roles</option>
              </select>
            ) : (
              <select
                value={form.idRol}
                onChange={(e) => actualizar('idRol', e.target.value)}
                disabled={rolesEstado === 'cargando'}
                required
              >
                {rolesEstado === 'cargando' && <option value="">Cargando roles…</option>}
                {roles.map((rol) => (
                  <option key={rol.id} value={rol.id}>
                    {rol.descripcion ?? rol.nombre}
                  </option>
                ))}
              </select>
            )}
          </label>
        </div>

        <p className="usuarios-nota">
          El usuario se crea habilitado con una contraseña provisoria (<code>123</code>) que deberá
          cambiar en su primer ingreso.
        </p>

        <button
          type="submit"
          className="btn-guardar"
          disabled={estado === 'enviando' || rolesEstado !== 'listo'}
        >
          {estado === 'enviando' ? 'Creando…' : '✓ Crear Usuario'}
        </button>
      </form>

      <section className="card">
        <h2>Usuarios Existentes</h2>
        <div className="usuarios-tabla-wrapper">
          <table className="usuarios-tabla">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Email</th>
                <th>Teléfono</th>
                <th>Rol</th>
                {puedeGestionar && <th aria-label="Acciones" />}
              </tr>
            </thead>
            <tbody>
              {usuariosEstado === 'listo' &&
                usuarios.map((u) =>
                  editandoId === u.id ? (
                    <tr key={u.id}>
                      <td>
                        <input
                          value={edicion.nombre}
                          onChange={(e) => setEdicion((prev) => ({ ...prev, nombre: e.target.value }))}
                        />
                      </td>
                      <td>
                        <input
                          type="text"
                          value={edicion.email}
                          onChange={(e) => setEdicion((prev) => ({ ...prev, email: e.target.value }))}
                        />
                      </td>
                      <td>
                        <input
                          value={edicion.telefono}
                          onChange={(e) => setEdicion((prev) => ({ ...prev, telefono: e.target.value }))}
                        />
                      </td>
                      <td>
                        <select
                          value={edicion.idRol}
                          onChange={(e) => setEdicion((prev) => ({ ...prev, idRol: e.target.value }))}
                        >
                          {roles.map((rol) => (
                            <option key={rol.id} value={rol.id}>
                              {rol.descripcion ?? rol.nombre}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="usuarios-acciones">
                        <button
                          type="button"
                          className="btn-icono"
                          title="Guardar"
                          disabled={guardandoEdicion}
                          onClick={() => guardarEdicion(u.id)}
                        >
                          <CheckIcon />
                        </button>
                        <button
                          type="button"
                          className="btn-icono"
                          title="Cancelar"
                          disabled={guardandoEdicion}
                          onClick={cancelarEdicion}
                        >
                          <CloseIcon />
                        </button>
                      </td>
                    </tr>
                  ) : (
                    <tr key={u.id}>
                      <td>{u.nombre}</td>
                      <td>{u.email}</td>
                      <td>{u.telefono ?? '—'}</td>
                      <td>{u.rolDescripcion ?? u.rol}</td>
                      {puedeGestionar && (
                        <td className="usuarios-acciones">
                          <button
                            type="button"
                            className="btn-icono"
                            title="Editar"
                            onClick={() => iniciarEdicion(u)}
                          >
                            <EditIcon />
                          </button>
                          <button
                            type="button"
                            className="btn-icono btn-icono--peligro"
                            title="Eliminar"
                            onClick={() => handleEliminar(u)}
                          >
                            <TrashIcon />
                          </button>
                        </td>
                      )}
                    </tr>
                  ),
                )}
            </tbody>
          </table>
          {usuariosEstado === 'cargando' && (
            <p className="usuarios-tabla-vacio">Cargando usuarios…</p>
          )}
          {usuariosEstado === 'listo' && usuarios.length === 0 && (
            <p className="usuarios-tabla-vacio">Todavía no hay usuarios corporativos cargados.</p>
          )}
          {usuariosEstado === 'error' && (
            <p className="usuarios-tabla-vacio">No se pudo cargar el listado de usuarios.</p>
          )}
        </div>
      </section>
    </div>
  );
}
