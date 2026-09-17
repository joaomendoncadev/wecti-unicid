import type { Evento } from '../types';
import { turnoDoEvento, type TurnoEvento } from './data';

export interface GrupoEventos {
  /** Identidade estável do grupo — serve de `key` no React. */
  chave: string;
  /** Data/hora do primeiro evento do grupo, para montar o cabeçalho. */
  inicio: string;
  turno: TurnoEvento;
  eventos: Evento[];
}

/**
 * Quebra a lista em blocos de "um dia, um turno", mantendo a ordem
 * cronológica crescente.
 *
 * Agrupa por dia **e** turno, e não só por turno: com dois dias de
 * evento, um grupo "Manhã" único juntaria a palestra de quinta com a de
 * sexta na mesma caixa, e o aluno perderia justamente a informação que
 * usa para se organizar.
 *
 * Ordena por conta própria mesmo já recebendo ordenado da API: é barato
 * e evita que um filtro aplicado na tela reintroduza a bagunça que esta
 * função existe para resolver.
 */
export function agruparPorDiaETurno(eventos: Evento[]): GrupoEventos[] {
  const ordenados = [...eventos].sort((a, b) =>
    a.data_hora_inicio.localeCompare(b.data_hora_inicio),
  );

  const grupos: GrupoEventos[] = [];

  for (const evento of ordenados) {
    const turno = turnoDoEvento(evento.data_hora_inicio);
    // "2026-09-28" — a string do backend já vem nesse formato, então o
    // slice evita converter para Date só para reextrair o dia (e evita
    // junto a armadilha de fuso que isso traria).
    const dia = evento.data_hora_inicio.slice(0, 10);
    const chave = `${dia}-${turno}`;

    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.chave === chave) {
      ultimo.eventos.push(evento);
    } else {
      grupos.push({ chave, inicio: evento.data_hora_inicio, turno, eventos: [evento] });
    }
  }

  return grupos;
}
