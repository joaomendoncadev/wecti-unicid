import { useMemo, useState } from 'react';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import EmptyState from '../../components/EmptyState';
import { useInscricoes } from '../../hooks/useInscricoes';
import { useEventos } from '../../hooks/useEventos';
import { baixarCertificado } from '../../services/inscricoes';
import { extrairMensagemErro } from '../../services/api';
import { useToast } from '../../context/ToastContext';
import { salvarArquivo } from '../../utils/arquivo';
import { formatarDataHora } from '../../utils/data';

export default function CertificadosPage() {
  const { inscricoes, carregando, erro, recarregar } = useInscricoes('historico');
  const { eventos } = useEventos({});
  const { notificarErro } = useToast();
  const [baixandoId, setBaixandoId] = useState<string | null>(null);

  const eventosPorId = useMemo(() => new Map(eventos.map((e) => [e.id, e])), [eventos]);
  const disponiveis = inscricoes.filter((i) => i.certificado_disponivel);

  const baixar = async (inscricaoId: string, tituloEvento: string) => {
    setBaixandoId(inscricaoId);
    try {
      const blob = await baixarCertificado(inscricaoId);
      salvarArquivo(blob, `certificado-${tituloEvento.replace(/\s+/g, '-').toLowerCase()}.pdf`);
    } catch (e) {
      notificarErro(extrairMensagemErro(e, 'Não foi possível gerar o certificado.'));
    } finally {
      setBaixandoId(null);
    }
  };

  return (
    <PageContainer titulo="Certificados" descricao="Certificados disponíveis para download">
      {carregando && <LoadingBlock mensagem="Carregando certificados..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} onTentarNovamente={recarregar} />}
      {!carregando && !erro && disponiveis.length === 0 && (
        <EmptyState
          titulo="Nenhum certificado disponível"
          descricao="Certificados aparecem aqui depois que você lê o QR code de entrada e o de saída do evento."
          icone="🎓"
        />
      )}
      {!carregando && !erro && disponiveis.length > 0 && (
        <div className="flex flex-col gap-3">
          {disponiveis.map((inscricao) => {
            const evento = eventosPorId.get(inscricao.evento_id);
            return (
              <div
                key={inscricao.id}
                className="flex flex-col gap-3 rounded-card border border-border bg-surface p-5 sm:flex-row sm:items-center sm:justify-between"
              >
                <div>
                  <h3 className="font-semibold text-text">{evento?.titulo ?? 'Evento'}</h3>
                  <p className="text-sm text-text-muted">{evento ? formatarDataHora(evento.data_hora_inicio) : ''}</p>
                </div>
                <Button
                  carregando={baixandoId === inscricao.id}
                  onClick={() => baixar(inscricao.id, evento?.titulo ?? 'evento')}
                >
                  Baixar PDF
                </Button>
              </div>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}
