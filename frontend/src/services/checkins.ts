import api from './api';
import type { Checkin, EventoCheckin, QrCodeSessao, SessaoCheckin, TipoSessaoCheckin } from '../types';

/** Admin gera uma sessão de check-in (entrada) ou check-out (saída) pro
 *  evento. A sessão vale pelo horário do evento - fora dele, a API
 *  recusa a criação. */
export function criarSessaoCheckin(eventoId: string, tipo: TipoSessaoCheckin) {
  return api.post<SessaoCheckin>(`/eventos/${eventoId}/checkin-sessoes`, { tipo }).then((res) => res.data);
}

/** Busca o QR da janela atual. Precisa ser chamado de novo a cada
 *  `codigo_expira_em`: o link embutido carrega um código rotativo, e o
 *  QR anterior deixa de ser aceito. */
export function buscarQrCodeSessao(sessaoId: string) {
  return api.get<QrCodeSessao>(`/checkin-sessoes/${sessaoId}/qrcode`).then((res) => res.data);
}

/** Chamado pelo aluno ao escanear o QR com a câmera do celular. O
 *  `codigo` vem do parâmetro `c` da URL que estava no QR - sem ele (ou
 *  com um já vencido) a API recusa. */
export function confirmarCheckinSessao(sessaoId: string, codigo: string) {
  return api.post<Checkin>(`/checkin-sessoes/${sessaoId}/confirmar`, { codigo }).then((res) => res.data);
}

/** Relatório de presença (admin/professor) - quem já fez check-in nesse
 *  evento, com nome/RGM do aluno. */
export function listarCheckinsDoEvento(eventoId: string) {
  return api.get<EventoCheckin[]>(`/eventos/${eventoId}/checkins`).then((res) => res.data);
}

/**
 * Admin afirma a presença de quem participou e não conseguiu ler o QR
 * code (câmera com defeito, sinal fraco, conta travada na hora).
 *
 * Registra a PRESENÇA, não os pontos: pontuação, certificado e saída da
 * lista de no-show derivam todos do check-in, então passam a valer
 * sozinhos. Entrada e saída são as do próprio evento - não há horário a
 * informar. Ver PresencaManualService no backend.
 */
export function registrarPresencaManual(eventoId: string, alunoId: string, justificativa: string) {
  return api
    .post<EventoCheckin>(`/eventos/${eventoId}/presencas-manuais`, {
      aluno_id: alunoId,
      justificativa,
    })
    .then((res) => res.data);
}
