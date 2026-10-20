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

/**
 * Os dias que o catálogo da coordenação usa, em ordem de semana.
 *
 * Lista fixa, e não derivada dos dados: os botões precisam ficar sempre
 * na mesma posição. Se saíssem da distribuição carregada, mudariam de
 * lugar a cada filtro aplicado e o admin clicaria no dia errado.
 *
 * As grafias batem com as gravadas na V9 (a migration uniformizou as
 * cinco variantes que vinham da planilha, como "SEGUNDA-FERIA").
 */
export const DIAS_SEMANA = [
  'SEGUNDA-FEIRA',
  'TERÇA-FEIRA',
  'QUARTA-FEIRA',
  'QUINTA-FEIRA',
  'SEXTA-FEIRA',
  'SÁBADO',
] as const;

export type DiaSemana = (typeof DIAS_SEMANA)[number];

/** Rótulo curto para os botões de filtro - "SEGUNDA-FEIRA" em seis
 *  botões lado a lado não caberia na largura da tela. */
export const ROTULO_DIA_CURTO: Record<DiaSemana, string> = {
  'SEGUNDA-FEIRA': 'Seg',
  'TERÇA-FEIRA': 'Ter',
  'QUARTA-FEIRA': 'Qua',
  'QUINTA-FEIRA': 'Qui',
  'SEXTA-FEIRA': 'Sex',
  'SÁBADO': 'Sáb',
};
