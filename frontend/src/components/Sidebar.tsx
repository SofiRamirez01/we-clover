import { useState } from 'react';
import type { ReactElement, SVGProps } from 'react';
import logoTrebol from '../assets/logo-trebol-menta.svg';
import { useAuth } from '../context/AuthContext';
import './Sidebar.css';

const ROL_ADMINISTRATIVO = 'ROLE_ADMINISTRATIVO';

type IconProps = SVGProps<SVGSVGElement>;

function baseIconProps(props: IconProps): IconProps {
  return {
    width: 20,
    height: 20,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    ...props,
  };
}



const PedidosIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <rect x="5" y="3.5" width="14" height="17" rx="2" />
    <path d="M9 3.5V3a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 3v.5" />
    <path d="M8.5 11.5h7M8.5 15h5" />
  </svg>
);

const FichasIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <path d="M7 2.5h7L18.5 7v13a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 20V4A1.5 1.5 0 0 1 7 2.5Z" />
    <path d="M14 2.5V7h4.5" />
    <path d="M8.5 12.5h7M8.5 16h4.5" />
  </svg>
);

const FichaTecnicaIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <circle cx="8.5" cy="10" r="1.5" />
    <path d="m5 17 4-4 3 3 4-5 3 4" />
  </svg>
);

const ProduccionIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <circle cx="7" cy="6" r="2.3" />
    <circle cx="7" cy="18" r="2.3" />
    <path d="M20 5 8.6 16.4M11.2 12.8 20 21.5M8.9 8.3 12 11.4" />
  </svg>
);

const ComprasIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <path d="M3.5 4h2l1 12.5a2 2 0 0 0 2 1.9h8a2 2 0 0 0 2-1.7L20 8H6.2" />
    <circle cx="9.5" cy="21" r="1.3" />
    <circle cx="16.5" cy="21" r="1.3" />
  </svg>
);

const TizadaIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <path d="M8 3 4 6v4l3-1v9.5A1.5 1.5 0 0 0 8.5 20h7a1.5 1.5 0 0 0 1.5-1.5V9l3 1V6l-4-3a3 3 0 0 1-6 0Z" />
    <g transform="rotate(42 12 15.5)">
      <rect x="6.7" y="14" width="10.6" height="3" rx="0.6" />
      <path d="M9.3 14v1.5M12 14v1.5M14.7 14v1.5" />
    </g>
  </svg>
);

const ReportesIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <path d="M4 20V10M11 20V4M18 20v-7" />
    <path d="M2.5 20h19" />
  </svg>
);

const UsuariosIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <circle cx="9" cy="8" r="3.2" />
    <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
    <path d="M17 8h4M19 6v4" />
  </svg>
);

const ConfigIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z" />
  </svg>
);

const ChevronRightIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)} width={14} height={14}>
    <path d="M9 6l6 6-6 6" />
  </svg>
);

const BackIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <path d="M19 12H5" />
    <path d="M11 6l-6 6 6 6" />
  </svg>
);

export type AppView = 'pedidos' | 'pedidos-nuevo' | 'usuarios' | 'patrones-corte' | 'fichas-tecnicas';

interface NavItem {
  label: string;
  icon: (props: IconProps) => ReactElement;
  view?: AppView;
}

const NAV_ITEMS_RAIZ: NavItem[] = [
  { label: 'Pedidos', icon: PedidosIcon, view: 'pedidos' },
  { label: 'Ficha Técnica', icon: FichaTecnicaIcon, view: 'fichas-tecnicas' },
  { label: 'Producción', icon: ProduccionIcon },
  { label: 'Planificador Compras', icon: ComprasIcon },
  { label: 'Motor Tizada', icon: TizadaIcon },
  { label: 'Reportes', icon: ReportesIcon },
];

const NAV_ITEMS_CONFIG: NavItem[] = [
  { label: 'Molderías', icon: FichasIcon, view: 'patrones-corte' },
  { label: 'Usuarios', icon: UsuariosIcon, view: 'usuarios' },
];

const VISTAS_CONFIG: AppView[] = ['patrones-corte', 'usuarios'];

interface SidebarProps {
  activeView: AppView;
  onNavigate: (view: AppView) => void;
}

export default function Sidebar({ activeView, onNavigate }: SidebarProps) {
  const { usuario } = useAuth();
  const esAdministrativo = usuario?.rol === ROL_ADMINISTRATIVO;

  const [submenuConfigAbierto, setSubmenuConfigAbierto] = useState(
    () => esAdministrativo && VISTAS_CONFIG.includes(activeView),
  );
  const [activeViewAnterior, setActiveViewAnterior] = useState(activeView);

  // Si se navega a una vista del submenú de Config desde otro lugar de la app,
  // el sidebar debe reflejarlo aunque el usuario no lo haya abierto a mano.
  if (activeView !== activeViewAnterior) {
    setActiveViewAnterior(activeView);
    if (esAdministrativo && VISTAS_CONFIG.includes(activeView)) {
      setSubmenuConfigAbierto(true);
    }
  }

  function renderLink({ label, icon: Icon, view }: NavItem, active: boolean) {
    return (
      <a
        key={label}
        href="#"
        className={`sidebar-link${active ? ' sidebar-link--active' : ''}`}
        aria-current={active ? 'page' : undefined}
        aria-disabled={!view}
        onClick={(e) => {
          e.preventDefault();
          if (view) onNavigate(view);
        }}
      >
        <Icon className="sidebar-link-icon" />
        <span>{label}</span>
      </a>
    );
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <img src={logoTrebol} className="sidebar-brand-icon" alt="WE CLOVER" />
      </div>

      <nav className="sidebar-nav">
        {submenuConfigAbierto && esAdministrativo ? (
          <>
            <a
              href="#"
              className="sidebar-link sidebar-link--volver"
              onClick={(e) => {
                e.preventDefault();
                setSubmenuConfigAbierto(false);
              }}
            >
              <BackIcon className="sidebar-link-icon" />
              <span>Config</span>
            </a>
            {NAV_ITEMS_CONFIG.map((item) => renderLink(item, item.view === activeView))}
          </>
        ) : (
          <>
            {NAV_ITEMS_RAIZ.map((item) => {
              const active =
                item.view === activeView || (item.view === 'pedidos' && activeView === 'pedidos-nuevo');
              return renderLink(item, active);
            })}
            {esAdministrativo && (
              <a
                href="#"
                className={`sidebar-link${VISTAS_CONFIG.includes(activeView) ? ' sidebar-link--active' : ''}`}
                onClick={(e) => {
                  e.preventDefault();
                  setSubmenuConfigAbierto(true);
                }}
              >
                <ConfigIcon className="sidebar-link-icon" />
                <span>Config</span>
                <ChevronRightIcon className="sidebar-link-chevron" />
              </a>
            )}
          </>
        )}
      </nav>
    </aside>
  );
}
