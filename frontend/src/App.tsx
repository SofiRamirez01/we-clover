import Sidebar from './components/Sidebar';
import NuevoPedidoView from './components/NuevoPedidoView';
import LoginView from './components/LoginView';
import { AuthProvider, useAuth } from './context/AuthContext';
import './App.css';

function AppShell() {
  const { usuario } = useAuth();

  if (!usuario) {
    return <LoginView />;
  }

  return (
    <div className="app-layout">
      <Sidebar />
      <main className="app-content">
        <NuevoPedidoView />
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
