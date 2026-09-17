import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { jwtDecode } from 'jwt-decode';
import {
  login as loginRequest,
  redefinirSenha as redefinirSenhaRequest,
  registrar as registrarRequest,
} from '../services/auth';
import { TOKEN_KEY, USUARIO_KEY } from '../services/api';
import type {
  CadastroAlunoRequest,
  JwtPayload,
  LoginRequest,
  LoginResponse,
  Perfil,
  RedefinirSenhaRequest,
  Usuario,
} from '../types';

interface AuthContextValue {
  usuario: Usuario | null;
  perfil: Perfil | null;
  autenticado: boolean;
  entrar: (dados: LoginRequest) => Promise<Usuario>;
  registrar: (dados: CadastroAlunoRequest) => Promise<Usuario>;
  redefinirSenha: (dados: RedefinirSenhaRequest) => Promise<Usuario>;
  /** Reflete no contexto um usuário que mudou por fora do login (hoje:
   *  a própria pessoa corrigindo o cadastro em /perfil). Sem isto o
   *  nome na navbar continuaria o antigo até o próximo login, já que
   *  ele vem do objeto salvo no localStorage, não do token. */
  atualizarUsuario: (usuario: Usuario) => void;
  sair: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function lerUsuarioSalvo(): Usuario | null {
  const bruto = localStorage.getItem(USUARIO_KEY);
  if (!bruto) return null;
  try {
    return JSON.parse(bruto) as Usuario;
  } catch {
    return null;
  }
}

/** Perfil extraido do payload do JWT (claim "perfil"), como pedido - serve
 *  de fonte da verdade independente do objeto "usuario" guardado, caso um
 *  dia fiquem dessincronizados. */
function perfilDoToken(token: string | null): Perfil | null {
  if (!token) return null;
  try {
    const payload = jwtDecode<JwtPayload>(token);
    return payload.perfil;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(() => lerUsuarioSalvo());
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));

  const persistirSessao = (resposta: LoginResponse) => {
    localStorage.setItem(TOKEN_KEY, resposta.token);
    localStorage.setItem(USUARIO_KEY, JSON.stringify(resposta.usuario));
    setToken(resposta.token);
    setUsuario(resposta.usuario);
    return resposta.usuario;
  };

  const entrar = async (dados: LoginRequest) => persistirSessao(await loginRequest(dados));

  // Cadastro publico ja devolve token igual ao login - entra direto no
  // sistema sem precisar de um segundo passo de login em seguida.
  const registrar = async (dados: CadastroAlunoRequest) => persistirSessao(await registrarRequest(dados));

  // "Esqueci minha senha" tambem ja devolve token - entra direto com a
  // senha nova.
  const redefinirSenha = async (dados: RedefinirSenhaRequest) => persistirSessao(await redefinirSenhaRequest(dados));

  const atualizarUsuario = (atualizado: Usuario) => {
    localStorage.setItem(USUARIO_KEY, JSON.stringify(atualizado));
    setUsuario(atualizado);
  };

  const sair = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USUARIO_KEY);
    setToken(null);
    setUsuario(null);
  };

  const perfil = useMemo(() => usuario?.perfil ?? perfilDoToken(token), [usuario, token]);

  const value: AuthContextValue = {
    usuario,
    perfil,
    autenticado: Boolean(token),
    entrar,
    registrar,
    redefinirSenha,
    atualizarUsuario,
    sair,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth precisa ser usado dentro de um AuthProvider');
  }
  return ctx;
}
