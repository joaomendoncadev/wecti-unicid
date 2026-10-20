# WECTI - Backend

API REST do WECTI - a única fonte de verdade do sistema; nenhum cliente
acessa o banco direto. **Em produção** desde setembro de 2026: 38
operações em 27 rotas, autenticação JWT com autorização por perfil, 9
migrations versionadas e 164 testes automatizados.

As regras de negócio e o porquê de cada decisão estão no
[`CLAUDE.md`](../CLAUDE.md) da raiz; o contrato dos endpoints, em
[`docs/openapi.yaml`](../docs/openapi.yaml).

Construído sobre Spring Boot 4.1 (não 3.x - a última linha 3.x sai de
suporte em 30/06/2026, não fazia sentido começar um projeto novo nela).
O Boot 4 trouxe mudanças de quebra reais (nomes de starter, Spring
Security com CSRF habilitado por padrão, etc.) - essas adaptações estão
comentadas no topo do `pom.xml`.

Ao clonar, rode `mvn clean compile` antes de mais nada, para baixar as
dependências.

## Pré-requisitos

- JDK 17+ (a versão exigida pelo Boot 4; JDK 21 funciona também e é
  recomendado pela Spring)
- Maven 3.9+
- Docker (para rodar o MySQL local)

## Rodando localmente

O `application.yml` (perfil padrão) não tem nenhum valor default para
credenciais - é o arquivo usado em produção e não deve conter segredo
nenhum. Para desenvolvimento local, ative o perfil `dev`
(`application-dev.yml`), que aponta para o MySQL do `docker-compose.yml`:

```bash
docker compose up -d db
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A API sobe em `http://localhost:8080`. Endpoints úteis:

- `GET /health` - verifica se a aplicação está no ar
- `GET /docs` - Swagger UI
- `GET /api-docs` - OpenAPI gerado a partir do código (vai divergir do
  `docs/openapi.yaml` da raiz até as regras de negócio serem
  implementadas - o arquivo da raiz é o contrato combinado com o time,
  este aqui é só o que já existe em código)

## Variáveis de ambiente

No perfil padrão (produção), `DB_USER`, `DB_PASSWORD` e `JWT_SECRET` são
**obrigatórias** - não têm default, e a aplicação não sobe sem elas
(de propósito, para nenhum segredo real ficar no código). No perfil
`dev`, todas têm default apontando para o `docker-compose.yml` local.

| Variável            | Default (perfil `dev`) | Obrigatória em produção | Descrição                       |
|----------------------|-------------------------|--------------------------|----------------------------------|
| `DB_HOST`            | `localhost`             | não (URL fixa no perfil padrão) | Host do MySQL             |
| `DB_PORT`            | `3307`                  | não (URL fixa no perfil padrão) | Porta do MySQL (docker-compose local; produção usa 3306) |
| `DB_NAME`            | `wecti`                 | não (URL fixa no perfil padrão) | Nome do banco              |
| `DB_USER`            | `wecti`                 | **sim**                  | Usuário do banco                 |
| `DB_PASSWORD`        | `wecti`                 | **sim**                  | Senha do banco                   |
| `JWT_SECRET`         | valor de dev fixo       | **sim**                  | Segredo de assinatura dos tokens - use um valor aleatório forte em produção |
| `JWT_EXPIRATION_MS`  | `86400000` (24h)        | não                       | Validade do token em ms          |
| `SERVER_PORT`        | `8080`                  | não                       | Porta da aplicação                |

## Estrutura

```
src/main/java/com/wecti/api/
├── domain/       entidades JPA (Usuario, Evento, Inscricao, Checkin, ...)
├── repository/   interfaces Spring Data JPA
├── config/       segurança e Swagger
└── controller/   só tem o /health por enquanto

src/main/resources/db/migration/
└── V1__init.sql  schema inicial (Flyway)
```

## Próximos passos (Fase 1)

- Implementar `/auth/login` com JWT de verdade
- Controllers e services para eventos, inscrições, check-in/check-out
- Job de penalização por no-show (rodando após o evento)
- Geração de certificado em PDF e envio de e-mail
