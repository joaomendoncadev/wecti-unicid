# Deploy e operação do WECTI

O sistema roda na **AWS Lightsail**, numa instância Ubuntu chamada
`wecti-prod`. Este documento cobre como publicar, como voltar atrás quando algo
dá errado, e o que olhar durante o evento.

Para a descrição dos arquivos de configuração, veja
[`../deploy/README.md`](../deploy/README.md).

> **Histórico:** até agosto de 2026 este guia descrevia uma hospedagem cPanel
> (Integrator Host), com upload de `.jar` pelo painel, phpMyAdmin e `.htaccess`.
> Nada disso vale mais. O que sobreviveu daquele texto — migrations, rate limit,
> QR rotativo, leitura de log — está preservado aqui porque continua verdadeiro:
> é conhecimento sobre o **sistema**, não sobre a hospedagem.

---

## 1. Como o sistema está montado

```
                    ┌──────────── wecti-prod (Lightsail, 2 GB) ────────────┐
  navegador ──443──▶│  nginx ──▶ /var/www/wecti          (site estático)   │
                    │        └─▶ 127.0.0.1:8080 ──▶ contêiner wecti-api    │
                    │                                        │             │
                    │                                   wecti-db (MySQL)   │
                    │                                   volume persistente │
                    └──────────────────────────────────────────────────────┘
```

| Peça | Onde |
|---|---|
| Site (landing + React) | `/var/www/wecti`, servido pelo nginx |
| API | Contêiner `wecti-api`, escutando **só** em `127.0.0.1:8080` |
| Banco | Contêiner `wecti-db`, volume `wecti-db-data`, **sem porta pública** |
| Segredos | `/opt/wecti/.env` — só na máquina, nunca no repositório |
| TLS | Let's Encrypt via certbot, renovação automática (`certbot.timer`) |
| Backup | `/opt/wecti/backups`, dump diário às 03:00, 14 dias |

A API não tem porta aberta para a internet: tudo passa pelo nginx. Por isso o
`server.forward-headers-strategy=framework` no `application.yml` importa — sem
ele a aplicação veria toda requisição vindo do IP do nginx, e o limite de
tentativas de login bloquearia o site inteiro por causa de um único atacante.

---

## 2. Publicar uma versão

**Push na `main` publica automaticamente.** Não há passo manual.

O que acontece, em ordem:

1. **Testes** — `mvn verify` com MySQL de serviço. Se um teste falhar, nada sai
   daqui.
2. **Imagem** — a API é compilada e publicada no GHCR com duas tags: o SHA do
   commit e `latest`.
3. **Frontend** — `npm ci && npm run build`, com o endereço da API gravado
   dentro dos arquivos.
4. **Deploy** — envia o site por `rsync`, troca a imagem da API, espera o
   `/health` e **volta sozinho para a versão anterior** se ela não responder em
   60 segundos.
5. **Conferência externa** — bate o site pelo endereço público, incluindo as
   rotas de QR code.

Para publicar sem um commit novo: aba **Actions** → workflow **Deploy** →
*Run workflow*.

### A instância não compila nada

A máquina tem 2 GB de RAM, com MySQL e a JVM já ocupando boa parte. Rodar Maven
e npm ali significava um deploy capaz de derrubar o banco por falta de memória —
no pior momento possível. O Actions constrói; o servidor só troca arquivos.

### O que o deploy NÃO toca

- `/opt/wecti/.env` — os segredos ficam só na máquina
- Os `.conf` do nginx — quem os edita é o certbot; sobrescrevê-los quebraria o
  TLS na renovação seguinte
- O volume do banco

---

## 3. Rollback

O `.env` guarda a versão no ar em `WECTI_TAG`. Voltar é trocar a tag:

```bash
cd /opt/wecti && grep WECTI_TAG .env
```

```bash
cd /opt/wecti && sed -i 's|^WECTI_TAG=.*|WECTI_TAG=<sha-anterior>|' .env && docker compose -f compose.prod.yml --env-file .env up -d api
```

Leva segundos: a imagem antiga já está no GHCR, não há recompilação. Os SHAs
ficam no histórico de commits e na lista de pacotes do repositório.

O deploy faz isso sozinho quando o `/health` falha. Este passo manual é para
quando o problema aparece **depois** — a API subiu, mas alguma coisa está errada.

---

## 4. Backup e restauração

O dump roda todo dia às 03:00 e guarda 14 dias.

```bash
ls -lh /opt/wecti/backups
```

Para conferir que um backup presta — **faça isso antes de precisar dele**:

```bash
/opt/wecti/scripts/restaurar-backup.sh /opt/wecti/backups/wecti-2026-09-07_0300.sql.gz
```

Restaura num banco separado (`wecti_teste_restauracao`), mostra a contagem de
linhas por tabela e **não toca na produção** — o script recusa explicitamente
restaurar por cima dela.

> **Pendência conhecida:** os dumps ficam no mesmo disco da instância. Isso
> cobre "apaguei a tabela errada", mas não cobre "perdi a instância". Um
> snapshot agendado da Lightsail fecharia essa lacuna.

Restauração de verdade, depois de um desastre — com o sistema parado e um dump
do estado atual guardado antes:

```bash
cd /opt/wecti && source .env && docker compose -f compose.prod.yml --env-file .env stop api && gunzip -c backups/ARQUIVO.sql.gz | docker exec -i -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root "$DB_NAME" && docker compose -f compose.prod.yml --env-file .env start api
```

---

## 5. Banco de dados e migrations

O Flyway aplica sozinho, na ordem, o que faltar. O schema **nunca** muda por
geração automática (`ddl-auto` está fixo em `validate`).

Versão esperada hoje: **7**.

| Migration | O que faz |
|---|---|
| **V2** | Adiciona `cpf`, cria `sessoes_checkin` e **apaga `inscricoes.qrcode_token`** — parte destrutiva |
| **V3** | Adiciona `curso` em usuários |
| **V4** | Adiciona `codigo` em certificados e preenche os existentes |
| **V5** | Adiciona `segredo` em `sessoes_checkin` (código rotativo do QR) |
| **V6** | Adiciona `capacidade` em eventos e cria `pontuacoes_extras` |
| **V7** | **Apaga a tabela `periodos`** e as colunas `periodo_id` — segunda parte destrutiva |

As duas destrutivas são o motivo de o backup vir antes de qualquer atualização
de schema. Nada de valor se perde na V7: inscrições, presenças, pontos e
certificados nunca dependeram de período.

Conferir em que versão o banco está:

```bash
cd /opt/wecti && source .env && docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root "$DB_NAME" -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

**Se alguma linha tiver `success = 0`, não suba a aplicação**: há uma migration
que falhou no meio e o banco está inconsistente. Restaure o backup.

---

## 6. Variáveis de ambiente

Ficam em `/opt/wecti/.env`. Modelo em
[`../deploy/.env.production.exemplo`](../deploy/.env.production.exemplo).

### Sem estas a aplicação não sobe

| Variável | O que é |
|---|---|
| `DB_USER`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | Credenciais do MySQL |
| `JWT_SECRET` | Assina os tokens de login |

O `JWT_SECRET` precisa ser exclusivo de produção. Quem o tiver forja um token de
admin. Gere com `openssl rand -base64 48`.

### Sem estas o site não funciona direito

| Variável | Valor |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://wecti.com.br,https://www.wecti.com.br` |
| `FRONTEND_URL` | `https://wecti.com.br` |

A regra do `CORS_ALLOWED_ORIGINS` é sempre a mesma: lista de onde o **navegador**
chama, ou seja, o endereço do **site** — nunca o da API. Errar isso produz um
sintoma enganoso: o login falha com "não foi possível entrar" mesmo com a senha
certa, e funciona por `curl`.

> O endereço da API no frontend **não** vem daqui. Ele é gravado dentro dos
> arquivos no momento do build, pela variável de repositório `VITE_API_URL` no
> GitHub. Trocar depois do build não tem efeito nenhum — é preciso publicar de
> novo.

### Ajustáveis

| Variável | Padrão | Para quê |
|---|---|---|
| `SPRINGDOC_ENABLED` | `false` | Deixa `/docs` e `/api-docs` fora do ar |
| `RATE_LIMIT_MAX_FALHAS` | `10` | Tentativas de login erradas antes de bloquear o IP |
| `RATE_LIMIT_JANELA_MINUTOS` | `15` | Duração do bloqueio |
| `CHECKIN_JANELA_CODIGO_SEGUNDOS` | `60` | De quanto em quanto tempo o QR se renova |
| `CHECKIN_TOLERANCIA_ANTES_MINUTOS` | `30` | Quanto antes do evento o check-in abre |
| `CHECKIN_TOLERANCIA_DEPOIS_MINUTOS` | `30` | Quanto depois do fim o check-out ainda vale |
| `INSCRICAO_TOLERANCIA_MINUTOS` | `15` | Folga para se inscrever depois do início |

**Sobre o rate limit:** ele conta **apenas tentativas que falham**. Numa
palestra a turma inteira acessa pelo mesmo Wi-Fi e sai com o mesmo IP público —
se acertos contassem, os alunos bloqueariam uns aos outros. Quem digita a senha
certa nunca entra na conta.

**Sobre a janela do QR:** o QR fica projetado e é o mesmo para a sala inteira,
então uma foto da tela mandada no grupo serviria para quem não veio. O link
carrega um código que vale por uma janela curta, e a tela do admin busca o
próximo sozinha. A janela anterior também é aceita, então na prática o código
vale entre uma e duas janelas. **Não deixe abaixo de ~30 s** — aluno com
internet ruim começa a perder check-in legítimo.

**Sobre a folga de inscrição:** existe para quem chega atrasado. Sem ela, quem
aparece 5 minutos depois do começo não se inscreve, logo não faz check-in, logo
não pontua — ficaria de fora de uma palestra em que está presente. **Não passe
de 30 minutos** sem mexer também em `CHECKIN_TOLERANCIA_ANTES_MINUTOS`.

---

## 7. Diagnóstico

```bash
cd /opt/wecti && docker compose -f compose.prod.yml --env-file .env ps
```

```bash
cd /opt/wecti && docker compose -f compose.prod.yml --env-file .env logs -f api
```

Start bem-sucedido, nesta ordem:

```
Successfully validated N migrations    (ou "Migrating schema ... to version 7")
Tomcat started on port 8080
Started WectiApiApplication in X.X seconds
```

### Erros já vistos neste projeto

| No log | Causa | O que fazer |
|---|---|---|
| `PlaceholderResolutionException: ... app.datasource.username` | Falta `DB_USER`/`DB_PASSWORD` no `.env` | Conferir a seção 6 |
| `Access denied for user 'X'@'%'` | Senha errada ou usuário inexistente | Conferir `DB_USER`/`DB_PASSWORD` |
| `Access denied ... to database 'Y'` | Usuário não associado ao banco, ou `DB_NAME` errado | Conferir `DB_NAME` |
| `Unknown database` | Banco não existe com esse nome | Conferir `DB_NAME` |
| `Communications link failure` | O contêiner do banco não subiu | `docker compose ps`; ver os logs do `db` |
| `Port already in use` | Instância anterior ainda rodando | `docker compose down` e subir de novo |

### Alguém está vendo a página antiga depois de um deploy

Peça um recarregamento forçado (no Safari, Option+clique no botão de recarregar;
no Chrome, Shift+F5). Se resolver, era cache do navegador.

Isso **não deveria mais acontecer**: o nginx envia `Cache-Control: no-cache` para
o HTML e para o `style.css`, que não têm hash no nome — o navegador é obrigado a
revalidar, e recebe um `304` vazio quando nada mudou. Já os `/assets/*` do Vite
têm hash no nome e ficam um ano em cache, porque um deploy gera outro nome.

Se o sintoma voltar, confira que a regra continua no ar:

```bash
curl -sI https://wecti.com.br/style.css | grep -i cache-control
```

Tem que responder `no-cache`. Se vier vazio, a configuração do nginx foi
sobrescrita — reaplique `deploy/nginx/wecti.com.br.conf`.

> Aconteceu em 07/09/2026: o `style.css` é compartilhado entre a landing e a
> página de inscrições. Quem tinha visitado a home antes do deploy abriu a
> página nova reaproveitando o CSS antigo, que ainda não tinha as regras dela —
> e viu o conteúdo sem estilo nenhum, com um vão enorme no lugar do título.

### Site fora do ar

```bash
sudo nginx -t && sudo systemctl status nginx --no-pager
```

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/health
```

Se o `/health` interno responde 200 mas o site não abre, o problema é nginx ou
DNS, não a aplicação.

### Certificado

Renova sozinho pelo `certbot.timer`. Para conferir:

```bash
sudo certbot certificates && systemctl list-timers --no-pager | grep certbot
```

### Memória

A máquina tem 2 GB e um swap de 2 GB. Se o sistema ficar lento, veja se está em
swap:

```bash
free -h && docker stats --no-stream
```

Swap em uso constante significa que a máquina está pequena para a carga — nesse
caso, aumentar o plano da Lightsail é a saída, não mais ajuste.

---

## 8. Primeiro usuário admin

O sistema não cria administrador sozinho.

```bash
cd /opt/wecti && source .env && docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root "$DB_NAME" -e "INSERT INTO usuarios (id, nome, email, senha, perfil, cpf, criado_em) VALUES (UUID(), 'Nome do Administrador', 'admin@unicid.edu.br', 'definir-pela-tela', 'ADMIN', '12345678901', NOW());"
```

O campo `senha` recebe esse texto de propósito: não é um hash, então ninguém
entra com ele. Em seguida, abra `https://wecti.com.br/recuperar-senha` e informe
o e-mail e o CPF cadastrados — isso define a senha real.

O CPF é obrigatório para admin justamente por isso: é o identificador que
permite recuperar o acesso.

---

## 9. Conferir depois de publicar

O deploy já testa os itens marcados com ✅ automaticamente. Os demais precisam
de gente.

- ✅ `https://api.wecti.com.br/health` responde 200
- ✅ `https://wecti.com.br/` abre a **landing**, não a tela de login
- ✅ `https://wecti.com.br/validar/ABC123` abre o sistema (caminho do QR de
  certificado)
- ✅ `https://wecti.com.br/checkin/confirmar/xyz` abre o sistema
- [ ] `http://wecti.com.br` redireciona para HTTPS, com cadeado fechado
- [ ] Os botões **Primeiro acesso** e **Login** na landing levam ao sistema
- [ ] Um aluno consegue se cadastrar e se inscrever
- [ ] O QR na tela do admin **muda sozinho** a cada ~1 minuto
- [ ] Um celular lê o QR e confirma presença
- [ ] Um link de QR copiado e aberto 3 minutos depois é **recusado**
- [ ] O certificado sai em PDF e o código valida em `/validar`
- [ ] `/docs` **não** abre
- [ ] 11 tentativas de login com senha errada devolvem `429`

O teste do QR vencido é o que costuma ser esquecido, e é justamente a trava
contra repassar o código para quem não está na sala.

---

## 10. Antes do evento

**Rode um piloto com turma reduzida**, do cadastro à emissão do certificado. É a
forma mais barata de descobrir um problema de configuração — com 20 pessoas em
vez de 300.

No dia, tenha à mão:

- O comando de rollback (seção 3)
- `docker compose logs -f api` aberto num terminal
- O contato de quem tem acesso SSH à instância
