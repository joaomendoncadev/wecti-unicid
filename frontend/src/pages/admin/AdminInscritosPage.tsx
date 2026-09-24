import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import EmptyState from '../../components/EmptyState';
import ErrorMessage from '../../components/ErrorMessage';
import { useEventos } from '../../hooks/useEventos';
import { listarInscritosComContato } from '../../services/inscricoes';
import { extrairMensagemErro } from '../../services/api';
import { useToast } from '../../context/ToastContext';
import { formatarDataHora } from '../../utils/data';
import { baixarCsv } from '../../utils/arquivo';
import type { InscritoEvento } from '../../types';

/**
 * Lista de contato dos inscritos num evento - nome e e-mail de quem está
 * na turma, para a organização avisar mudança de sala, mandar material ou
 * cobrar presença. Sai também em CSV, com as mesmas colunas da tabela.
 *
 * <p>Mostra só quem está inscrito <b>agora</b>: quem cancelou devolveu a
 * vaga e sai da lista no instante do cancelamento (a API filtra por
 * inscrição ATIVA). Não há cópia nem cache no meio do caminho - cada
 * abertura da tela, e cada clique em "Atualizar", é uma consulta ao banco.
 *
 * <p>O evento escolhido vai para a URL (?evento=...), então esta tela dá
 * um link fixo por evento: dá para deixar a lista do Hackathon nos
 * favoritos e cair direto nela. Sem isso, cada visita começaria do zero no
 * "Selecione um evento".
 *
 * <p>Tela de admin de propósito: e-mail de aluno é dado de contato, e a
 * API devolve 403 para qualquer outro perfil. Ver SecurityConfig.
 */
export default function AdminInscritosPage() {
  const { eventos, carregando: carregandoEventos } = useEventos({});
  const { notificarErro, notificarSucesso } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const eventoId = searchParams.get('evento') ?? '';
  const [inscritos, setInscritos] = useState<InscritoEvento[]>([]);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    if (!eventoId) {
      setInscritos([]);
      setErro(null);
      return;
    }
    let cancelado = false;
    setCarregando(true);
    setErro(null);
    listarInscritosComContato(eventoId)
      .then((lista) => {
        if (!cancelado) setInscritos(lista);
      })
      .catch((e) => {
        if (cancelado) return;
        // A lista some junto com o erro: deixar a do evento anterior na
        // tela enquanto o cabecalho ja mostra outro evento faria o admin
        // escrever para a turma errada.
        setInscritos([]);
        setErro(extrairMensagemErro(e, 'Não foi possível carregar os inscritos.'));
      })
      .finally(() => {
        if (!cancelado) setCarregando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [eventoId, recarga]);

  const selecionarEvento = (novoEventoId: string) => {
    // replace: trocar de evento nao e navegacao, e refinar a mesma
    // consulta - senao o "voltar" do navegador percorreria cada evento
    // que o admin espiou antes de sair da tela.
    setSearchParams(novoEventoId ? { evento: novoEventoId } : {}, { replace: true });
  };

  const eventoSelecionado = useMemo(
    () => eventos.find((e) => e.id === eventoId),
    [eventos, eventoId],
  );

  /**
   * As mesmas colunas da tabela, num arquivo que abre no Excel. Baixar
   * em vez de copiar porque a lista costuma virar anexo de e-mail ou
   * planilha de controle da organizacao - copiar so serviria para colar
   * na hora.
   *
   * O nome do arquivo leva o titulo da atividade e a data: o admin baixa
   * a lista de varios eventos, e "inscritos.csv (3)" na pasta de
   * downloads nao diz de quem e cada uma.
   */
  const baixarLista = () => {
    const titulo = (eventoSelecionado?.titulo ?? 'atividade')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .toLowerCase();
    const hoje = new Date().toISOString().slice(0, 10);

    baixarCsv(
      `inscritos-${titulo}-${hoje}.csv`,
      ['Aluno', 'E-mail', 'RGM', 'Inscrito em'],
      inscritos.map((i) => [i.nome, i.email, i.rgm, formatarDataHora(i.inscrito_em)]),
    );
    notificarSucesso(`${inscritos.length} inscrito${inscritos.length === 1 ? '' : 's'} no arquivo.`);
  };

  const copiarEmails = async () => {
    const lista = inscritos.map((i) => i.email).join(', ');
    try {
      await navigator.clipboard.writeText(lista);
      notificarSucesso(`${inscritos.length} e-mail${inscritos.length === 1 ? '' : 's'} copiado${inscritos.length === 1 ? '' : 's'}.`);
    } catch {
      // Sem permissao de area de transferencia (ou pagina sem HTTPS):
      // avisa em vez de falhar calado, que pareceria um botao quebrado.
      notificarErro('O navegador bloqueou a cópia. Selecione os e-mails na tabela.');
    }
  };

  return (
    <PageContainer
      titulo="Inscritos"
      descricao="Nome e e-mail de quem está inscrito em cada atividade - para avisar a turma, mandar material ou cobrar presença"
    >
      <div className="flex flex-col gap-4 rounded-card border border-border bg-surface p-6">
        {carregandoEventos ? (
          <LoadingBlock mensagem="Carregando eventos..." />
        ) : (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <label className="flex flex-1 flex-col gap-1.5 text-sm">
              <span className="font-medium text-text">Atividade</span>
              <select
                className="rounded-lg border border-border bg-surface px-3.5 py-2.5 text-text outline-none focus:border-accent"
                value={eventoId}
                onChange={(e) => selecionarEvento(e.target.value)}
              >
                <option value="">Selecione uma atividade</option>
                {eventos.map((evento) => (
                  <option key={evento.id} value={evento.id}>
                    {evento.titulo}
                  </option>
                ))}
              </select>
            </label>

            {eventoId && (
              <Button variante="outline" onClick={() => setRecarga((n) => n + 1)} disabled={carregando}>
                Atualizar
              </Button>
            )}
          </div>
        )}
      </div>

      {!eventoId && (
        <div className="mt-6">
          <EmptyState
            icone="📋"
            titulo="Escolha uma atividade"
            descricao="A lista mostra quem está inscrito agora - quem cancela sai dela na hora."
          />
        </div>
      )}

      {eventoId && (
        <div className="mt-6 flex flex-col gap-4 rounded-card border border-border bg-surface p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-text">{eventoSelecionado?.titulo ?? 'Atividade'}</h2>
              <p className="text-sm text-text-muted">
                {carregando
                  ? 'Carregando...'
                  : `${inscritos.length} aluno${inscritos.length === 1 ? '' : 's'} inscrito${inscritos.length === 1 ? '' : 's'} agora.`}
              </p>
            </div>
            {!carregando && inscritos.length > 0 && (
              <div className="flex flex-wrap gap-3">
                <Button variante="outline" onClick={copiarEmails}>
                  Copiar e-mails
                </Button>
                <Button onClick={baixarLista}>Baixar lista (CSV)</Button>
              </div>
            )}
          </div>

          {erro && <ErrorMessage mensagem={erro} />}

          {carregando && <LoadingBlock mensagem="Carregando inscritos..." />}

          {!carregando && !erro && inscritos.length === 0 && (
            <EmptyState
              icone="🙋"
              titulo="Ninguém inscrito nesta atividade"
              descricao="Assim que um aluno se inscrever pelo site, ele aparece aqui."
            />
          )}

          {!carregando && !erro && inscritos.length > 0 && (
            <div className="overflow-x-auto rounded-card border border-border">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-text-muted">
                    <th className="px-5 py-3 font-medium">Aluno</th>
                    <th className="px-5 py-3 font-medium">E-mail</th>
                    <th className="px-5 py-3 font-medium">RGM</th>
                    <th className="px-5 py-3 font-medium">Inscrito em</th>
                  </tr>
                </thead>
                <tbody>
                  {inscritos.map((inscrito) => (
                    <tr key={inscrito.aluno_id} className="border-b border-border last:border-0">
                      <td className="px-5 py-3 text-text">{inscrito.nome}</td>
                      <td className="px-5 py-3 text-text-muted">
                        <a className="hover:text-accent" href={`mailto:${inscrito.email}`}>
                          {inscrito.email}
                        </a>
                      </td>
                      <td className="px-5 py-3 text-text-muted">{inscrito.rgm ?? '-'}</td>
                      <td className="px-5 py-3 text-text-muted">{formatarDataHora(inscrito.inscrito_em)}</td>
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
