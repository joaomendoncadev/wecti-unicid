import api from './api';
import type { Disciplina, Distribuicao, DistribuicaoDoAluno, TurnoDisciplina } from '../types';

export interface FiltroDisciplinas {
  turno?: TurnoDisciplina;
  busca?: string;
}

/**
 * Catálogo de turmas da coordenação. Sem paginação de propósito: são 131
 * turmas e o aluno percorre a lista escolhendo as dele - trocar de página
 * no meio perderia o que já digitou nas outras.
 */
export function listarDisciplinas(filtro: FiltroDisciplinas = {}) {
  return api.get<Disciplina[]>('/disciplinas', { params: filtro }).then((res) => res.data);
}

/** O que o aluno já distribuiu, com limite, restante e nota. */
export function buscarMinhaDistribuicao() {
  return api.get<Distribuicao>('/me/distribuicao').then((res) => res.data);
}

/**
 * Substitui a distribuição INTEIRA pelo que for enviado - turma que não
 * estiver na lista é turma que o aluno zerou. O backend usa o id do
 * token, nunca do corpo.
 */
export function salvarMinhaDistribuicao(itens: Array<{ disciplina_id: string; pontos: number }>) {
  return api.put<Distribuicao>('/me/distribuicao', { itens }).then((res) => res.data);
}

/** Consulta do admin: como cada aluno repartiu a pontuação. */
export function listarDistribuicoes() {
  return api.get<DistribuicaoDoAluno[]>('/distribuicoes').then((res) => res.data);
}
