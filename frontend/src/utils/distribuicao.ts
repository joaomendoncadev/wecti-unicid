import type { TurnoDisciplina } from '../types';

/** "MANHA" é como o valor viaja (sem acento, por ser enum de banco); a
 *  tela mostra "Manhã". */
export const ROTULO_TURNO: Record<TurnoDisciplina, string> = {
  MANHA: 'Manhã',
  NOITE: 'Noite',
};

/**
 * Nota no formato brasileiro, com o mínimo de casas que não perde valor.
 *
 * Três casas, e não duas: com 1000 pontos por nota qualquer valor cabe
 * exato em três (137 pontos = 0,137). Arredondar para duas faria a soma
 * das linhas não bater com o total, e o aluno conferindo o comprovante
 * concluiria - com razão - que o sistema errou.
 */
export function formatarNota(nota: number): string {
  return nota.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 3 });
}
