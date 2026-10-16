import { useEffect, useMemo, useState } from 'react';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import { listarDisciplinas, salvarMinhaDistribuicao } from '../../services/distribuicao';
import { extrairMensagemErro } from '../../services/api';
import { ROTULO_TURNO, formatarNota } from '../../utils/distribuicao';
import type { Disciplina, Distribuicao, TurnoDisciplina } from '../../types';

interface Props {
  distribuicao: Distribuicao;
  onFechar: () => void;
  onSalvo: (nova: Distribuicao) => void;
}

type FiltroTurno = TurnoDisciplina | 'TODOS';

/** Espera antes de buscar enquanto o aluno ainda digita. */
const ESPERA_BUSCA_MS = 300;

/**
 * O aluno reparte a pontuação do WECTI entre as turmas em que estuda.
 *
 * **O estado dos pontos vive aqui, não no servidor, enquanto ele edita.**
 * Trocar de filtro não pode perder o que já foi digitado nas turmas que
 * saíram da lista - por isso `pontos` é um mapa por id de turma, mantido
 * inteiro, e a lista filtrada só decide o que aparece.
 *
 * O "restante" é recalculado a cada tecla sobre esse mapa, e não a partir
 * da resposta do servidor: o aluno precisa ver o saldo mudar enquanto
 * digita, não depois de salvar.
 */
export default function DistribuirPontuacaoModal({ distribuicao, onFechar, onSalvo }: Props) {
  const [disciplinas, setDisciplinas] = useState<Disciplina[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erroCatalogo, setErroCatalogo] = useState<string | null>(null);

  const [turno, setTurno] = useState<FiltroTurno>('TODOS');
  const [busca, setBusca] = useState('');

  // Mapa id-da-turma -> pontos, semeado com o que já estava salvo.
  const [pontos, setPontos] = useState<Record<string, number>>(() =>
    Object.fromEntries(distribuicao.itens.map((i) => [i.disciplina_id, i.pontos])),
  );
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    setCarregando(true);
    const timer = setTimeout(() => {
      listarDisciplinas({
        turno: turno === 'TODOS' ? undefined : turno,
        busca: busca.trim() || undefined,
      })
        .then((lista) => {
          if (!cancelado) {
            setDisciplinas(lista);
            setErroCatalogo(null);
          }
        })
        .catch((e) => {
          if (!cancelado) setErroCatalogo(extrairMensagemErro(e, 'Não foi possível carregar as matérias.'));
        })
        .finally(() => {
          if (!cancelado) setCarregando(false);
        });
    }, ESPERA_BUSCA_MS);

    return () => {
      cancelado = true;
      clearTimeout(timer);
    };
  }, [turno, busca]);

  const distribuido = useMemo(
    () => Object.values(pontos).reduce((soma, p) => soma + (p || 0), 0),
    [pontos],
  );
  const restante = distribuicao.limite - distribuido;
  const excedeu = restante < 0;
  const completa = restante === 0 && distribuicao.limite > 0;

  const alterar = (id: string, valor: string) => {
    const numero = valor === '' ? 0 : Number(valor);
    if (!Number.isFinite(numero) || numero < 0) return;
    setPontos((atual) => ({ ...atual, [id]: Math.floor(numero) }));
    setErro(null);
  };

  /** Joga tudo o que sobrou numa turma só - atalho para quem vai
   *  concentrar a nota numa matéria, que é o caso mais comum. */
  const usarRestante = (id: string) => {
    if (restante <= 0) return;
    setPontos((atual) => ({ ...atual, [id]: (atual[id] || 0) + restante }));
    setErro(null);
  };

  const salvar = async () => {
    setErro(null);
    if (excedeu) {
      setErro(`Você distribuiu ${distribuido} pontos, mas só tem ${distribuicao.limite}.`);
      return;
    }
    setSalvando(true);
    try {
      const itens = Object.entries(pontos)
        .filter(([, p]) => p > 0)
        .map(([disciplina_id, p]) => ({ disciplina_id, pontos: p }));
      onSalvo(await salvarMinhaDistribuicao(itens));
    } catch (e) {
      setErro(extrairMensagemErro(e, 'Não foi possível salvar a distribuição.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 p-4">
      <div className="flex max-h-[92vh] w-full max-w-4xl flex-col rounded-card border border-border bg-surface">
        <div className="flex flex-col gap-4 border-b border-border p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-text">Distribuir pontuação</h2>
              <p className="text-sm text-text-muted">
                Escolha em quais matérias seus pontos viram nota. Cada{' '}
                {distribuicao.pontos_por_nota} pontos valem 1,0.
              </p>
            </div>
            <Button variante="outline" onClick={onFechar} className="shrink-0">
              Fechar
            </Button>
          </div>

          {/* O saldo é o número que o aluno olha o tempo todo, então fica
              fixo no topo e muda a cada tecla - não só depois de salvar. */}
          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-lg border border-border px-4 py-3 text-center">
              <p className="text-xs text-text-muted">Disponível</p>
              <p className="text-xl font-bold text-text tabular-nums">{distribuicao.limite}</p>
            </div>
            <div className="rounded-lg border border-border px-4 py-3 text-center">
              <p className="text-xs text-text-muted">Distribuído</p>
              <p className="text-xl font-bold text-accent tabular-nums">{distribuido}</p>
            </div>
            <div
              className={`rounded-lg border px-4 py-3 text-center ${
                excedeu
                  ? 'border-red-500/40 bg-red-500/5'
                  : completa
                    ? 'border-accent/40 bg-accent/5'
                    : 'border-amber-500/40 bg-amber-500/5'
              }`}
            >
              <p className="text-xs text-text-muted">{excedeu ? 'Excedeu' : 'Falta distribuir'}</p>
              <p
                className={`text-xl font-bold tabular-nums ${
                  excedeu ? 'text-red-400' : completa ? 'text-accent' : 'text-amber-300'
                }`}
              >
                {Math.abs(restante)}
              </p>
            </div>
          </div>

          {excedeu && (
            <p className="rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-2.5 text-sm text-red-400">
              Você passou do que tem disponível. Tire {Math.abs(restante)} pontos de alguma matéria.
            </p>
          )}
          {completa && (
            <p className="rounded-lg border border-accent/30 bg-accent/5 px-4 py-2.5 text-sm text-accent">
              Tudo distribuído — equivale a {formatarNota(distribuido / distribuicao.pontos_por_nota)} de
              nota. Salve para gerar o comprovante.
            </p>
          )}

          <div className="flex flex-col gap-3 sm:flex-row">
            <input
              className="flex-1 rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-text outline-none focus:border-accent"
              placeholder="Buscar por matéria, código ou professor"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
            <div className="flex gap-1.5">
              {(['TODOS', 'MANHA', 'NOITE'] as FiltroTurno[]).map((opcao) => (
                <button
                  key={opcao}
                  type="button"
                  onClick={() => setTurno(opcao)}
                  className={`rounded-lg border px-4 py-2 text-sm transition-colors ${
                    turno === opcao
                      ? 'border-accent bg-accent/10 text-accent'
                      : 'border-border text-text-muted hover:border-accent/50'
                  }`}
                >
                  {opcao === 'TODOS' ? 'Todos' : ROTULO_TURNO[opcao]}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {carregando && <LoadingBlock mensagem="Carregando matérias..." />}

          {!carregando && erroCatalogo && (
            <p className="rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-2.5 text-sm text-red-400">
              {erroCatalogo}
            </p>
          )}

          {!carregando && !erroCatalogo && disciplinas.length === 0 && (
            <p className="py-8 text-center text-sm text-text-muted">
              Nenhuma matéria encontrada com esse filtro.
            </p>
          )}

          {!carregando && disciplinas.length > 0 && (
            <div className="overflow-x-auto rounded-card border border-border">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-text-muted">
                    <th className="px-4 py-3 font-medium">Cód.</th>
                    <th className="px-4 py-3 font-medium">Matéria</th>
                    <th className="px-4 py-3 font-medium">Turno / Dia</th>
                    <th className="px-4 py-3 font-medium">Professor</th>
                    <th className="px-4 py-3 text-right font-medium">Pontos</th>
                    <th className="px-4 py-3 text-right font-medium">Nota</th>
                  </tr>
                </thead>
                <tbody>
                  {disciplinas.map((d) => {
                    const valor = pontos[d.id] || 0;
                    return (
                      <tr
                        key={d.id}
                        className={`border-b border-border last:border-0 ${
                          valor > 0 ? 'bg-accent/5' : ''
                        }`}
                      >
                        <td className="px-4 py-3 text-text-muted tabular-nums">{d.codigo}</td>
                        <td className="px-4 py-3 text-text">{d.nome}</td>
                        <td className="px-4 py-3 text-text-muted">
                          {ROTULO_TURNO[d.turno]} · {d.dia_semana}
                        </td>
                        <td className="px-4 py-3 text-text-muted">{d.professor}</td>
                        <td className="px-4 py-2 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            {/* Atalho para o caso mais comum: concentrar
                                o que sobrou numa matéria só. */}
                            {restante > 0 && (
                              <button
                                type="button"
                                onClick={() => usarRestante(d.id)}
                                title={`Jogar os ${restante} pontos restantes aqui`}
                                className="rounded border border-border px-2 py-1 text-xs text-text-muted transition-colors hover:border-accent hover:text-accent"
                              >
                                +{restante}
                              </button>
                            )}
                            <input
                              type="number"
                              min={0}
                              inputMode="numeric"
                              value={valor === 0 ? '' : valor}
                              onChange={(e) => alterar(d.id, e.target.value)}
                              placeholder="0"
                              className="w-24 rounded-lg border border-border bg-surface px-2.5 py-1.5 text-right text-sm tabular-nums text-text outline-none focus:border-accent"
                            />
                          </div>
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-text-muted">
                          {valor > 0 ? formatarNota(valor / distribuicao.pontos_por_nota) : '-'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border p-6">
          <p className="text-xs text-text-muted">
            Você pode salvar parcial e voltar depois. O comprovante sai quando distribuir tudo.
          </p>
          {erro && (
            <p className="w-full rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-sm text-red-400">
              {erro}
            </p>
          )}
          <div className="flex gap-2">
            <Button variante="outline" onClick={onFechar}>
              Cancelar
            </Button>
            <Button onClick={salvar} carregando={salvando} disabled={excedeu}>
              Salvar distribuição
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
