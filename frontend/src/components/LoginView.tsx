import { useState } from 'react';
import type { FormEvent } from 'react';
import CloverIcon from './CloverIcon';
import { useAuth } from '../context/AuthContext';
import './LoginView.css';

export default function LoginView() {
  const { login, cargando, error } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      await login(email, password);
    } catch {
      // el mensaje de error ya queda expuesto por useAuth().error
    }
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-brand">
          <CloverIcon className="login-brand-icon" width={48} height={48} />
          <h1>WE CLOVER</h1>
          <p>Sistema de Gestión y Optimización de Recursos</p>
        </div>

        {error && (
          <div className="alerta alerta--error" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="login-form">
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="usuario@weclover.com"
              autoComplete="username"
              required
            />
          </label>
          <label>
            Contraseña
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
              required
            />
          </label>

          <button type="submit" className="btn-guardar" disabled={cargando}>
            {cargando ? 'Ingresando…' : 'Ingresar'}
          </button>
        </form>
      </div>
    </div>
  );
}
