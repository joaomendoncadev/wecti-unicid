import { useMemo, useState } from 'react';
import PageContainer from '../../components/PageContainer';
import EventoCard from '../../components/EventoCard';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import EmptyState from '../../components/EmptyState';
import { useEventos } from '../../hooks/useEventos';
import { useInscricoes } from '../../hooks/useInscricoes';
import { inscreverEmEvento } from '../../services/inscricoes';
import { extrairMensagemErro } from '../../services/api';
import { useToast } from '../../context/ToastContext';
import { agruparPorDiaETurno } from '../../utils/eventos';
import { formatarDiaPorExtenso, LABEL_TURNO } from '../../utils/data';

type Aba = 'em_cartaz' | 'encerrados';

const ABAS: { valor: Aba; label: string }[] = [
  { valor: 'em_cartaz', label: 'Em cartaz' },
  { valor: 'encerrados', label: 'Encerrados' },
];

const VAZIO: Record<Aba, { titulo: string; descricao: string }> = {
  em_cartaz: {
    titulo: 'Nenhum evento disponível',
    descricao: 'Não há palestras abertas no momento. Confira a aba "Encerrados" para ver as que já aconteceram.',
  },
  encerrados: {
    titulo: 'Nenhum evento encerrado',
    descricao: 'As palestras que já terminaram aparecem aqui.',
  },
};

export default function EventosPage() {
  // "em_cartaz" = o que ainda não terminou, incluindo o que está
  // acontecendo agora. Antes a tela pedia só os que ainda não tinham
  // começado, e a palestra sumia da lista na hora exata em que começava -
  // o aluno achava que tinha sido cancelada. Quando termina de vez, ela
  // sai daqui e passa a viver na aba "Encerrados".
  const [aba, setAba] = useState<Aba>('em_cartaz');
  const { eventos, carregando, erro, recarregar } = useEventos({ status: aba });
  const { inscricoes, recarregar: recarregarInscricoes } = useInscricoes();
  const { notificarSucesso, notificarErro } = useToast();
  const [inscrevendoId, setInscrevendoId] = useState<string | null>(null);

  const grupos = useMemo(() => agruparPorDiaETurno(eventos), [eventos]);

  const jaInscrito = (eventoId: string) =>
    inscricoes.some((i) => i.evento_id === eventoId && i.status === 'ativa');

  const inscrever = async (eventoId: string) => {
    setInscrevendoId(eventoId);
    try {
      await inscreverEmEvento(eventoId);
      notificarSucesso('Inscrição realizada! Sua presença é confirmada no dia, lendo o QR code do evento.');
      recarregarInscricoes();
      // Recarrega os eventos também: a vaga que o aluno acabou de ocupar
      // precisa sumir do contador antes que ele (ou outro) olhe de novo.
      recarregar();
    } catch (erroInscricao) {
      notificarErro(extrairMensagemErro(erroInscricao, 'Não foi possível se inscrever.'));
    } finally {
      setInscrevendoId(null);
    }
  };

  const acaoDoEvento = (eventoId: string, inscricoesAbertas: boolean, emAndamento: boolean, lotado: boolean) => {
    // Na aba de encerrados não há ação possível: um botão desabilitado em
    // cada card só polui a lista, que ali é histórico.
    if (aba === 'encerrados') return undefined;

    // A ordem importa: "Inscrito" tem que vencer os outros estados, senão
    // o aluno que já está inscrito numa palestra em andamento veria
    // "Inscrições encerradas" e acharia que ficou de fora.
    if (jaInscrito(eventoId)) {
      return (
        <Button variante="outline" disabled className="w-full">
          Inscrito
        </Button>
      );
    }
    if (!inscricoesAbertas) {
      // O evento continua na lista depois de começar - o aluno precisa
      // saber que ele existe e está rolando, mesmo sem poder mais entrar.
      return (
        <Button variante="outline" disabled className="w-full">
          {emAndamento ? 'Já começou' : 'Inscrições encerradas'}
        </Button>
      );
    }
    if (lotado) {
      // Continua visível, e não escondido: o aluno precisa saber que o
      // evento existe e encheu - some da tela e ele acha que houve erro.
      return (
        <Button variante="outline" disabled className="w-full">
          Vagas esgotadas
        </Button>
      );
    }
    return (
      <Button carregando={inscrevendoId === eventoId} onClick={() => inscrever(eventoId)} className="w-full">
        Inscrever-se
      </Button>
    );
  };

  return (
    <PageContainer
      titulo="Eventos"
      descricao="Palestras do WECTI - as inscrições fecham pouco depois de cada evento começar"
    >
      <div className="mb-6 flex w-fit gap-1 rounded-full border border-border bg-surface p-1">
        {ABAS.map((opcao) => (
          <button
            key={opcao.valor}
            onClick={() => setAba(opcao.valor)}
            aria-pressed={aba === opcao.valor}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              // text-bg, e não text-white: o accent é claro (#00e8c4) e
              // texto branco em cima dele fica ilegível.
              aba === opcao.valor ? 'bg-accent text-bg' : 'text-text-muted hover:text-text'
            }`}
          >
            {opcao.label}
          </button>
        ))}
      </div>

      {carregando && <LoadingBlock mensagem="Carregando eventos..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} onTentarNovamente={recarregar} />}
      {!carregando && !erro && eventos.length === 0 && (
        <EmptyState titulo={VAZIO[aba].titulo} descricao={VAZIO[aba].descricao} icone="🗓️" />
      )}

      {!carregando && !erro && grupos.length > 0 && (
        <div className="flex flex-col gap-10">
          {grupos.map((grupo) => (
            <section key={grupo.chave}>
              <div className="mb-4 flex items-baseline gap-3 border-b border-border pb-2">
                <h2 className="text-sm font-semibold uppercase tracking-wider text-text">
                  {formatarDiaPorExtenso(grupo.inicio)}
                </h2>
                <span className="text-xs font-medium uppercase tracking-wider text-accent">
                  {LABEL_TURNO[grupo.turno]}
                </span>
                <span className="ml-auto text-xs text-text-muted">
                  {grupo.eventos.length} {grupo.eventos.length === 1 ? 'palestra' : 'palestras'}
                </span>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {grupo.eventos.map((evento) => (
                  <EventoCard
                    key={evento.id}
                    evento={evento}
                    acao={acaoDoEvento(
                      evento.id,
                      evento.inscricoes_abertas,
                      evento.em_andamento,
                      evento.lotado,
                    )}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </PageContainer>
  );
}
