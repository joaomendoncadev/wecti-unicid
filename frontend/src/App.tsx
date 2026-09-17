import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import RegistrarPage from './pages/RegistrarPage';
import RecuperarSenhaPage from './pages/RecuperarSenhaPage';
import ValidarCertificadoPage from './pages/ValidarCertificadoPage';
import EventosPage from './pages/aluno/EventosPage';
import MinhasInscricoesPage from './pages/aluno/MinhasInscricoesPage';
import HistoricoPage from './pages/aluno/HistoricoPage';
import CertificadosPage from './pages/aluno/CertificadosPage';
import PontuacaoPage from './pages/aluno/PontuacaoPage';
import RankingPage from './pages/aluno/RankingPage';
import PerfilPage from './pages/aluno/PerfilPage';
import CheckinConfirmarPage from './pages/aluno/CheckinConfirmarPage';
import AdminEventosPage from './pages/admin/AdminEventosPage';
import AdminUsuariosPage from './pages/admin/AdminUsuariosPage';
import AdminCheckinPage from './pages/admin/AdminCheckinPage';
import AdminInscritosPage from './pages/admin/AdminInscritosPage';
import AdminRankingPage from './pages/admin/AdminRankingPage';
import { homeDoPerfil } from './utils/rotas';
import type { Perfil } from './types';

function RotaPrivada({ perfis, children }: { perfis?: Perfil[]; children: ReactNode }) {
  return (
    <ProtectedRoute perfis={perfis}>
      <Navbar />
      <main>{children}</main>
    </ProtectedRoute>
  );
}

function RotaInicial() {
  const { autenticado, perfil } = useAuth();
  if (!autenticado) return <Navigate to="/login" replace />;
  return <Navigate to={homeDoPerfil(perfil)} replace />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cadastro" element={<RegistrarPage />} />
      <Route path="/recuperar-senha" element={<RecuperarSenhaPage />} />
      {/* Validacao de certificado - publica, sem login (destino do QR
       *  code impresso no certificado). */}
      <Route path="/validar" element={<ValidarCertificadoPage />} />
      <Route path="/validar/:codigo" element={<ValidarCertificadoPage />} />
      <Route path="/" element={<RotaInicial />} />

      {/* Aluno */}
      <Route
        path="/eventos"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <EventosPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/minhas-inscricoes"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <MinhasInscricoesPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/historico"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <HistoricoPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/certificados"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <CertificadosPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/pontuacao"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <PontuacaoPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/ranking"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <RankingPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/perfil"
        element={
          <RotaPrivada>
            <PerfilPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/checkin/confirmar/:sessaoId"
        element={
          <RotaPrivada perfis={['ALUNO']}>
            <CheckinConfirmarPage />
          </RotaPrivada>
        }
      />

      {/* Admin */}
      <Route
        path="/admin/eventos"
        element={
          <RotaPrivada perfis={['ADMIN']}>
            <AdminEventosPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/admin/usuarios"
        element={
          <RotaPrivada perfis={['ADMIN']}>
            <AdminUsuariosPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/admin/ranking"
        element={
          <RotaPrivada perfis={['ADMIN']}>
            <AdminRankingPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/admin/inscritos"
        element={
          <RotaPrivada perfis={['ADMIN']}>
            <AdminInscritosPage />
          </RotaPrivada>
        }
      />
      <Route
        path="/admin/checkin"
        element={
          <RotaPrivada perfis={['ADMIN']}>
            <AdminCheckinPage />
          </RotaPrivada>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
