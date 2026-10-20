# WECTI

Sistema de controle de presença em palestras acadêmicas da Universidade
Cidade de São Paulo (UNICID). **Em produção** — rodou durante os três
dias da Semana de Tecnologia (28, 29 e 30 de setembro de 2026), com
alunos reais.

🔗 **[www.wecti.com.br](https://www.wecti.com.br)**

---

## O problema

Controlar presença em palestra acadêmica costuma ser lista de papel
circulando pela sala: assinatura ilegível, aluno que assina pelo colega,
e alguém digitando tudo depois. No fim, ninguém sabe ao certo quem
assistiu o quê.

O WECTI resolve isso com o aparelho que todo aluno já tem no bolso —
**sem pedir que ninguém instale aplicativo nenhum**.

## Como funciona

```
  ADMIN                        ALUNO
  ─────                        ─────
  projeta o QR na tela  ──→    escaneia com a câmera do celular
  (renova a cada 15 min)       (abre direto no navegador)
                                      │
                                      ▼
                               confirma presença autenticado
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          ▼                           ▼                           ▼
      pontuação                  certificado                nota nas matérias
                             (validação pública)          (1000 pts = 1,0)
```

1. O aluno **se autocadastra** e escolhe as palestras.
2. Na sala, o admin **projeta um QR code** que se renova sozinho a cada
   15 minutos — um print mandado no grupo vence junto com a janela.
3. O aluno **escaneia com a câmera do próprio celular**. A câmera abre a
   URL embutida no QR, que confirma a presença dele autenticado.
4. Check-in **e** check-out geram pontos e liberam o **certificado**, com
   código de validação pública.
5. No fim do evento, o aluno **converte os pontos em décimos de nota** nas
   matérias que escolher.

## Telas

| | |
|---|---|
| **Aluno** | Eventos, minhas inscrições, histórico, certificados, pontuação, distribuição de notas |
| **Admin** | Eventos, usuários, check-in (QR projetado + lista de presença ao vivo), ranking, notas distribuídas |

## Regras de negócio

As decisões de produto vieram do professor responsável, que atuou como
Product Owner — várias delas mudaram depois de ver o sistema funcionando
no ar. O raciocínio de cada uma está documentado em
**[`CLAUDE.md`](CLAUDE.md)**, inclusive as que foram revertidas e por quê.

- **Inscrição e cancelamento fecham juntos**, 15 minutos após o início do
  evento. A folga existe para quem chega atrasado: sem ela, quem aparece
  5 minutos depois não se inscreve, logo não faz check-in, logo não
  pontua — ficaria de fora de uma palestra em que está presente.
- **Certificado exige check-in e check-out**, e nada mais. A leitura dos
  dois QR codes é a prova de presença.
- **Teto de 2000 pontos** em palestras. Pontos de gincana (lançados pelo
  admin) ficam fora do teto, por serem premiação.
- **Conversão em nota**: 1000 pontos = 1,0, com teto de 2,0. O aluno
  reparte como quiser entre as turmas em que estuda.
- **Presença lançada pelo admin**, para quem participou e não conseguiu
  ler o QR. Grava a *presença*, não os pontos — assim pontuação,
  certificado e saída do no-show passam a valer sozinhos.

## O que o evento no ar ensinou

O sistema entrou em produção e quebrou de um jeito que nenhum teste
pegaria. Vale registrar:

> **Dezenas de alunos não conseguiram entrar no primeiro dia.**
>
> O limite de tentativas de login era por endereço IP. Mas o Wi-Fi do
> campus coloca a turma inteira atrás de um mesmo IP público (NAT) —
> então dez alunos errando a própria senha bloqueavam **todos os
> outros**, inclusive no "esqueci minha senha".
>
> A correção trocou a chave do limite de IP para **conta**, e reduziu a
> espera de 15 para 2 minutos: num evento, 15 minutos de bloqueio
> equivalem a perder a palestra.

Outras correções vindas do uso real:

- RGM e e-mail passaram a tolerar espaço e maiúscula na recuperação de
  senha — o teclado do celular cola um espaço invisível no fim do número,
  e a comparação era byte a byte.
- O QR passou a valer 15 minutos (eram 60 segundos), com barra de tempo
  na tela: o aluno que precisava fazer login no meio levava "este QR code
  já mudou" num check-in legítimo.
- Exclusão de aluno passou a apagar em cascata — alunos criaram conta
  duplicada e o admin precisava consolidar na conta certa.

## Stack

**Backend** — Java 17, Spring Boot 4.1, Spring Security (JWT), JPA,
MySQL 8 com Flyway, Maven
**Frontend** — React 19, TypeScript, Vite, Tailwind CSS 4, React Router,
Axios, React Hook Form + Zod
**Testes** — JUnit 5, Mockito, AssertJ, H2

## Números

| | |
|---|---|
| Operações de API | 38, em 27 rotas |
| Migrations versionadas | 9 |
| Testes automatizados | 164 |
| Arquivos Java / TypeScript | 125 / 62 |

## Decisões técnicas

- **O backend é a única fonte de verdade.** Nenhum cliente toca o banco.
- **QR code rotativo**: o link carrega um código derivado por HMAC-SHA256
  de uma janela de tempo — não é um identificador fixo. A janela anterior
  também é aceita, então quem escaneou na virada não é punido.
- **Schema só muda por migration versionada.** `ddl-auto` fica em
  `validate`; o Hibernate nunca altera tabela.
- **Regra de negócio num lugar só.** A pontuação é calculada por
  `RegraPontuacao`, usada pela tela do aluno e pelo ranking — números
  diferentes nos dois lugares derrubariam a confiança na competição.
- **O contrato vem antes.** Endpoint novo entra em
  [`docs/openapi.yaml`](docs/openapi.yaml) antes de ser implementado.

## Rodando localmente

```bash
# Banco
docker compose up -d

# Backend  → http://localhost:8080  (Swagger em /docs)
cd backend && mvn spring-boot:run

# Frontend → http://localhost:5173
cd frontend && npm install && npm run dev
```

Detalhes em [`backend/README.md`](backend/README.md) e
[`frontend/README.md`](frontend/README.md). Para publicar, ver
[`docs/DEPLOY.md`](docs/DEPLOY.md).

## Documentação

| Arquivo | O que tem |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Regras de negócio e o porquê de cada decisão |
| [`docs/openapi.yaml`](docs/openapi.yaml) | Contrato da API — fonte de verdade dos endpoints |
| [`docs/DEPLOY.md`](docs/DEPLOY.md) | Publicação em produção (VPS com Docker Compose) |
| [`docs/verificar-banco.sql`](docs/verificar-banco.sql) | Diagnóstico do banco antes de subir versão |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Convenções de commit e fluxo de branches |

## Equipe

Projeto acadêmico desenvolvido em grupo na UNICID, com o professor
responsável atuando como Product Owner: definia os requisitos,
priorizava e validava cada entrega.
