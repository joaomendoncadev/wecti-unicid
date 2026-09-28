import type { Perfil } from '../types';

/**
 * Rota "home" de cada perfil - usada depois do login e como destino de
 * redirecionamento quando alguém tenta acessar uma rota que não pode.
 * `perfil` só é null antes do login carregar; nesse caso cai em
 * /perfil, a única rota que não exige nenhum perfil específico.
 *
 * Importante NÃO simplificar pra um fallback tipo
 * "perfil !== 'ALUNO' ? admin : aluno" - isso já causou um loop infinito
 * de redirecionamento no passado, quando existia um terceiro perfil
 * (Professor) sem rota própria: ele caía em /admin/eventos, que exigia
 * ADMIN, e o ProtectedRoute redirecionava de volta pro mesmo lugar.
 */
export function homeDoPerfil(perfil: Perfil | null): string {
  if (perfil === 'ADMIN') return '/admin/eventos';
  if (perfil === 'ALUNO') return '/eventos';
  return '/perfil';
}

/** Rota que o usuário tentou abrir antes de ser mandado para o login -
 *  guardada pelo ProtectedRoute em `location.state.from`. */
export interface EstadoDeOrigem {
  from?: { pathname: string; search?: string };
}

/**
 * Para onde mandar a pessoa depois de entrar: de volta ao que ela estava
 * tentando abrir, ou para a home do perfil.
 *
 * Existe porque isso quebrou no primeiro dia da WECTI 2026. O aluno
 * escaneia o QR de check-in sem estar logado, cai no login, e dali vai
 * para "Esqueci minha senha" ou "Primeiro acesso" — telas que mandavam
 * todo mundo para /eventos e **jogavam fora o link do check-in**. Ele
 * recuperava a conta e ainda assim tinha de escanear o QR de novo, com
 * a fila andando.
 *
 * O `search` precisa vir junto: é ele que carrega o `?c=` do código
 * rotativo. Sem o parâmetro, a confirmação de presença não acontece.
 */
export function destinoAposEntrar(estado: unknown, perfil: Perfil | null): string {
  const origem = (estado as EstadoDeOrigem | null)?.from;
  if (!origem?.pathname) return homeDoPerfil(perfil);
  return `${origem.pathname}${origem.search ?? ''}`;
}
