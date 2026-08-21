import { useState } from 'react';
import Sidebar from './components/Sidebar';
import type { AppView } from './components/Sidebar';
import NuevoPedidoView from './components/NuevoPedidoView';
import PedidosListView from './components/PedidosListView';
import UsuariosView from './components/UsuariosView';
import LoginView from './components/LoginView';
import { AuthProvider, useAuth } from './context/AuthContext';
import type { PedidoResponse } from './types/pedido';
import './App.css';

function AppShell() {
  const { usuario } = useAuth();
  const [view, setView] = useState<AppView>('pedidos');
  const [mensajePedidos, setMensajePedidos] = useState<string | null>(null);
  const [pedidoAEditar, setPedidoAEditar] = useState<PedidoResponse | null>(null);

  if (!usuario) {
    return <LoginView />;
  }

  function irANuevoPedido() {
    setMensajePedidos(null);
    setPedidoAEditar(null);
    setView('pedidos-nuevo');
  }

  function irAEditarPedido(pedido: PedidoResponse) {
    setMensajePedidos(null);
    setPedidoAEditar(pedido);
    setView('pedidos-nuevo');
  }

  function volverAPedidos(mensaje: string) {
    setMensajePedidos(mensaje);
    setPedidoAEditar(null);
    setView('pedidos');
  }

  function cancelarNuevoPedido() {
    setMensajePedidos(null);
    setPedidoAEditar(null);
    setView('pedidos');
  }

  let contenido;
  if (view === 'pedidos') {
    contenido = (
      <PedidosListView
        onNuevoPedido={irANuevoPedido}
        onEditarPedido={irAEditarPedido}
        mensajeExito={mensajePedidos}
      />
    );
  } else if (view === 'pedidos-nuevo') {
    contenido = (
      <NuevoPedidoView onCreado={volverAPedidos} onVolver={cancelarNuevoPedido} pedidoAEditar={pedidoAEditar} />
    );
  } else {
    contenido = <UsuariosView />;
  }

  return (
    <div className="app-layout">
      <Sidebar
        activeView={view}
        onNavigate={(v) => {
          setMensajePedidos(null);
          setView(v);
        }}
      />
      <main className="app-content">{contenido}</main>
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}

export default App;
