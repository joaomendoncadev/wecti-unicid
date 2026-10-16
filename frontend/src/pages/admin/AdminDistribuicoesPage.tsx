import { useEffect, useMemo, useState } from 'react';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import EmptyState from '../../components/EmptyState';
import { listarDistribuicoes } from '../../services/distribuicao';
import { extrairMensagemErro } from '../../services/api';
import { ROTULO_TURNO, formatarNota } from '../../utils/distribuicao';
import type { DistribuicaoDoAluno, TurnoDisciplina } from '../../types';

type FiltroTurno = TurnoDisciplina | 'TODOS';

/**
 * Como cada aluno converteu a pontuação do WECTI em nota.
 *
 * É a tela que a coordenação consulta para lançar as notas: mostra RGM,
 * disciplina, professor, período e a nota de cada linha - os campos que o
 * professor precisa para registrar no diário.
 *
 * Uma linha por (aluno, turma), e não agrupado por aluno na tabela: o uso
 * real é procurar "quem me deu nota" por professor ou por disciplina, e
 * para isso a tabela plana filtra e ordena melhor. O total por aluno
 * continua visível na coluna da esquerda.
 */
export default function AdminDistribuicoesPage() {
  const [distribuicoes, setDistribuicoes] = useState<DistribuicaoDoAluno[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const [busca, setBusca] = useState('');
  const [turno, setTurno] = useState<FiltroTurno>('TODOS');

  const carregar = () => {
    setCarregando(true);
    listarDistribuicoes()
      .then((lista) => {
        setDistribuicoes(lista);
        setErro(null);
      })
      .catch((e) => setErro(extrairMensagemErro(e, 'Não foi possível carregar as distribuições.')))
      .finally(() => setCarregando(false));
  };

  useEffect(carregar, []);

  // Achata para uma linha por (aluno, turma) - é assim que a coordenação
  // lê, procurando pelo professor ou pela disciplina.
  const linhas = useMemo(
    () =>
      distribuicoes.flatMap((aluno) =>
        aluno.itens.map((item) => ({
          chave: `${aluno.aluno_rgm}-${item.disciplina_id}`,
          alunoNome: aluno.aluno_nome,
          alunoRgm: aluno.aluno_rgm,
          totalAluno: aluno.total_pontos,
          ...item,
        })),
      ),
    [distribuicoes],
  );

  const filtradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    return linhas.filter((l) => {
      if (turno !== 'TODOS' && l.turno !== turno) return false;
      if (!termo) return true;
      return (
        l.alunoNome.toLowerCase().includes(termo) ||
        (l.alunoRgm ?? '').includes(termo) ||
        l.disciplina.toLowerCase().includes(termo) ||
        l.codigo.includes(termo) ||
        l.professor.toLowerCase().includes(termo)
      );
    });
  }, [linhas, busca, turno]);

  const totalPontos = filtradas.reduce((soma, l) => soma + l.pontos, 0);

  return (
    <PageContainer
      titulo="Distribuição de pontuação"
      descricao="Como cada aluno converteu os pontos do WECTI em nota"
      acao={
        <Button variante="outline" onClick={carregar} carregando={carregando}>
          Atualizar
        </Button>
      }
    >
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          className="flex-1 rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-text outline-none focus:border-accent"
          placeholder="Buscar por aluno, RGM, disciplina, código ou professor"
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

      {carregando && <LoadingBlock mensagem="Carregando distribuições..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} onTentarNovamente={carregar} />}

      {!carregando && !erro && linhas.length === 0 && (
        <EmptyState
          titulo="Nenhum aluno distribuiu pontuação ainda"
          descricao="Assim que um aluno converter os pontos dele em nota, a distribuição aparece aqui."
        />
      )}

      {!carregando && !erro && linhas.length > 0 && (
        <>
          <p className="mb-3 text-sm text-text-muted">
            {filtradas.length} lançamento{filtradas.length === 1 ? '' : 's'} ·{' '}
            {new Set(filtradas.map((l) => l.alunoRgm)).size} aluno(s) ·{' '}
            <span className="font-medium text-text">{totalPontos} pontos</span> no filtro atual
          </p>

          <div className="overflow-x-auto rounded-card border border-border bg-surface">
            <table className="w-full min-w-[920px] text-left text-sm">
              <thead>
                <tr className="border-b border-border text-text-muted">
                  <th className="px-5 py-3 font-medium">Aluno</th>
                  <th className="px-5 py-3 font-medium">RGM</th>
                  <th className="px-5 py-3 font-medium">Cód.</th>
                  <th className="px-5 py-3 font-medium">Disciplina</th>
                  <th className="px-5 py-3 font-medium">Professor</th>
                  <th className="px-5 py-3 font-medium">Período</th>
                  <th className="px-5 py-3 text-right font-medium">Pontos</th>
                  <th className="px-5 py-3 text-right font-medium">Nota</th>
                </tr>
              </thead>
              <tbody>
                {filtradas.map((l) => (
                  <tr key={l.chave} className="border-b border-border last:border-0">
                    <td className="px-5 py-3 text-text">{l.alunoNome}</td>
                    <td className="px-5 py-3 tabular-nums text-text-muted">{l.alunoRgm ?? '-'}</td>
                    <td className="px-5 py-3 tabular-nums text-text-muted">{l.codigo}</td>
                    <td className="px-5 py-3 text-text">{l.disciplina}</td>
                    <td className="px-5 py-3 text-text-muted">{l.professor}</td>
                    <td className="px-5 py-3 text-text-muted">
                      {ROTULO_TURNO[l.turno]} · {l.dia_semana}
                    </td>
                    <td className="px-5 py-3 text-right tabular-nums text-text-muted">{l.pontos}</td>
                    <td className="px-5 py-3 text-right font-semibold tabular-nums text-accent">
                      {formatarNota(l.nota)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </PageContainer>
  );
}
