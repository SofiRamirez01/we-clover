import type { ReactElement, SVGProps } from 'react';
import logoTrebol from '../assets/logo-trebol-menta.svg';
import './Sidebar.css';

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

const CrmIcon = (props: IconProps) => (
  <svg {...baseIconProps(props)}>
    <circle cx="9" cy="8" r="3" />
    <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
    <circle cx="17" cy="7" r="2.4" />
    <path d="M15.5 13.2c2.6.4 4.5 2.6 4.5 5.3" />
  </svg>
);

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
    <rect x="3" y="8.5" width="18" height="7" rx="1.5" />
    <path d="M6.5 8.5V12M10 8.5V12M13.5 8.5V12M17 8.5V12" />
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

export type AppView = 'pedidos' | 'pedidos-nuevo' | 'usuarios';

interface NavItem {
  label: string;
  icon: (props: IconProps) => ReactElement;
  view?: AppView;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'CRM', icon: CrmIcon },
  { label: 'PEDIDOS', icon: PedidosIcon, view: 'pedidos' },
  { label: 'FICHAS TÉCNICAS', icon: FichasIcon },
  { label: 'PRODUCCIÓN', icon: ProduccionIcon },
  { label: 'PLANIFICADOR COMPRAS', icon: ComprasIcon },
  { label: 'MOTOR TIZADA', icon: TizadaIcon },
  { label: 'REPORTES', icon: ReportesIcon },
  { label: 'USUARIOS', icon: UsuariosIcon, view: 'usuarios' },
];

interface SidebarProps {
  activeView: AppView;
  onNavigate: (view: AppView) => void;
}

export default function Sidebar({ activeView, onNavigate }: SidebarProps) {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <img src={logoTrebol} className="sidebar-brand-icon" alt="WE CLOVER" />
      </div>

      <nav className="sidebar-nav">
        {NAV_ITEMS.map(({ label, icon: Icon, view }) => {
          const active =
            view === activeView || (view === 'pedidos' && activeView === 'pedidos-nuevo');
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
        })}
      </nav>
    </aside>
  );
}
