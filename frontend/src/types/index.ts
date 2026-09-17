// Tipos espelhando exatamente os DTOs JSON do backend (docs/openapi.yaml).
// O backend serializa em snake_case (spring.jackson.property-naming-strategy:
// SNAKE_CASE), por isso os campos aqui usam snake_case, sem camada de
// mapeamento intermediaria.

/**
 * So ADMIN e ALUNO - confirmado com o stakeholder do projeto que esta
 * versao nao precisa de um perfil PROFESSOR separado. O contrato
 * (docs/openapi.yaml) documenta o enum Perfil em minusculas, mas o
 * backend serializa o enum Java como veio (maiusculas) - e isso que a
 * API realmente devolve e espera em NovoUsuario.perfil.
 */
export type Perfil = 'ADMIN' | 'ALUNO';

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  perfil: Perfil;
  rgm: string | null;
  cpf: string | null;
  // Curso do aluno (ex.: "Ciencia da Computacao") - so informativo,
  // confirmado com o professor que nao afeta pontuacao/elegibilidade.
  curso: string | null;
}

export interface NovoUsuario {
  nome: string;
  email: string;
  perfil: Perfil;
  rgm?: string | null;
  cpf?: string | null;
  curso?: string | null;
}

export interface Palestrante {
  id: string;
  nome: string;
  bio: string | null;
}

export interface NovoPalestrante {
  nome: string;
  bio?: string;
}

export interface Evento {
  id: string;
  titulo: string;
  descricao: string | null;
  local: string | null;
  data_hora_inicio: string;
  data_hora_fim: string;
  pontos: number;
  /** Total de vagas. `null` = sem limite. */
  capacidade: number | null;
  /** Vagas ocupadas agora - só inscrições ativas. */
  inscritos: number;
  /** `null` quando não há limite. Vem calculado da API para que nenhuma
   *  tela precise lembrar do caso "sem limite" na hora de subtrair. */
  vagas_restantes: number | null;
  lotado: boolean;
  /** Se ainda dá para se inscrever (ou cancelar): vale até pouco depois
   *  do início do evento (há uma folga para quem chega atrasado). Vem
   *  pronto da API para que nenhuma tela recalcule a regra por conta e
   *  fique fora de sincronia - já mudou duas vezes. */
  inscricoes_abertas: boolean;
  /** Instante exato em que as inscrições fecham. */
  inscricoes_ate: string;
  em_andamento: boolean;
  encerrado: boolean;
  palestrantes: Palestrante[];
}

export interface NovoEvento {
  titulo: string;
  descricao?: string;
  local?: string;
  data_hora_inicio: string;
  data_hora_fim: string;
  pontos: number;
  /** `null` = sem limite de vagas. */
  capacidade?: number | null;
  palestrante_ids?: string[];
}

export type InscricaoStatus = 'ativa' | 'cancelada';

export interface Checkin {
  id: string;
  inscricao_id: string;
  entrada: string;
  saida: string | null;
  percentual_presenca: number | null;
}

export interface Inscricao {
  id: string;
  aluno_id: string;
  evento_id: string;
  status: InscricaoStatus;
  criada_em: string;
  cancelada_em: string | null;
  checkin: Checkin | null;
  certificado_disponivel: boolean;
}

/** Linha do relatório de presença (GET /eventos/{id}/checkins) - "Participantes do Evento". */
export interface EventoCheckin {
  inscricao_id: string;
  aluno_id: string;
  aluno_nome: string;
  aluno_rgm: string | null;
  entrada: string;
  saida: string | null;
  percentual_presenca: number | null;
}

/**
 * Linha da lista de contato dos inscritos num evento
 * (GET /eventos/{id}/inscritos, só admin). Traz a pessoa, não a
 * inscrição: é o que a organização usa para falar com a turma.
 */
export interface InscritoEvento {
  aluno_id: string;
  nome: string;
  email: string;
  /** Nulo só em cadastro anterior à obrigatoriedade do RGM. */
  rgm: string | null;
  inscrito_em: string;
}

// Igual ao Perfil: o enum Java (TipoSessaoCheckin.ENTRADA/.SAIDA) e
// serializado como veio, em maiusculas - a naming strategy SNAKE_CASE
// so afeta nomes de campo, nao valores de enum.
export type TipoSessaoCheckin = 'ENTRADA' | 'SAIDA';

/** Sessão de QR code de check-in/check-out gerada pelo admin pra um
 *  evento - projetada na tela, o aluno confirma a própria presença
 *  escaneando com a câmera do celular (ver CheckinSessaoController).
 *  Dura o horário do evento, não mais 6 horas a partir da criação. */
export interface SessaoCheckin {
  id: string;
  evento_id: string;
  tipo: TipoSessaoCheckin;
  criada_em: string;
  expira_em: string;
}

/** O QR da janela atual. O conteúdo muda a cada janela (um código
 *  rotativo vai embutido no link), então a tela do admin precisa buscar
 *  o próximo em `codigo_expira_em` - é isso que faz um print da tela
 *  mandado no grupo parar de funcionar. */
export interface QrCodeSessao {
  png_base64: string;
  codigo_expira_em: string;
  sessao_expira_em: string;
}

export type EventoPontuacaoStatus = 'concluido' | 'no_show' | 'cancelado';

export interface EventoPontuacaoItem {
  evento_id: string;
  titulo: string;
  pontos: number;
  status: EventoPontuacaoStatus;
}

/** Pontos lançados à mão pelo admin - prêmio de gincana, tipicamente. */
export interface PontuacaoExtra {
  id: string;
  aluno_id: string;
  aluno_nome: string;
  pontos: number;
  motivo: string;
  criado_por_nome: string;
  criado_em: string;
}

/** Pontuação do aluno no WECTI. Sem recorte por semestre - o conceito de
 *  "período" foi removido do sistema (nunca foi validado, e fazia a tela
 *  parar de carregar quando o semestre cadastrado terminava). */
export interface Pontuacao {
  /** O que vale no ranking: eventos + extras. */
  pontos_total: number;
  pontos_eventos: number;
  pontos_extras: number;
  eventos: EventoPontuacaoItem[];
  extras: PontuacaoExtra[];
}

export interface RankingItem {
  /** Empatados dividem a mesma posição (1, 2, 2, 4). */
  posicao: number;
  aluno_id: string;
  aluno_nome: string;
  /** Só vem preenchido para o ADMIN - o aluno não vê o RGM dos colegas. */
  aluno_rgm: string | null;
  aluno_curso: string | null;
  pontos_eventos: number;
  pontos_extras: number;
  pontos_total: number;
  eventos_concluidos: number;
}

export interface Ranking {
  itens: RankingItem[];
}

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface LoginResponse {
  token: string;
  usuario: Usuario;
}

// Cadastro publico ("Primeiro acesso? Crie sua conta") - sem campo
// perfil de proposito: sempre vira ALUNO no backend, nunca escolhido
// pelo cliente (ver CadastroAlunoRequest no backend).
export interface CadastroAlunoRequest {
  nome: string;
  email: string;
  senha: string;
  rgm: string;
  curso?: string;
}

// "Esqueci minha senha" - identidade confirmada com RGM (aluno) ou CPF
// (professor), sem link por email (ver RedefinirSenhaRequest no backend).
export interface RedefinirSenhaRequest {
  email: string;
  identificador: string;
  nova_senha: string;
}

/** Resposta da pagina publica de validacao de certificado
 *  (GET /validar/{codigo}). Sem RGM de proposito - ver
 *  CertificadoValidacaoResponse no backend. */
export interface CertificadoValidacao {
  codigo: string;
  aluno_nome: string;
  evento_titulo: string;
  data_realizacao: string;
  carga_horaria: string;
  emitido_em: string;
}

// Envelope de paginacao (ver PaginaResponse no backend) - usado nas
// listagens que crescem muito, ex.: GET /usuarios.
export interface Pagina<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  total_elementos: number;
  total_paginas: number;
}

export interface ErroResponse {
  timestamp: string;
  status: number;
  erro: string;
  mensagem: string;
  campo?: string | null;
}

/** Claims decodificadas do JWT (ver JwtService no backend). */
export interface JwtPayload {
  sub: string;
  perfil: Perfil;
  iat: number;
  exp: number;
}
