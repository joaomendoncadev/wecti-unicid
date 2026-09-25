import { useCallback, useEffect, useState } from 'react';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import QrCodeComTempo from '../../components/QrCodeComTempo';
import EmptyState from '../../components/EmptyState';
import { useEventos } from '../../hooks/useEventos';
import { useCheckinsDoEvento } from '../../hooks/useCheckinsDoEvento';
import { criarSessaoCheckin, buscarQrCodeSessao } from '../../services/checkins';
import { extrairMensagemErro } from '../../services/api';
import { useToast } from '../../context/ToastContext';
import { formatarDataHora } from '../../utils/data';
import type { QrCodeSessao, SessaoCheckin, TipoSessaoCheckin } from '../../types';

/** Folga depois do fim da janela, pra pedir o QR seguinte já dentro da
 *  janela nova e não pegar o código velho por causa de arredondamento de
 *  relógio. */
const FOLGA_MS = 1000;

/**
 * Admin escolhe o evento e o tipo de sessão (entrada ou saída) e gera um
 * QR code pra projetar na tela. Cada aluno inscrito escaneia com a câmera
 * do próprio celular (Android ou iOS, sem app nenhum) - a câmera abre a
 * URL embutida no QR direto no navegador, que confirma a presença dele
 * autenticado como aluno. Ver CheckinSessaoController e
 * docs/openapi.yaml.
 *
 * O QR **se renova sozinho** enquanto essa tela fica aberta: o link
 * embutido carrega um código que vale só por uma janela de tempo curta.
 * É o que impede que um aluno presente fotografe a tela, mande no grupo e
 * quem não veio marque presença de casa. Por isso a tela precisa ficar
 * aberta e projetada durante o check-in - se ela for fechada, o QR
 * congela e para de ser aceito na janela seguinte.
 */
export default function AdminCheckinPage() {
  const { eventos, carregando: carregandoEventos } = useEventos({});
  const { notificarErro } = useToast();

  const [eventoId, setEventoId] = useState('');
  const [tipo, setTipo] = useState<TipoSessaoCheckin>('ENTRADA');
  const [sessao, setSessao] = useState<SessaoCheckin | null>(null);
  const [qrCode, setQrCode] = useState<QrCodeSessao | null>(null);
  const [gerando, setGerando] = useState(false);
  const [erroRotacao, setErroRotacao] = useState<string | null>(null);

  // Lista de presença: se atualiza sozinha enquanto a tela fica aberta,
  // que é o modo como ela é usada (projetada durante o check-in). O
  // botão "Atualizar" continua ali para forçar agora.
  const {
    participantes,
    carregandoInicial: carregandoParticipantes,
    atualizando,
    atualizadoEm,
    erro: erroParticipantes,
    recarregar: recarregarParticipantes,
    intervaloSegundos,
  } = useCheckinsDoEvento(eventoId);

  /** Esconde o QR gerado anteriormente assim que o evento ou o tipo
   *  mudam - sem isso, o QR (e o link embutido nele) continuavam sendo
   *  os do evento/tipo antigo, só o texto acima mudava pra refletir a
   *  nova seleção, dando a falsa impressão de que era o QR certo. */
  const limparQrCode = useCallback(() => {
    setSessao(null);
    setQrCode(null);
    setErroRotacao(null);
  }, []);

  /**
   * Mantém o QR da tela sempre na janela atual: busca o de agora e
   * reagenda a próxima busca pro instante em que esse vence.
   *
   * Isso não é um refresh cosmético - o QR anterior deixa de ser aceito
   * pela API. Se a rotação parar (rede caiu, sessão acabou), o QR na tela
   * vira decoração e os alunos levam erro ao escanear, então o erro
   * aparece no lugar do QR em vez de deixar a imagem velha ali.
   */
  useEffect(() => {
    const sessaoId = sessao?.id;
    if (!sessaoId) return;

    let cancelado = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const atualizar = async () => {
      try {
        const qr = await buscarQrCodeSessao(sessaoId);
        if (cancelado) return;
        setQrCode(qr);
        setErroRotacao(null);
        const emMs = new Date(qr.codigo_expira_em).getTime() - Date.now() + FOLGA_MS;
        timer = setTimeout(atualizar, Math.max(emMs, FOLGA_MS));
      } catch (e) {
        if (cancelado) return;
        setQrCode(null);
        setErroRotacao(extrairMensagemErro(e, 'O QR code parou de ser atualizado. Gere um novo.'));
      }
    };
    atualizar();

    return () => {
      cancelado = true;
      if (timer) clearTimeout(timer);
    };
  }, [sessao?.id]);

  const selecionarEvento = (novoEventoId: string) => {
    setEventoId(novoEventoId);
    limparQrCode();
  };

  const selecionarTipo = (novoTipo: TipoSessaoCheckin) => {
    setTipo(novoTipo);
    limparQrCode();
  };

  const gerarQrCode = async () => {
    if (!eventoId) {
      notificarErro('Escolha um evento primeiro.');
      return;
    }
    setGerando(true);
    limparQrCode();
    try {
      // O primeiro QR (e todos os seguintes) vêm do efeito de rotação
      // acima, disparado por essa mudança de sessão.
      setSessao(await criarSessaoCheckin(eventoId, tipo));
    } catch (e) {
      notificarErro(extrairMensagemErro(e, 'Não foi possível gerar o QR code.'));
    } finally {
      setGerando(false);
    }
  };

  const eventoSelecionado = eventos.find((e) => e.id === eventoId);

  return (
    <PageContainer
      titulo="Check-in"
      descricao="Gere o QR code de entrada ou saída e projete na tela - cada aluno confirma a própria presença escaneando com a câmera do celular"
    >
      <div className="flex flex-col gap-4 rounded-card border border-border bg-surface p-6">
        {carregandoEventos ? (
          <LoadingBlock mensagem="Carregando eventos..." />
        ) : (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <label className="flex flex-1 flex-col gap-1.5 text-sm">
              <span className="font-medium text-text">Evento</span>
              <select
                className="rounded-lg border border-border bg-surface px-3.5 py-2.5 text-text outline-none focus:border-accent"
                value={eventoId}
                onChange={(e) => selecionarEvento(e.target.value)}
              >
                <option value="">Selecione um evento</option>
                {eventos.map((evento) => (
                  <option key={evento.id} value={evento.id}>
                    {evento.titulo}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-sm">
              <span className="font-medium text-text">Tipo</span>
              <select
                className="rounded-lg border border-border bg-surface px-3.5 py-2.5 text-text outline-none focus:border-accent"
                value={tipo}
                onChange={(e) => selecionarTipo(e.target.value as TipoSessaoCheckin)}
              >
                <option value="ENTRADA">Entrada</option>
                <option value="SAIDA">Saída</option>
              </select>
            </label>

            <Button carregando={gerando} onClick={gerarQrCode} className="sm:mb-0">
              Gerar QR code
            </Button>
          </div>
        )}

        {sessao && (
          <div className="flex flex-col items-center gap-3 rounded-card border border-accent/30 bg-accent/5 p-8">
            <p className="text-sm text-text-muted">
              QR de <span className="font-semibold text-text">{tipo === 'ENTRADA' ? 'entrada' : 'saída'}</span> para
            </p>
            <p className="text-lg font-bold text-text">{eventoSelecionado?.titulo}</p>

            {qrCode && (
              <QrCodeComTempo
                pngBase64={qrCode.png_base64}
                expiraEm={qrCode.codigo_expira_em}
                janelaSegundos={qrCode.janela_segundos}
              />
            )}

            {!qrCode && !erroRotacao && <LoadingBlock mensagem="Gerando QR code..." />}

            {erroRotacao && (
              <div className="flex h-72 w-72 flex-col items-center justify-center gap-2 rounded-lg border border-red-500/40 bg-red-500/5 p-6 text-center">
                <span className="text-3xl">⚠️</span>
                <p className="text-sm text-red-400">{erroRotacao}</p>
              </div>
            )}

            <p className="text-xs text-text-muted">
              Check-in aberto até {new Date(sessao.expira_em).toLocaleString('pt-BR')} - peça pro aluno escanear
              com a câmera do próprio celular
            </p>
            <p className="max-w-md text-center text-xs text-text-muted">
              O QR se renova a cada 15 minutos, e a tela busca o próximo sozinha - quem já escaneou continua
              conseguindo confirmar. <span className="font-medium text-text">Deixe esta tela aberta e
              projetada</span> durante o check-in: fechada, ela para de renovar e o QR projetado vence.
            </p>
          </div>
        )}
      </div>

      {eventoId && (
        <div className="mt-6 flex flex-col gap-4 rounded-card border border-border bg-surface p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-text">Participantes do Evento</h2>
              <p className="text-sm text-text-muted">
                {carregandoParticipantes
                  ? 'Carregando...'
                  : `${participantes.length} participante${participantes.length === 1 ? '' : 's'} presente${participantes.length === 1 ? '' : 's'}.`}
              </p>
              {/* Prova de vida: num telão, uma lista parada e uma lista
                  quebrada têm exatamente a mesma aparência. O horário da
                  última atualização é o que distingue as duas. */}
              {atualizadoEm && (
                <p className="mt-0.5 flex items-center gap-1.5 text-xs text-text-muted">
                  <span
                    className={`inline-block h-1.5 w-1.5 shrink-0 rounded-full ${
                      atualizando ? 'bg-accent' : 'bg-accent/40'
                    }`}
                    aria-hidden
                  />
                  Atualiza sozinha a cada {intervaloSegundos}s - última às{' '}
                  {atualizadoEm.toLocaleTimeString('pt-BR')}
                </p>
              )}
            </div>
            <Button variante="outline" onClick={recarregarParticipantes} carregando={atualizando}>
              Atualizar
            </Button>
          </div>

          {/* A lista continua na tela: uma falha de rede momentânea não
              pode apagar a lista de presença projetada. */}
          {erroParticipantes && (
            <p className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-4 py-2.5 text-sm text-amber-300">
              {erroParticipantes} Tentando de novo em alguns segundos.
            </p>
          )}

          {carregandoParticipantes && <LoadingBlock mensagem="Carregando participantes..." />}

          {!carregandoParticipantes && participantes.length === 0 && (
            <EmptyState titulo="Nenhum check-in ainda" descricao="Assim que um aluno confirmar a entrada, ele aparece aqui." />
          )}

          {participantes.length > 0 && (
            <div className="overflow-x-auto rounded-card border border-border">
              <table className="w-full min-w-[560px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-text-muted">
                    <th className="px-5 py-3 font-medium">Aluno</th>
                    <th className="px-5 py-3 font-medium">RGM</th>
                    <th className="px-5 py-3 font-medium">Entrada</th>
                    <th className="px-5 py-3 font-medium">Saída</th>
                  </tr>
                </thead>
                <tbody>
                  {participantes.map((p) => (
                    <tr key={p.inscricao_id} className="border-b border-border last:border-0">
                      <td className="px-5 py-3 text-text">{p.aluno_nome}</td>
                      <td className="px-5 py-3 text-text-muted">{p.aluno_rgm ?? '-'}</td>
                      <td className="px-5 py-3 text-text-muted">{formatarDataHora(p.entrada)}</td>
                      <td className="px-5 py-3 text-text-muted">{p.saida ? formatarDataHora(p.saida) : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </PageContainer>
  );
}
