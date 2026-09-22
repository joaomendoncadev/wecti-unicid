# CLAUDE.md - Convenções do projeto WECTI

Este arquivo dá contexto para qualquer sessão de Claude Code (ou para
qualquer pessoa do time) trabalhando neste repositório. Leia antes de
gerar código novo.

## O que é o WECTI

Sistema de controle de acesso a palestras e eventos acadêmicos. Tem três
clientes, mas só uma fonte de verdade:

- **Backend (API REST)** - pasta `backend/` deste repositório. É o único
  que fala com o banco de dados. Ninguém mais acessa o MySQL direto.
- **Web do aluno** - autoatendimento (inscrição, histórico, certificado,
  cancelamento, check-in/check-out via QR code lido pela câmera do
  celular). Consome a API, não acessa o banco.
- **Web do admin** - cadastra eventos e usuários, gera o QR code de
  check-in/check-out de cada evento (projetado na tela pro aluno
  escanear com a câmera do próprio celular - sem app dedicado).

O contrato de API (fonte de verdade dos endpoints) está em
`docs/openapi.yaml`. Qualquer endpoint novo ou alterado deve ser refletido
lá primeiro, antes de implementar.

## Perfis de usuário

`admin`, `aluno` (enum `Perfil`) são os únicos perfis ativos nesta
versão - confirmado com o professor (stakeholder do projeto): não existe
perfil "operador", e o perfil `professor` (que existia numa versão
anterior) não é mais oferecido no cadastro nem tem tela própria. Ele
continua existindo no enum só por compatibilidade com cadastros que já
existiam no banco antes dessa mudança - uma conta assim ainda consegue
logar, mas não tem acesso a nada além do que qualquer usuário autenticado
tem (não é redirecionada num loop - ver `homeDoPerfil` no frontend e o
comentário em `SecurityConfig.java`). Não inventar volta desse perfil sem
confirmar de novo.

- **Admin**: cadastra eventos e usuários (só Aluno ou Admin) e edita
  qualquer cadastro, com as travas descritas abaixo; gera os QR codes de
  check-in/check-out; acompanha a lista de inscritos (nome e e-mail) e
  de presença; lança pontos de gincana; é o único que vê o ranking.
- **Aluno**: se autocadastra (nome, RGM, email, senha e curso opcional -
  ver `CadastroAlunoRequest`) e depois pode corrigir o próprio cadastro;
  escolhe os eventos em que participa, confirma a própria presença
  escaneando o QR code, acompanha **a própria** pontuação (não a
  classificação da turma) e emite certificado.

## Regras de negócio já fechadas (não inventar alternativa)

- **Inscrição e cancelamento fecham juntos, 15 minutos depois do início
  do evento.** Até lá o aluno pode entrar e sair livremente; a partir daí,
  nem uma coisa nem outra. Quem ficou inscrito e não apareceu conta como
  no-show.

  A folga de 15 minutos é para quem chega atrasado: sem ela, quem aparece
  5 minutos depois do começo não se inscreve, logo não faz check-in, logo
  não pontua — ficaria de fora de uma palestra em que está presente.
  Configurável em `app.inscricao.tolerancia-apos-inicio-minutos`, mas
  **não passe de 30 minutos**: a pontuação exige 75% de permanência, e
  numa palestra de 2 horas quem entra 30 min atrasado fica com exatamente
  90 min de 120 — 30 é o limite. Uma folga maior deixaria o aluno se
  inscrever numa palestra em que já não tem como alcançar a presença
  mínima, o que é pior do que não deixar inscrever: ele acha que vai
  pontuar e não pontua.

  > Antes o cancelamento era "até 1 dia antes" e a inscrição ia até o
  > **fim** do evento — dois prazos diferentes, e nenhum deles fazia
  > sentido. O de 1 dia tornava impossível desistir de uma palestra
  > marcada para o mesmo dia; o outro deixava alguém se inscrever no meio
  > da palestra (ou depois dela, levando no-show na hora). O professor
  > pediu a mudança ao testar o sistema no ar, em setembro de 2026, e
  > pediu a folga para retardatários logo em seguida.
  >
  > Fechando os dois no mesmo instante, o incentivo fica certo: quem não
  > vai mais, cancela e devolve a vaga; quem não cancelar, perde os
  > pontos. A regra vive em `PrazoInscricao` — um lugar só, usado pela
  > inscrição, pelo cancelamento e pelo que a tela mostra.
- **Janela do QR de check-in: de 1 hora antes do início até 30 minutos
  depois do fim** do evento (`app.checkin.tolerancia-antes-minutos` = 60,
  `app.checkin.tolerancia-depois-minutos` = 30). Eram 30 min antes; o
  professor pediu 1h (setembro de 2026) para poder montar a sala e
  projetar o QR sem correria. A janela só diz **quando dá para escanear**
  — não é presença (ver o recorte no item do certificado).

  Em produção quem manda é a variável de ambiente
  (`CHECKIN_TOLERANCIA_ANTES_MINUTOS`): se ela estiver definida no painel
  da hospedagem, vence o padrão do código e também o de
  `deploy/compose.prod.yml`. Ao mudar esse padrão, mude nos três lugares
  (`application.yml`, `compose.prod.yml`, `docs/producao.env.exemplo`) e
  confira o painel.
- **No-show**: penaliza apenas quem NÃO cancelou E NÃO fez check-in.
- **Certificado**: exige check-in + check-out + permanência >= 75% da
  duração do evento (`data_hora_fim - data_hora_inicio`). Ver o método
  `Checkin.isPresencaQualificada(...)`.

  **Só conta o tempo dentro do horário do evento.** O check-in abre 1h
  antes e fecha 30min depois (`app.checkin.tolerancia-*`), então entrada
  e saída podem cair fora da palestra — e essa folga não é presença. A
  conta é a **interseção** entre `[entrada, saida]` e
  `[dataHoraInicio, dataHoraFim]`. Sem o recorte, quem chegasse na metade
  e só marcasse a saída no corredor somava os dois pedaços de folga e
  passava nos 75% sem ter assistido. Quem fica do começo ao fim não é
  afetado.
- **Pontuação**: cada evento tem um valor fixo de pontos
  (`Evento.pontos`), definido no cadastro do evento. Só conta para o
  aluno quando ele cumpre o mesmo critério do certificado - não basta o
  check-in.
- **Penalidade**: o no-show desconta um valor **fixo**, configurável em
  `app.pontuacao.penalidade-no-show` (padrão **100**) - e não os pontos
  que aquele evento valeria.

  > Era assim até setembro de 2026: faltar numa palestra de 50 pontos
  > custava 50, e numa de 300 custava 300. Quem pegava vaga na palestra
  > mais concorrida e não aparecia era quem mais perdia, embora o
  > desperdício (uma vaga vazia) seja o mesmo nos dois casos.
- **Teto de pontos de evento**: o aluno ganha no máximo
  `app.pontuacao.limite-eventos` (padrão **2000**) em palestras.

  O teto apara **apenas os ganhos**; a penalidade de no-show é descontada
  **depois** dele. Nessa ordem, quem já passou do teto continua sentindo a
  falta - se o teto fosse aplicado sobre o saldo, a penalidade sumiria
  junto com o excedente. Exemplo: 2300 ganhos + 1 falta →
  `min(2300, 2000) - 100 = 1900`.

  **Gincana fica fora do teto** (é premiação lançada à mão pelo admin,
  não pontuação de palestra). A conta inteira vive em `RegraPontuacao`,
  usada pela tela individual e pelo ranking - um número diferente nos dois
  lugares derrubaria a confiança na competição.
- **Ranking: só admin.** O aluno vê a própria pontuação (`/me/pontuacao`),
  não a classificação da turma. `GET /ranking` exige ADMIN no
  `SecurityConfig` - a restrição é do backend, não só do menu.
- **Cada pessoa corrige o próprio cadastro** em `PUT /usuarios/me`, os
  dois perfis: **aluno** em nome, RGM e curso; **admin** em nome e CPF.
  O campo do outro perfil vem no corpo mas é ignorado, então aluno não
  ganha CPF nem admin ganha RGM.

  **Não** mexe em e-mail (é o login - errar ali tiraria o acesso da
  própria pessoa), perfil (seria escalada de privilégio) nem senha (essa
  vai por `/auth/redefinir-senha`). O id vem do token, nunca do corpo.
- **O admin edita qualquer cadastro** em `PUT /usuarios/{id}` (tela de
  Usuários), incluindo e-mail e perfil - campos que o dono não mexe
  sozinho. Duas travas impedem que ele se tranque fora do sistema:

  - **não pode rebaixar a si mesmo** para ALUNO;
  - **não pode excluir a própria conta**.

  Só admin cria admin, então um admin que se rebaixasse não teria quem o
  promovesse de volta - o sistema ficaria sem administrador e só um
  acesso direto ao banco resolveria. Rebaixar **outro** admin é
  permitido: isso sempre deixa pelo menos um de pé, que é ele mesmo.
- **Não existe recorte por semestre.** A pontuação é simplesmente a do
  aluno no WECTI, somando todas as palestras mais os pontos de gincana.
  Não há reinício por período.

  > Uma versão anterior deste arquivo afirmava o contrário — "a pontuação
  > é acumulada dentro do período (semestre) e reinicia a cada novo
  > período" — e listava isso como regra fechada. **Era errado.** A regra
  > nasceu do rascunho inicial do `openapi.yaml`, escrito antes de falar
  > com o professor, e nunca foi validada. Perguntado diretamente se o
  > semestre do aluno impactaria a pontuação das palestras, ele
  > respondeu: *"Qualquer aluno pode se matricular de qualquer palestra.
  > Não precisa relacionar com nada."*
  >
  > A entidade `Periodo` foi removida na migration V7. Os dados mostravam
  > que ninguém entendia o conceito: os dois registros em produção se
  > chamavam "Matutino" e "Noturno", com datas idênticas. Pior, a
  > pontuação e o ranking buscavam "o período que contém hoje" e falhavam
  > quando não havia nenhum — as telas parariam de carregar em
  > 21/12/2026, sem erro visível.
  >
  > Se um dia for preciso separar edições do WECTI, o recorte é por
  > **data do evento**, não por uma entidade nova.
- **Listagem de eventos em ordem cronológica, agrupada por dia e turno.**
  A API devolve `GET /eventos` ordenado por `dataHoraInicio` crescente
  (`EventoService.listar`) - sem isso a grade saía na ordem do banco,
  embaralhada. A tela do aluno agrupa em blocos de "um dia, um turno"
  (`agruparPorDiaETurno` em `frontend/src/utils/eventos.ts`): **manhã**
  antes das 12h, **tarde** das 12h às 18h, **noite** a partir das 18h,
  pelo horário de início. O agrupamento é por dia **e** turno para não
  juntar a manhã de quinta com a de sexta. É só apresentação: turno não é
  campo do evento nem coluna no banco.
- **RGM**: identificador acadêmico do aluno, 8 dígitos, único.
  Obrigatório quando `perfil = ALUNO`; essa obrigatoriedade é validada em
  código (service), não no schema do banco (a constraint do banco só
  garante o formato de 8 dígitos quando o valor não é nulo).

## Convenções de código (backend)

- Java 17+, Spring Boot 4.1, Maven.
- Pacote base `com.wecti.api`, organizado por camada: `domain`,
  `repository`, `service`, `controller`, `config`, `dto`.
- Entidades JPA usam Lombok (`@Data`, `@Builder`, `@NoArgsConstructor`,
  `@AllArgsConstructor`) e UUID como chave primária
  (`@GeneratedValue(strategy = GenerationType.UUID)`).
- Migrações de banco via Flyway, em `backend/src/main/resources/db/migration`.
  Nunca usar `ddl-auto: update` ou `create` - o schema só muda via
  migration versionada (`ddl-auto` está fixo em `validate`).
- Nomes de coluna em `snake_case` no banco, `camelCase` em Java (mapeado
  via `@Column(name = "...")`).

## Autenticação e autorização

Autenticação por JWT stateless (`security/JwtService`,
`JwtAuthenticationFilter`). Quem pode chamar cada rota fica decidido em
**um lugar só**: as regras `requestMatchers(...).hasRole(...)` do
`SecurityConfig`. Toda restrição por perfil vive no backend - esconder o
item do menu no frontend é conveniência, não segurança. Endpoint novo
precisa de regra explícita lá.

Quando a rota age sobre "a própria pessoa" (`/me/...`, `PUT
/usuarios/me`), o id vem do token (`@AuthenticationPrincipal
AuthenticatedUser`), nunca do caminho ou do corpo.

> Uma versão anterior deste arquivo dizia que o JWT "não estava
> implementado" e que o `SecurityConfig` liberava tudo (`permitAll`).
> Isso era o esqueleto inicial e deixou de valer há tempos.
