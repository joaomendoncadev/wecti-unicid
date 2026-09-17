import api from './api';
import type { AtualizarPerfil, Pagina, NovoUsuario, Perfil, Usuario } from '../types';

export interface FiltroUsuarios {
  perfil?: Perfil;
  busca?: string;
  page?: number;
  size?: number;
}

export function listarUsuarios(filtro: FiltroUsuarios = {}) {
  return api.get<Pagina<Usuario>>('/usuarios', { params: filtro }).then((res) => res.data);
}

export function criarUsuario(dados: NovoUsuario) {
  return api.post<Usuario>('/usuarios', dados).then((res) => res.data);
}

export function atualizarUsuario(id: string, dados: NovoUsuario) {
  return api.put<Usuario>(`/usuarios/${id}`, dados).then((res) => res.data);
}

export function excluirUsuario(id: string) {
  return api.delete<void>(`/usuarios/${id}`).then((res) => res.data);
}

export function buscarMeuUsuario() {
  return api.get<Usuario>('/usuarios/me').then((res) => res.data);
}

/** A própria pessoa corrigindo o cadastro. Não leva id: o backend usa o
 *  do token, então não há como editar o perfil de outra pessoa. */
export function atualizarMeuPerfil(dados: AtualizarPerfil) {
  return api.put<Usuario>('/usuarios/me', dados).then((res) => res.data);
}
