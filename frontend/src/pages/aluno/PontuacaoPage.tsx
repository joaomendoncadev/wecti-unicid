import PageContainer from '../../components/PageContainer';
import Badge from '../../components/Badge';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import EmptyState from '../../components/EmptyState';
import { usePontuacao } from '../../hooks/usePontuacao';
import { formatarDataHora } from '../../utils/data';

export default function PontuacaoPage() {
  const { pontuacao, carregando, erro, recarregar } = usePontuacao();

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
    </PageContainer>
  );
}
