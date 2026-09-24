/*
 * Ponto e vírgula, e não vírgula, como separador: no Excel em português
 * a vírgula é separador decimal, e um .csv separado por vírgula abre com
 * tudo empilhado numa coluna só. O BOM no começo é o que faz o Excel
 * reconhecer o arquivo como UTF-8 - sem ele, "João" vira "JoÃ£o".
 */
const SEPARADOR = ';';
const BOM_UTF8 = '﻿';

function escapar(valor: string) {
  // Aspas, separador ou quebra de linha dentro de um campo confundem o
  // leitor: a saída é envolver em aspas e dobrar as aspas internas.
  const precisaAspas = /["\n\r;]/.test(valor);
  const escapado = valor.replace(/"/g, '""');
  return precisaAspas ? `"${escapado}"` : escapado;
}

/**
 * Monta um CSV (cabeçalho + linhas) e baixa como arquivo.
 *
 * Campo vazio vira string vazia em vez de "null"/"undefined", que
 * apareceriam como texto na planilha.
 */
export function baixarCsv(nomeArquivo: string, cabecalho: string[], linhas: (string | null | undefined)[][]) {
  const conteudo = [cabecalho, ...linhas]
    .map((linha) => linha.map((celula) => escapar(celula ?? '')).join(SEPARADOR))
    .join('\r\n');

  salvarArquivo(new Blob([BOM_UTF8 + conteudo], { type: 'text/csv;charset=utf-8' }), nomeArquivo);
}

/** Dispara o download de um Blob no navegador com o nome de arquivo dado. */
export function salvarArquivo(blob: Blob, nomeArquivo: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
