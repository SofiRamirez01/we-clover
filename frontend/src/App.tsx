import { useState } from 'react';
import Sidebar from './components/Sidebar';
import type { AppView } from './components/Sidebar';
import NuevoPedidoView from './components/NuevoPedidoView';
import UsuariosView from './components/UsuariosView';
import LoginView from './components/LoginView';
import { AuthProvider, useAuth } from './context/AuthContext';
import './App.css';

function AppShell() {
  const { usuario } = useAuth();
  const [view, setView] = useState<AppView>('pedidos');

  if (!usuario) {
    return <LoginView />;
  }

  return (
    <div className="app-layout">
      <Sidebar activeView={view} onNavigate={setView} />
      <main className="app-content">
        {view === 'pedidos' ? <NuevoPedidoView /> : <UsuariosView />}
      </main>
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
