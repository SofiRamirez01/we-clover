import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import './NuevoPedidoView.css';
import { crearPedido } from '../services/pedidoService';
import { useAuth } from '../context/AuthContext';
import { extraerMensajeError } from '../utils/errores';
import type { EstadoPedido, PedidoCreateRequest } from '../types/pedido';

const FORMATO_NUMERO_FICHA = /^\d{4}-\d{2}$/;

const ESTADOS_INICIALES: { value: EstadoPedido; label: string }[] = [
  { value: 'PRESUPUESTADO', label: 'Presupuestado' },
  { value: 'SENADO', label: 'Señado' },
];

interface PrendaRow {
  id: string;
  articulo: string;
  cantidad: string;
  precioUnitario: string;
}

interface ClienteForm {
  colegioNombre: string;
  localidad: string;
  provincia: string;
  nombreContacto: string;
  telefono: string;
  mail: string;
  cantAlumnos: string;
  curso: string;
}

interface InfoGeneralForm {
  numeroFicha: string;
  fechaPedido: string;
  estado: EstadoPedido;
}

type EnvioEstado = 'idle' | 'enviando' | 'exito' | 'error';

const clienteInicial: ClienteForm = {
  colegioNombre: '',
  localidad: '',
  provincia: '',
  nombreContacto: '',
  telefono: '',
  mail: '',
  cantAlumnos: '',
  curso: '',
};

const infoGeneralInicial: InfoGeneralForm = {
  numeroFicha: '',
  fechaPedido: new Date().toISOString().slice(0, 10),
  estado: 'PRESUPUESTADO',
};

function nuevaPrenda(): PrendaRow {
  return { id: crypto.randomUUID(), articulo: '', cantidad: '', precioUnitario: '' };
}

function aNumero(valor: string): number {
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : 0;
}

function formatearMoneda(valor: number): string {
  return valor.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function iniciales(nombre: string | undefined): string {
  if (!nombre) return '';
  return nombre
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase())
    .join('');
}

export default function NuevoPedidoView() {
  const { usuario, logout } = useAuth();

  const [cliente, setCliente] = useState<ClienteForm>(clienteInicial);
  const [infoGeneral, setInfoGeneral] = useState<InfoGeneralForm>(infoGeneralInicial);
  const [prendas, setPrendas] = useState<PrendaRow[]>([nuevaPrenda()]);
  const [pagoInicial, setPagoInicial] = useState('');
  const [estado, setEstado] = useState<EnvioEstado>('idle');
  const [mensaje, setMensaje] = useState<string | null>(null);

  const precioTotal = useMemo(
    () => prendas.reduce((acc, p) => acc + aNumero(p.cantidad) * aNumero(p.precioUnitario), 0),
    [prendas],
  );
  const saldo = precioTotal - aNumero(pagoInicial);
  const porcentajePagado = precioTotal > 0 ? (aNumero(pagoInicial) / precioTotal) * 100 : 0;

  function actualizarCliente<K extends keyof ClienteForm>(campo: K, valor: ClienteForm[K]) {
    setCliente((prev) => ({ ...prev, [campo]: valor }));
  }

  function actualizarInfoGeneral<K extends keyof InfoGeneralForm>(campo: K, valor: InfoGeneralForm[K]) {
    setInfoGeneral((prev) => ({ ...prev, [campo]: valor }));
  }

  function actualizarPrenda(id: string, campo: keyof Omit<PrendaRow, 'id'>, valor: string) {
    setPrendas((prev) => prev.map((p) => (p.id === id ? { ...p, [campo]: valor } : p)));
  }

  function agregarPrenda() {
    setPrendas((prev) => [...prev, nuevaPrenda()]);
  }

  function quitarPrenda(id: string) {
    setPrendas((prev) => (prev.length > 1 ? prev.filter((p) => p.id !== id) : prev));
  }

  function limpiarFormulario() {
    setCliente(clienteInicial);
    setInfoGeneral({ ...infoGeneralInicial, fechaPedido: new Date().toISOString().slice(0, 10) });
    setPrendas([nuevaPrenda()]);
    setPagoInicial('');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!usuario) {
      return;
    }

    const numeroFicha = infoGeneral.numeroFicha.trim();
    if (!FORMATO_NUMERO_FICHA.test(numeroFicha)) {
      setEstado('error');
      setMensaje('El Nº de ficha debe tener el formato AAAA-NN (ej: 2026-01).');
      return;
    }

    const productosValidos = prendas.filter(
      (p) => p.articulo.trim() !== '' && aNumero(p.cantidad) > 0 && aNumero(p.precioUnitario) > 0,
    );
    if (productosValidos.length === 0) {
      setEstado('error');
      setMensaje('Cargá al menos una prenda con artículo, cantidad y precio unitario.');
      return;
    }

    const payload: PedidoCreateRequest = {
      colegioNombre: cliente.colegioNombre.trim(),
      colegioLocalidad: cliente.localidad.trim() || undefined,
      colegioProvincia: cliente.provincia.trim() || undefined,
      representanteNombre: cliente.nombreContacto.trim(),
      representanteTelefono: cliente.telefono.trim() || undefined,
      representanteEmail: cliente.mail.trim(),
      idVendedor: usuario.id,
      codigoInterno: numeroFicha,
      curso: cliente.curso.trim(),
      cantAlumnos: aNumero(cliente.cantAlumnos),
      estado: infoGeneral.estado,
      productos: productosValidos.map((p) => ({
        tipoPrenda: p.articulo.trim(),
        cantidadTotal: aNumero(p.cantidad),
        costo: aNumero(p.precioUnitario),
      })),
      pagoInicial: aNumero(pagoInicial),
    };

    setEstado('enviando');
    setMensaje(null);
    try {
      const pedidoCreado = await crearPedido(payload);
      setEstado('exito');
      setMensaje(
        `Pedido ${pedidoCreado.codigoInterno} creado correctamente (estado: ${pedidoCreado.estadoActual}, ` +
          `total: $${formatearMoneda(pedidoCreado.precioTotal)}).`,
      );
      limpiarFormulario();
    } catch (error) {
      setEstado('error');
      setMensaje(extraerMensajeError(error, 'No se pudo guardar el pedido. Intentá nuevamente.'));
    }
  }

  return (
    <div className="nuevo-pedido">
      <header className="nuevo-pedido-header">
        <h1>Carga de Nuevo Pedido - Egresados 2026</h1>
        <div className="user-chip">
          <span className="user-chip-avatar">{iniciales(usuario?.nombre)}</span>
          <div className="user-chip-info">
            <strong>{usuario?.nombre}</strong>
            <span>{usuario?.rol}</span>
          </div>
          <button type="button" className="btn-logout" onClick={logout}>
            Salir
          </button>
        </div>
      </header>

      {mensaje && (
        <div className={`alerta alerta--${estado === 'error' ? 'error' : 'exito'}`} role="status">
          {mensaje}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <section className="card">
          <h2>Sección 1: Datos del Cliente</h2>
          <div className="grid grid-3">
            <label>
              Colegio
              <input
                value={cliente.colegioNombre}
                onChange={(e) => actualizarCliente('colegioNombre', e.target.value)}
                placeholder="Nombre del colegio"
                required
              />
            </label>
            <label>
              Localidad
              <input
                value={cliente.localidad}
                onChange={(e) => actualizarCliente('localidad', e.target.value)}
                required
              />
            </label>
            <label>
              Provincia
              <input
                value={cliente.provincia}
                onChange={(e) => actualizarCliente('provincia', e.target.value)}
              />
            </label>
            <label>
              Nombre Alumno/Contacto Principal
              <input
                value={cliente.nombreContacto}
                onChange={(e) => actualizarCliente('nombreContacto', e.target.value)}
                required
              />
            </label>
            <label>
              Teléfono
              <input
                value={cliente.telefono}
                onChange={(e) => actualizarCliente('telefono', e.target.value)}
              />
            </label>
            <label>
              Mail
              <input
                type="email"
                value={cliente.mail}
                onChange={(e) => actualizarCliente('mail', e.target.value)}
                required
              />
            </label>
            <label>
              Curso
              <input
                value={cliente.curso}
                onChange={(e) => actualizarCliente('curso', e.target.value)}
                placeholder="Ej: 6to B"
                required
              />
            </label>
            <label>
              Cant. Alumnos
              <input
                type="number"
                min={0}
                value={cliente.cantAlumnos}
                onChange={(e) => actualizarCliente('cantAlumnos', e.target.value)}
                required
              />
            </label>
            <label>
              Vendedor
              <input value={usuario?.email ?? ''} readOnly className="input-readonly" />
            </label>
          </div>
        </section>

        <div className="row-2col">
          <section className="card">
            <h2>Sección 2: Información General del Pedido</h2>
            <div className="grid grid-3">
              <label>
                Nº Ficha
                <input
                  value={infoGeneral.numeroFicha}
                  onChange={(e) => actualizarInfoGeneral('numeroFicha', e.target.value)}
                  placeholder="2026-01"
                  required
                />
              </label>
              <label>
                Fecha Pedido
                <input
                  type="date"
                  value={infoGeneral.fechaPedido}
                  onChange={(e) => actualizarInfoGeneral('fechaPedido', e.target.value)}
                />
              </label>
              <label>
                Estado Pedido
                <select
                  value={infoGeneral.estado}
                  onChange={(e) => actualizarInfoGeneral('estado', e.target.value as EstadoPedido)}
                >
                  {ESTADOS_INICIALES.map(({ value, label }) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <h2 className="section-title-spaced">Sección 3: Desglose de Prendas y Precios</h2>
            <table className="prendas-table">
              <thead>
                <tr>
                  <th>Artículo</th>
                  <th>Cantidad Total</th>
                  <th>Precio Unitario ($)</th>
                  <th>Subtotal ($)</th>
                  <th aria-label="Acciones" />
                </tr>
              </thead>
              <tbody>
                {prendas.map((prenda) => (
                  <tr key={prenda.id}>
                    <td>
                      <input
                        value={prenda.articulo}
                        onChange={(e) => actualizarPrenda(prenda.id, 'articulo', e.target.value)}
                        placeholder="Ej: Buzos"
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        value={prenda.cantidad}
                        onChange={(e) => actualizarPrenda(prenda.id, 'cantidad', e.target.value)}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={prenda.precioUnitario}
                        onChange={(e) => actualizarPrenda(prenda.id, 'precioUnitario', e.target.value)}
                      />
                    </td>
                    <td className="subtotal-cell">
                      {formatearMoneda(aNumero(prenda.cantidad) * aNumero(prenda.precioUnitario))}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn-remove-row"
                        onClick={() => quitarPrenda(prenda.id)}
                        disabled={prendas.length === 1}
                        aria-label="Quitar prenda"
                      >
                        ×
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <button type="button" className="btn-secondary" onClick={agregarPrenda}>
              + Agregar Prenda
            </button>
          </section>

          <section className="card resumen-financiero">
            <h2>Sección 4: Resumen Financiero</h2>
            <label>
              Precio Total
              <input value={`$${formatearMoneda(precioTotal)}`} readOnly className="input-readonly" />
            </label>
            <label>
              Pago Inicial ($)
              <input
                type="number"
                min={0}
                step="0.01"
                value={pagoInicial}
                onChange={(e) => setPagoInicial(e.target.value)}
              />
            </label>
            <label>
              Saldo ($)
              <input value={`$${formatearMoneda(saldo)}`} readOnly className="input-readonly" />
            </label>
            <label>
              Porcentaje Pagado
              <input
                value={`${porcentajePagado.toFixed(0)}%`}
                readOnly
                className="input-readonly"
              />
            </label>

            <button type="submit" className="btn-guardar" disabled={estado === 'enviando'}>
              {estado === 'enviando' ? 'Guardando…' : '✓ Guardar Pedido'}
            </button>
          </section>
        </div>
      </form>
    </div>
  );
}
