import { useEffect, useState } from 'react';
import PageContainer from '../../components/PageContainer';
import Badge from '../../components/Badge';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import EmptyState from '../../components/EmptyState';
import DistribuirPontuacaoModal from './DistribuirPontuacaoModal';
import ComprovanteDistribuicao from './ComprovanteDistribuicao';
import { usePontuacao } from '../../hooks/usePontuacao';
import { useAuth } from '../../context/AuthContext';
import { buscarMinhaDistribuicao } from '../../services/distribuicao';
import { formatarDataHora } from '../../utils/data';
import { ROTULO_TURNO, formatarNota } from '../../utils/distribuicao';
import type { Distribuicao } from '../../types';

export default function PontuacaoPage() {
  const { pontuacao, carregando, erro, recarregar } = usePontuacao();
  const { usuario } = useAuth();

  // A distribuição é carregada junto com a pontuação, e não só ao abrir o
  // modal: o cartão abaixo precisa mostrar o que já foi distribuído
  // assim que a tela abre, senão o aluno não sabe se já fez ou não.
  const [distribuicao, setDistribuicao] = useState<Distribuicao | null>(null);
  const [distribuindo, setDistribuindo] = useState(false);
  const [imprimindo, setImprimindo] = useState(false);

  useEffect(() => {
    buscarMinhaDistribuicao().then(setDistribuicao).catch(() => setDistribuicao(null));
  }, []);

  return (
    <PageContainer titulo="Pontuação" descricao="Seus pontos no WECTI">
      {carregando && <LoadingBlock mensagem="Carregando pontuação..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} onTentarNovamente={recarregar} />}
      {!carregando && !erro && pontuacao && (
        <div className="flex flex-col gap-6">
          <div className="rounded-card border border-border bg-surface p-8 text-center">
            <p className="text-5xl font-extrabold text-accent">{pontuacao.pontos_total}</p>
            <p className="mt-1 text-sm text-text-muted">pontos acumulados</p>
            {/* A quebra só aparece quando há gincana: sem ela, "120 de
                palestras + 0 de gincanas" é ruído. */}
            {pontuacao.pontos_extras !== 0 && (
              <p className="mt-3 text-sm text-text-muted">
                <span className="font-medium text-text">{pontuacao.pontos_eventos}</span> de palestras
                {' + '}
                <span className="font-medium text-text">{pontuacao.pontos_extras}</span> de gincanas
              </p>
            )}
          </div>

          {/* Conversão da pontuação em nota. Só aparece para quem tem o
              que distribuir: um cartão "distribua 0 pontos" seria só
              frustração para quem não pontuou. */}
          {distribuicao && distribuicao.limite > 0 && (
            <div className="flex flex-col gap-4 rounded-card border border-border bg-surface p-6">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 className="text-base font-semibold text-text">Distribuir pontuação</h2>
                  <p className="mt-0.5 text-sm text-text-muted">
                    Troque seus pontos por nota nas matérias que você escolher. Cada{' '}
                    {distribuicao.pontos_por_nota} pontos valem 1,0.
                  </p>
                </div>
                <div className="flex gap-2">
                  {/* O comprovante só existe depois de distribuir tudo:
                      entregar ao professor um papel com pontos sobrando
                      seria entregar uma nota incompleta. */}
                  {distribuicao.completa && (
                    <Button variante="outline" onClick={() => setImprimindo(true)}>
                      Imprimir comprovante
                    </Button>
                  )}
                  <Button onClick={() => setDistribuindo(true)}>
                    {distribuicao.itens.length > 0 ? 'Editar distribuição' : 'Distribuir pontuação'}
                  </Button>
                </div>
              </div>

              {distribuicao.itens.length === 0 ? (
                <p className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-4 py-2.5 text-sm text-amber-300">
                  Você tem {distribuicao.limite} pontos para distribuir — equivalem a{' '}
                  {formatarNota(distribuicao.limite / distribuicao.pontos_por_nota)} de nota.
                </p>
              ) : (
                <>
                  <div className="overflow-x-auto rounded-card border border-border">
                    <table className="w-full min-w-[560px] text-left text-sm">
                      <thead>
                        <tr className="border-b border-border text-text-muted">
                          <th className="px-5 py-3 font-medium">Matéria</th>
                          <th className="px-5 py-3 font-medium">Professor</th>
                          <th className="px-5 py-3 text-right font-medium">Pontos</th>
                          <th className="px-5 py-3 text-right font-medium">Nota</th>
                        </tr>
                      </thead>
                      <tbody>
                        {distribuicao.itens.map((item) => (
                          <tr key={item.disciplina_id} className="border-b border-border last:border-0">
                            <td className="px-5 py-3 text-text">
                              {item.disciplina}
                              <span className="ml-2 text-xs text-text-muted">
                                {item.codigo} · {ROTULO_TURNO[item.turno]}
                              </span>
                            </td>
                            <td className="px-5 py-3 text-text-muted">{item.professor}</td>
                            <td className="px-5 py-3 text-right tabular-nums text-text-muted">
                              {item.pontos}
                            </td>
                            <td className="px-5 py-3 text-right font-semibold tabular-nums text-accent">
                              {formatarNota(item.nota)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {distribuicao.restante > 0 ? (
                    <p className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-4 py-2.5 text-sm text-amber-300">
                      Ainda faltam {distribuicao.restante} pontos para distribuir. Enquanto sobrar
                      ponto, você está deixando nota na mesa.
                    </p>
                  ) : (
                    <p className="text-sm text-text-muted">
                      Total distribuído:{' '}
                      <span className="font-semibold text-text">{distribuicao.distribuido} pontos</span>{' '}
                      = <span className="font-semibold text-accent">
                        {formatarNota(distribuicao.nota_total)}
                      </span>{' '}
                      de nota.
                    </p>
                  )}
                </>
              )}
            </div>
          )}

          {/* Só aparece para quem realmente bateu no teto. Sem este aviso,
              esse aluno soma a tabela abaixo à mão, dá diferente do total
              lá em cima e conclui que o sistema errou a conta dele. */}
          {pontuacao.pontos_eventos_excedente > 0 && (
            <div className="rounded-card border border-amber-500/30 bg-amber-500/5 px-5 py-4">
              <p className="text-sm text-amber-300">
                Você atingiu o limite de {pontuacao.limite_eventos} pontos em palestras.
              </p>
              <p className="mt-1 text-sm text-text-muted">
                Os {pontuacao.pontos_eventos_excedente} pontos acima do limite não entram no total.
                A tabela abaixo mostra o valor cheio de cada palestra. Pontos de gincana são contados
                à parte e não têm esse limite.
              </p>
            </div>
          )}

          {pontuacao.eventos.length === 0 ? (
            <EmptyState titulo="Nenhum evento pontuado ainda" icone="⭐" />
          ) : (
            <div className="overflow-x-auto rounded-card border border-border bg-surface">
              <table className="w-full min-w-[520px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-text-muted">
                    <th className="px-5 py-3 font-medium">Evento</th>
                    <th className="px-5 py-3 font-medium">Status</th>
                    <th className="px-5 py-3 font-medium text-right">Pontos</th>
                  </tr>
                </thead>
                <tbody>
                  {pontuacao.eventos.map((item) => (
                    <tr key={item.evento_id} className="border-b border-border last:border-0">
                      <td className="px-5 py-3 text-text">{item.titulo}</td>
                      <td className="px-5 py-3">
                        <Badge status={item.status} />
                      </td>
                      <td
                        className={`px-5 py-3 text-right font-semibold ${
                          item.pontos > 0 ? 'text-accent' : item.pontos < 0 ? 'text-red-400' : 'text-text-muted'
                        }`}
                      >
                        {item.pontos > 0 ? `+${item.pontos}` : item.pontos}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {pontuacao.extras.length > 0 && (
            <div className="rounded-card border border-border bg-surface">
              <div className="border-b border-border px-5 py-4">
                <h2 className="text-base font-semibold text-text">Pontos de gincana</h2>
                <p className="text-sm text-text-muted">
                  Lançados pela organização durante as palestras.
                </p>
              </div>
              <ul>
                {pontuacao.extras.map((extra) => (
                  <li
                    key={extra.id}
                    className="flex items-start justify-between gap-4 border-b border-border px-5 py-3 last:border-0"
                  >
                    <div className="min-w-0">
                      <p className="text-sm text-text">{extra.motivo}</p>
                      <p className="text-xs text-text-muted">{formatarDataHora(extra.criado_em)}</p>
                    </div>
                    <span
                      className={`shrink-0 font-semibold ${extra.pontos < 0 ? 'text-red-400' : 'text-accent'}`}
                    >
                      {extra.pontos > 0 ? `+${extra.pontos}` : extra.pontos}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {distribuindo && distribuicao && (
        <DistribuirPontuacaoModal
          distribuicao={distribuicao}
          onFechar={() => setDistribuindo(false)}
          onSalvo={(nova) => {
            setDistribuicao(nova);
            setDistribuindo(false);
          }}
        />
      )}

      {imprimindo && distribuicao && usuario && (
        <ComprovanteDistribuicao
          distribuicao={distribuicao}
          aluno={usuario}
          onFechar={() => setImprimindo(false)}
        />
      )}
    </PageContainer>
  );
}
