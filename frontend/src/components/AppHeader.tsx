import { useAuth } from '../context/AuthContext';
import './AppHeader.css';

function iniciales(nombre: string | undefined): string {
  if (!nombre) return '';
  return nombre
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase())
    .join('');
}

interface AppHeaderProps {
  title: string;
  onBack?: () => void;
}

export default function AppHeader({ title, onBack }: AppHeaderProps) {
  const { usuario, logout } = useAuth();

  return (
    <header className="app-header">
      <div className="app-header-title">
        {onBack && (
          <button type="button" className="btn-volver" onClick={onBack} aria-label="Volver">
            ←
          </button>
        )}
        <h1>{title}</h1>
      </div>
      <div className="user-chip">
        <span className="user-chip-avatar">{iniciales(usuario?.nombre)}</span>
        <div className="user-chip-info">
          <strong>{usuario?.nombre}</strong>
          <span>{usuario?.rolDescripcion ?? usuario?.rol}</span>
        </div>
        <button type="button" className="btn-logout" onClick={logout}>
          Salir
        </button>
      </div>
    </header>
  );
}
