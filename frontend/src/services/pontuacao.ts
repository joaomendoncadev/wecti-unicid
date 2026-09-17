import api from './api';
import type { Pontuacao, PontuacaoExtra, Ranking } from '../types';

export function buscarMinhaPontuacao() {
  return api.get<Pontuacao>('/me/pontuacao').then((res) => res.data);
}

export function buscarPontuacaoDoAluno(alunoId: string) {
  return api.get<Pontuacao>(`/pontuacao/aluno/${alunoId}`).then((res) => res.data);
}

/** Classificação por pontos - tela do admin. O aluno não vê mais o
 *  ranking da turma (só a própria pontuação), e o backend recusa a
 *  chamada dele com 403. */
export function buscarRanking() {
  return api.get<Ranking>('/ranking').then((res) => res.data);
}

/** Lança pontos de gincana num aluno (só admin). */
export function lancarPontosExtras(dados: { aluno_id: string; pontos: number; motivo: string }) {
  return api.post<PontuacaoExtra>('/pontuacao-extra', dados).then((res) => res.data);
}

/** Histórico de lançamentos de um aluno - o admin confere antes de
 *  premiar de novo, para não contar a mesma gincana duas vezes. */
export function listarPontosExtras(alunoId: string) {
  return api
    .get<PontuacaoExtra[]>('/pontuacao-extra', { params: { aluno_id: alunoId } })
    .then((res) => res.data);
}

/** Desfaz um lançamento (lançou no aluno errado, valor errado...). */
export function removerPontosExtras(id: string) {
  return api.delete(`/pontuacao-extra/${id}`).then(() => undefined);
}
