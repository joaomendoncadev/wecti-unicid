import { useCallback, useEffect, useState } from 'react';
import { listarCheckinsDoEvento } from '../services/checkins';
import { extrairMensagemErro } from '../services/api';
import type { EventoCheckin } from '../types';

/** De quanto em quanto tempo a lista se atualiza sozinha. 10s mantém o
 *  telão vivo durante o check-in sem transformar a tela num gerador de
 *  requisições — a sala enche em minutos, não em milissegundos. */
const INTERVALO_MS = 10_000;

/**
 * Lista de presença do evento, atualizando sozinha enquanto a tela fica
 * aberta.
 *
 * Existe porque essa tela fica projetada durante o check-in: o admin
 * precisa ver o aluno aparecer na lista logo depois de ele escanear o QR,
 * sem clicar em nada. O botão "Atualizar" continua, para quem quiser
 * forçar agora.
 *
 * Três cuidados que a versão ingênua (um `setInterval` chamando a API)
 * não teria:
 *
 * - **Não reagenda antes da resposta chegar.** O próximo ciclo é marcado
 *   depois que o anterior termina; com `setInterval` e rede lenta, as
 *   requisições se empilhariam.
 * - **Não pisca.** Só a primeira carga de cada evento mostra "carregando";
 *   as seguintes trocam os dados por baixo, com a tabela na tela. Uma
 *   lista que some e volta a cada 10 segundos num telão é pior do que
 *   uma lista parada.
 * - **Não gasta requisição com a aba escondida.** Se o admin minimizar ou
 *   trocar de aba, o ciclo continua marcando o tempo mas não chama a API;
 *   ao voltar, atualiza na hora.
 */
export function useCheckinsDoEvento(eventoId: string, intervaloMs: number = INTERVALO_MS) {
  const [participantes, setParticipantes] = useState<EventoCheckin[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [atualizadoEm, setAtualizadoEm] = useState<Date | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  // Mudar isto reinicia o efeito abaixo, que busca na hora e remarca o
  // ciclo a partir de agora - é o que o botão "Atualizar" faz.
  const [gatilho, setGatilho] = useState(0);
  const recarregar = useCallback(() => setGatilho((n) => n + 1), []);

  // Zera o que era do evento anterior ANTES da primeira busca do novo.
  // Sem isto, a tela mostraria por um instante a lista do evento errado -
  // e `atualizadoEm: null` é justamente o que marca "primeira carga".
  useEffect(() => {
    setParticipantes([]);
    setAtualizadoEm(null);
    setErro(null);
  }, [eventoId]);

  useEffect(() => {
    if (!eventoId) return;

    let cancelado = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const agendar = () => {
      timer = setTimeout(buscar, intervaloMs);
    };

    const buscar = async () => {
      if (cancelado) return;

      if (document.visibilityState === 'hidden') {
        agendar();
        return;
      }

      setBuscando(true);
      try {
        const dados = await listarCheckinsDoEvento(eventoId);
        if (cancelado) return;
        setParticipantes(dados);
        setAtualizadoEm(new Date());
        setErro(null);
      } catch (e) {
        if (cancelado) return;
        // Mantém a lista que já está na tela de propósito: uma falha de
        // rede momentânea não deve apagar a lista de presença projetada.
        setErro(extrairMensagemErro(e, 'Não foi possível atualizar a lista.'));
      } finally {
        if (!cancelado) {
          setBuscando(false);
          agendar();
        }
      }
    };

    const aoVoltarParaAba = () => {
      if (document.visibilityState !== 'visible') return;
      if (timer) clearTimeout(timer);
      buscar();
    };

    buscar();
    document.addEventListener('visibilitychange', aoVoltarParaAba);

    return () => {
      cancelado = true;
      if (timer) clearTimeout(timer);
      document.removeEventListener('visibilitychange', aoVoltarParaAba);
    };
  }, [eventoId, intervaloMs, gatilho]);

  return {
    participantes,
    /** Só na primeira carga de cada evento - é quando não há nada para
     *  mostrar ainda. */
    carregandoInicial: buscando && atualizadoEm === null,
    /** Atualização em segundo plano, com a tabela já na tela. */
    atualizando: buscando && atualizadoEm !== null,
    atualizadoEm,
    erro,
    recarregar,
    intervaloSegundos: Math.round(intervaloMs / 1000),
  };
}
