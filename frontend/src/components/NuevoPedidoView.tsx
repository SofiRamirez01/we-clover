import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import './NuevoPedidoView.css';
import AppHeader from './AppHeader';
import { actualizarPedido, crearPedido, listarTiposPrenda } from '../services/pedidoService';
import { useAuth } from '../context/AuthContext';
import { extraerMensajeError } from '../utils/errores';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../types/pedido';
import type {
  EstadoPedido,
  PedidoCreateRequest,
  PedidoResponse,
  PedidoUpdateRequest,
  TipoPrendaOption,
} from '../types/pedido';

const FORMATO_NUMERO_FICHA = /^\d{4}-\d{2}$/;

const ESTADOS_INICIALES: { value: EstadoPedido; label: string }[] = [
  { value: 'PRESUPUESTADO', label: 'Presupuestado' },
  { value: 'SENADO', label: 'Señado' },
];

interface PrendaRow {
  id: string;
  idTipoPrenda: string;
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
  fechaEstimadaEntrega: string;
  estado: EstadoPedido;
  observaciones: string;
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
  fechaEstimadaEntrega: '',
  estado: 'PRESUPUESTADO',
  observaciones: '',
};

function nuevaPrenda(): PrendaRow {
  return { id: crypto.randomUUID(), idTipoPrenda: '', cantidad: '', precioUnitario: '' };
}

function aNumero(valor: string): number {
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : 0;
}

function formatearMoneda(valor: number): string {
  return valor.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function clienteDesdePedido(pedido: PedidoResponse): ClienteForm {
  return {
    colegioNombre: pedido.nombreColegio,
    localidad: pedido.localidadColegio ?? '',
    provincia: pedido.provinciaColegio ?? '',
    nombreContacto: pedido.nombreRepresentanteCurso,
    telefono: pedido.telefonoRepresentanteCurso ?? '',
    mail: pedido.emailRepresentanteCurso,
    cantAlumnos: String(pedido.cantAlumnos),
    curso: pedido.curso,
  };
}

function infoGeneralDesdePedido(pedido: PedidoResponse): InfoGeneralForm {
  return {
    numeroFicha: pedido.codigoInterno,
    fechaPedido: pedido.fechaVenta,
    fechaEstimadaEntrega: pedido.fechaEstimadaEntrega,
    estado: pedido.estadoActual,
    observaciones: pedido.observaciones ?? '',
  };
}

function prendasDesdePedido(pedido: PedidoResponse): PrendaRow[] {
  if (pedido.productos.length === 0) return [nuevaPrenda()];
  return pedido.productos.map((p) => ({
    id: crypto.randomUUID(),
    idTipoPrenda: p.idTipoPrenda != null ? String(p.idTipoPrenda) : '',
    cantidad: String(p.cantidadTotal),
    precioUnitario: String(p.costo),
  }));
}

interface NuevoPedidoViewProps {
  onCreado: (mensaje: string) => void;
  onVolver: () => void;
  pedidoAEditar?: PedidoResponse | null;
}

export default function NuevoPedidoView({ onCreado, onVolver, pedidoAEditar }: NuevoPedidoViewProps) {
  const { usuario } = useAuth();
  const esEdicion = pedidoAEditar != null;

  const [cliente, setCliente] = useState<ClienteForm>(
    () => (pedidoAEditar ? clienteDesdePedido(pedidoAEditar) : clienteInicial),
  );
  const [infoGeneral, setInfoGeneral] = useState<InfoGeneralForm>(
    () => (pedidoAEditar ? infoGeneralDesdePedido(pedidoAEditar) : infoGeneralInicial),
  );
  const [prendas, setPrendas] = useState<PrendaRow[]>(
    () => (pedidoAEditar ? prendasDesdePedido(pedidoAEditar) : [nuevaPrenda()]),
  );
  const [pagoInicial, setPagoInicial] = useState(
    () => (pedidoAEditar ? String(pedidoAEditar.pagoInicial) : ''),
  );
  const [estado, setEstado] = useState<EnvioEstado>('idle');
  const [mensaje, setMensaje] = useState<string | null>(null);

  const [tiposPrenda, setTiposPrenda] = useState<TipoPrendaOption[]>([]);
  const [tiposPrendaEstado, setTiposPrendaEstado] = useState<'cargando' | 'listo' | 'error'>('cargando');

  useEffect(() => {
    let cancelado = false;
    listarTiposPrenda()
      .then((data) => {
        if (cancelado) return;
        setTiposPrenda(data);
        setTiposPrendaEstado('listo');
      })
      .catch(() => {
        if (!cancelado) setTiposPrendaEstado('error');
      });
    return () => {
      cancelado = true;
    };
  }, []);

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

    if (!infoGeneral.fechaEstimadaEntrega) {
      setEstado('error');
      setMensaje('Ingresá la fecha estimada de entrega.');
      return;
    }
    if (infoGeneral.fechaEstimadaEntrega < infoGeneral.fechaPedido) {
      setEstado('error');
      setMensaje('La fecha estimada de entrega no puede ser anterior a la fecha del pedido.');
      return;
    }

    const productosValidos = prendas.filter(
      (p) => p.idTipoPrenda !== '' && aNumero(p.cantidad) > 0 && aNumero(p.precioUnitario) > 0,
    );
    if (productosValidos.length === 0) {
      setEstado('error');
      setMensaje('Cargá al menos una prenda con artículo, cantidad y precio unitario.');
      return;
    }

    const productos = productosValidos.map((p) => ({
      idTipoPrenda: Number(p.idTipoPrenda),
      cantidadTotal: aNumero(p.cantidad),
      costo: aNumero(p.precioUnitario),
    }));

    setEstado('enviando');
    setMensaje(null);
    try {
      if (esEdicion && pedidoAEditar) {
        const payload: PedidoUpdateRequest = {
          colegioNombre: cliente.colegioNombre.trim(),
          colegioLocalidad: cliente.localidad.trim() || undefined,
          colegioProvincia: cliente.provincia.trim() || undefined,
          representanteNombre: cliente.nombreContacto.trim(),
          representanteTelefono: cliente.telefono.trim() || undefined,
          representanteEmail: cliente.mail.trim(),
          codigoInterno: numeroFicha,
          curso: cliente.curso.trim(),
          cantAlumnos: aNumero(cliente.cantAlumnos),
          observaciones: infoGeneral.observaciones.trim() || undefined,
          estado: infoGeneral.estado,
          fechaVenta: infoGeneral.fechaPedido,
          fechaEstimadaEntrega: infoGeneral.fechaEstimadaEntrega,
          productos,
          pagoInicial: aNumero(pagoInicial),
        };
        const pedidoActualizado = await actualizarPedido(pedidoAEditar.id, payload);
        onCreado(
          `Pedido ${pedidoActualizado.codigoInterno} actualizado correctamente (estado: ` +
            `${ESTADO_PEDIDO_LABELS[pedidoActualizado.estadoActual]}, total: $${formatearMoneda(pedidoActualizado.precioTotal)}).`,
        );
      } else {
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
          observaciones: infoGeneral.observaciones.trim() || undefined,
          estado: infoGeneral.estado,
          fechaVenta: infoGeneral.fechaPedido,
          fechaEstimadaEntrega: infoGeneral.fechaEstimadaEntrega,
          productos,
          pagoInicial: aNumero(pagoInicial),
        };
        const pedidoCreado = await crearPedido(payload);
        limpiarFormulario();
        onCreado(
          `Pedido ${pedidoCreado.codigoInterno} creado correctamente (estado: ${pedidoCreado.estadoActual}, ` +
            `total: $${formatearMoneda(pedidoCreado.precioTotal)}).`,
        );
      }
    } catch (error) {
      setEstado('error');
      setMensaje(
        extraerMensajeError(
          error,
          esEdicion ? 'No se pudo actualizar el pedido. Intentá nuevamente.' : 'No se pudo guardar el pedido. Intentá nuevamente.',
        ),
      );
    }
  }

  return (
    <div className="nuevo-pedido">
      <AppHeader title={esEdicion ? `Editar Pedido ${pedidoAEditar?.codigoInterno ?? ''}` : 'Carga de Nuevo Pedido'} onBack={onVolver} />

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
              <input
                value={esEdicion ? pedidoAEditar?.emailVendedor ?? '' : usuario?.email ?? ''}
                readOnly
                className="input-readonly"
              />
            </label>
          </div>
        </section>

        <section className="card">
            <h2>Sección 2: Información General del Pedido</h2>
            <div className="grid grid-4">
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
                  required
                />
              </label>
              <label>
                Fecha Estimada de Entrega
                <input
                  type="date"
                  value={infoGeneral.fechaEstimadaEntrega}
                  onChange={(e) => actualizarInfoGeneral('fechaEstimadaEntrega', e.target.value)}
                  required
                />
              </label>
              <label>
                Estado Pedido
                <select
                  value={infoGeneral.estado}
                  onChange={(e) => actualizarInfoGeneral('estado', e.target.value as EstadoPedido)}
                >
                  {esEdicion
                    ? ESTADOS_PEDIDO.map((valor) => (
                        <option key={valor} value={valor}>
                          {ESTADO_PEDIDO_LABELS[valor]}
                        </option>
                      ))
                    : ESTADOS_INICIALES.map(({ value, label }) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                </select>
              </label>
            </div>

            <label className="campo-observaciones">
              Observaciones
              <textarea
                value={infoGeneral.observaciones}
                onChange={(e) => actualizarInfoGeneral('observaciones', e.target.value)}
                placeholder="Notas adicionales sobre el pedido (opcional)"
                rows={3}
              />
            </label>
        </section>

        <section className="card">
            <h2>Sección 3: Desglose de Prendas y Precios</h2>
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
                      {tiposPrendaEstado === 'error' ? (
                        <select disabled>
                          <option>No se pudieron cargar los tipos de prenda</option>
                        </select>
                      ) : (
                        <select
                          value={prenda.idTipoPrenda}
                          onChange={(e) => actualizarPrenda(prenda.id, 'idTipoPrenda', e.target.value)}
                          disabled={tiposPrendaEstado === 'cargando'}
                        >
                          <option value="">
                            {tiposPrendaEstado === 'cargando' ? 'Cargando…' : 'Seleccionar…'}
                          </option>
                          {tiposPrenda.map((tipo) => (
                            <option key={tipo.id} value={tipo.id}>
                              {tipo.nombre}
                            </option>
                          ))}
                        </select>
                      )}
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
            <div className="grid grid-4">
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
            </div>

            <button
              type="submit"
              className="btn-guardar"
              disabled={estado === 'enviando' || tiposPrendaEstado === 'cargando'}
            >
              {estado === 'enviando' ? 'Guardando…' : esEdicion ? '✓ Guardar Cambios' : '✓ Guardar Pedido'}
            </button>
        </section>
      </form>
    </div>
  );
}
