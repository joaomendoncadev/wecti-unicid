import { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import Logo from './Logo';
import { useAuth } from '../context/AuthContext';
import { homeDoPerfil } from '../utils/rotas';

interface ItemMenu {
  to: string;
  label: string;
}

const MENU_ALUNO: ItemMenu[] = [
  { to: '/eventos', label: 'Eventos' },
  { to: '/minhas-inscricoes', label: 'Minhas inscrições' },
  { to: '/historico', label: 'Histórico' },
  { to: '/certificados', label: 'Certificados' },
  { to: '/pontuacao', label: 'Pontuação' },
  { to: '/ranking', label: 'Ranking' },
];

const MENU_ADMIN: ItemMenu[] = [
  { to: '/admin/eventos', label: 'Eventos' },
  { to: '/admin/usuarios', label: 'Usuários' },
  { to: '/admin/inscritos', label: 'Inscritos' },
  { to: '/admin/checkin', label: 'Check-in' },
  { to: '/admin/ranking', label: 'Ranking' },
];

export default function Navbar() {
  const { usuario, perfil, sair } = useAuth();
  const navigate = useNavigate();
  const [rolado, setRolado] = useState(false);
  const [menuAberto, setMenuAberto] = useState(false);

  useEffect(() => {
    const aoRolar = () => setRolado(window.scrollY > 40);
    window.addEventListener('scroll', aoRolar);
    return () => window.removeEventListener('scroll', aoRolar);
  }, []);

  const itens = perfil === 'ADMIN' ? MENU_ADMIN : MENU_ALUNO;
  const perfilInicial = homeDoPerfil(perfil);

  const handleSair = () => {
    sair();
    navigate('/login');
  };

  return (
    <nav
      className={`fixed inset-x-0 top-0 z-50 flex h-16 items-center justify-between border-b border-border px-4 backdrop-blur-xl transition-colors duration-300 sm:px-8 ${
        rolado ? 'bg-[rgba(10,10,15,0.96)]' : 'bg-[rgba(10,10,15,0.75)]'
      }`}
    >
      <NavLink to={perfilInicial}>
        <Logo />
      </NavLink>

      <ul className="hidden items-center gap-6 md:flex">
        {itens.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              className={({ isActive }) =>
                `text-sm font-medium transition-colors ${isActive ? 'text-accent' : 'text-text-muted hover:text-text'}`
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>

      <div className="hidden items-center gap-4 md:flex">
        <NavLink
          to="/perfil"
          className={({ isActive }) =>
            `text-sm font-medium ${isActive ? 'text-accent' : 'text-text-muted hover:text-text'}`
          }
        >
          {usuario?.nome ?? 'Perfil'}
        </NavLink>
        <button
          onClick={handleSair}
          className="rounded-full border border-border px-4 py-1.5 text-sm font-medium text-text transition hover:border-accent hover:text-accent"
        >
          Sair
        </button>
      </div>

      <button
        className="flex flex-col gap-1.5 md:hidden"
        aria-label="Menu"
        onClick={() => setMenuAberto((v) => !v)}
      >
        <span className="h-0.5 w-6 rounded bg-text" />
        <span className="h-0.5 w-6 rounded bg-text" />
        <span className="h-0.5 w-6 rounded bg-text" />
      </button>

      {menuAberto && (
        <div className="fixed inset-x-0 top-16 flex flex-col gap-4 border-b border-border bg-[rgba(10,10,15,0.97)] p-6 md:hidden">
          {itens.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setMenuAberto(false)}
              className={({ isActive }) => `text-sm font-medium ${isActive ? 'text-accent' : 'text-text-muted'}`}
            >
              {item.label}
            </NavLink>
          ))}
          <NavLink to="/perfil" onClick={() => setMenuAberto(false)} className="text-sm font-medium text-text-muted">
            {usuario?.nome ?? 'Perfil'}
          </NavLink>
          <button onClick={handleSair} className="w-fit rounded-full border border-border px-4 py-1.5 text-sm text-text">
            Sair
          </button>
        </div>
      )}
    </nav>
  );
}
