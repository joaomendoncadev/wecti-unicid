/**
 * Geração de CSV que abre certo no Excel em português.
 *
 * Três detalhes decidem isso, e errar qualquer um deles entrega um
 * arquivo que o professor não consegue usar:
 *
 * 1. **BOM UTF-8** (`﻿`) no começo. Sem ele, o Excel do Windows lê
 *    o arquivo como ANSI e "ENGENHARIA DE SOFTWARE · TERÇA-FEIRA" vira
 *    "TERÃ‡A-FEIRA". O Excel só reconhece UTF-8 em CSV quando o BOM está
 *    lá.
 * 2. **Ponto e vírgula** como separador. Na configuração brasileira do
 *    Windows, o Excel espera `;` - com vírgula, ele joga a linha inteira
 *    numa célula só e o professor precisa usar "Texto para colunas".
 * 3. **Vírgula decimal** nos números (feito por quem monta as linhas).
 *    Com ponto, o Excel pt-BR lê "0.9" como texto, não como nota, e a
 *    coluna não soma.
 *
 * Geração no navegador, e não no servidor: os dados já estão todos na
 * tela, filtrados. Um endpoint de exportação teria de reimplementar os
 * mesmos filtros no backend para chegar ao mesmo resultado.
 */

const SEPARADOR = ';';
const BOM = '﻿';

/**
 * Escapa um valor para uma célula.
 *
 * Aspas, ponto e vírgula e quebra de linha quebrariam a estrutura do
 * arquivo - nome de disciplina e de professor são texto livre, então
 * nada impede que apareçam.
 */
function celula(valor: string | number | null | undefined): string {
  if (valor === null || valor === undefined) return '';
  const texto = String(valor);
  if (texto.includes('"') || texto.includes(SEPARADOR) || /[\r\n]/.test(texto)) {
    return `"${texto.replace(/"/g, '""')}"`;
  }
  return texto;
}

/** Número no formato que o Excel pt-BR entende como número. */
export function numeroCsv(valor: number): string {
  return String(valor).replace('.', ',');
}

/**
 * Transforma um texto em pedaço seguro de nome de arquivo.
 *
 * Acento, barra e dois-pontos causam problema em nome de arquivo no
 * Windows, e os pedaços vêm de texto livre (busca do admin) ou de
 * rótulos de tela ("Manhã"). Sem isto, o download saía como
 * "wecti-notas-manhã-....csv".
 */
export function trechoDeNomeDeArquivo(texto: string, limite = 30): string {
  return texto
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, limite);
}

export function montarCsv(
  cabecalho: string[],
  linhas: Array<Array<string | number | null | undefined>>,
): string {
  // \r\n: é o que o Excel espera como fim de linha no Windows.
  return (
    BOM +
    [cabecalho, ...linhas].map((linha) => linha.map(celula).join(SEPARADOR)).join('\r\n')
  );
}

/**
 * Entrega o arquivo ao navegador.
 *
 * `revokeObjectURL` depois do clique: sem isso o blob fica preso na
 * memória da aba até ela ser fechada, e a tela do admin costuma ficar
 * aberta o dia todo.
 */
export function baixarCsv(nomeArquivo: string, conteudo: string): void {
  const blob = new Blob([conteudo], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
