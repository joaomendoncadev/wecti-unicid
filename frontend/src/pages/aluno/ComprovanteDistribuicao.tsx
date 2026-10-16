import { useEffect } from 'react';
import Button from '../../components/Button';
import { ROTULO_TURNO, formatarNota } from '../../utils/distribuicao';
import type { Distribuicao, Usuario } from '../../types';

interface Props {
  distribuicao: Distribuicao;
  aluno: Usuario;
  onFechar: () => void;
}

/**
 * Comprovante que o aluno imprime e entrega ao professor.
 *
 * Impressão por CSS (`@media print`) em vez de gerar PDF no servidor: o
 * conteúdo já está todo no navegador, e o aluno pode tanto imprimir
 * quanto "salvar como PDF" pelo próprio diálogo. Um endpoint de PDF
 * duplicaria a formatação em dois lugares para o mesmo resultado.
 *
 * A folha some das telas (`print:hidden` nos controles) e o papel sai
 * sempre claro - o tema escuro do site gastaria tinta e sairia ilegível
 * em impressora monocromática.
 */
export default function ComprovanteDistribuicao({ distribuicao, aluno, onFechar }: Props) {
  // Esc fecha: a folha ocupa a tela inteira e o botão pode ficar fora de
  // vista depois de rolar uma lista longa.
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onFechar();
    };
    window.addEventListener('keydown', aoTeclar);
    return () => window.removeEventListener('keydown', aoTeclar);
  }, [onFechar]);

  const emitidoEm = new Date().toLocaleString('pt-BR');

  return (
    <div className="fixed inset-0 z-[300] overflow-y-auto bg-black/80 p-4 print:static print:bg-white print:p-0">
      <div className="mx-auto max-w-3xl">
        <div className="mb-4 flex justify-end gap-2 print:hidden">
          <Button variante="outline" onClick={onFechar}>
            Fechar
          </Button>
          <Button onClick={() => window.print()}>Imprimir</Button>
        </div>

        {/* Cores fixas, não as do tema: o comprovante é papel. */}
        <div
          id="comprovante-distribuicao"
          className="rounded-card bg-white p-10 text-[#111] print:rounded-none print:p-0"
        >
          <div className="mb-6 border-b border-[#ccc] pb-4">
            <h1 className="text-xl font-bold">WECTI — Distribuição de pontuação em nota</h1>
            <p className="mt-1 text-sm text-[#555]">
              Universidade Cidade de São Paulo · emitido em {emitidoEm}
            </p>
          </div>

          <div className="mb-6 grid grid-cols-2 gap-x-8 gap-y-2 text-sm">
            <p>
              <span className="text-[#555]">Aluno:</span>{' '}
              <span className="font-semibold">{aluno.nome}</span>
            </p>
            <p>
              <span className="text-[#555]">RGM:</span>{' '}
              <span className="font-semibold tabular-nums">{aluno.rgm ?? '-'}</span>
            </p>
            <p>
              <span className="text-[#555]">Pontos distribuídos:</span>{' '}
              <span className="font-semibold tabular-nums">{distribuicao.distribuido}</span>
            </p>
            <p>
              <span className="text-[#555]">Nota total:</span>{' '}
              <span className="font-semibold tabular-nums">
                {formatarNota(distribuicao.nota_total)}
              </span>
            </p>
          </div>

          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b-2 border-[#111]">
                <th className="py-2 pr-3 font-semibold">Cód.</th>
                <th className="py-2 pr-3 font-semibold">Disciplina</th>
                <th className="py-2 pr-3 font-semibold">Período</th>
                <th className="py-2 pr-3 font-semibold">Professor</th>
                <th className="py-2 pr-3 text-right font-semibold">Pontos</th>
                <th className="py-2 text-right font-semibold">Nota</th>
              </tr>
            </thead>
            <tbody>
              {distribuicao.itens.map((item) => (
                <tr key={item.disciplina_id} className="border-b border-[#ddd]">
                  <td className="py-2 pr-3 tabular-nums">{item.codigo}</td>
                  <td className="py-2 pr-3">{item.disciplina}</td>
                  <td className="py-2 pr-3">
                    {ROTULO_TURNO[item.turno]} · {item.dia_semana}
                  </td>
                  <td className="py-2 pr-3">{item.professor}</td>
                  <td className="py-2 pr-3 text-right tabular-nums">{item.pontos}</td>
                  <td className="py-2 text-right font-semibold tabular-nums">
                    {formatarNota(item.nota)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-[#111] font-bold">
                <td className="py-2 pr-3" colSpan={4}>
                  Total
                </td>
                <td className="py-2 pr-3 text-right tabular-nums">{distribuicao.distribuido}</td>
                <td className="py-2 text-right tabular-nums">
                  {formatarNota(distribuicao.nota_total)}
                </td>
              </tr>
            </tfoot>
          </table>

          <div className="mt-10 grid grid-cols-2 gap-10 text-sm">
            <div className="border-t border-[#111] pt-2 text-center text-[#555]">
              Assinatura do aluno
            </div>
            <div className="border-t border-[#111] pt-2 text-center text-[#555]">
              Assinatura do professor
            </div>
          </div>

          <p className="mt-6 text-xs text-[#777]">
            Conversão: {distribuicao.pontos_por_nota} pontos equivalem a 1,0 de nota. Documento gerado
            pelo sistema WECTI a partir da pontuação registrada no evento.
          </p>
        </div>
      </div>
    </div>
  );
}
