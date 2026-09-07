# `deploy/` — configuração de produção

Tudo que descreve como o WECTI roda no ar. Antes de existir esta pasta, esses
arquivos viviam **apenas dentro da instância**: se a máquina fosse perdida, a
configuração ia junto, e a única documentação que sobrava descrevia outra
hospedagem.

Para o guia de operação — como acompanhar um deploy, fazer rollback, restaurar
backup —, veja [`../docs/DEPLOY.md`](../docs/DEPLOY.md).

## Onde o sistema roda

| | |
|---|---|
| Instância | Lightsail `wecti-prod`, 2 GB RAM / 2 vCPU / 60 GB, Ubuntu 22.04 |
| Região | `us-east-1a` |
| IP fixo | `34.193.37.28` |
| Site | `https://wecti.com.br` (e `www`) |
| API | `https://api.wecti.com.br` |
| Imagem | `ghcr.io/joaomendoncadev/wecti-api` |

## O que é o quê

| Arquivo | Papel | Quem aplica |
|---|---|---|
| `compose.prod.yml` | MySQL + API em contêiner | Enviado a cada deploy pelo Actions |
| `scripts/backup-mysql.sh` | Dump diário, retenção de 14 dias | Cron às 03:00 na instância |
| `scripts/restaurar-backup.sh` | Confere que um dump presta | Manual |
| `nginx/*.conf` | Roteamento e TLS | **Manual** — ver abaixo |
| `.env.production.exemplo` | Modelo do `/opt/wecti/.env` | Manual, uma vez |

## Por que o Nginx não é aplicado automaticamente

Quem edita esses arquivos no servidor é o **certbot**: ao emitir e renovar os
certificados, ele reescreve os blocos `listen 443 ssl` e os caminhos dos `.pem`
dentro deles. Se o deploy sobrescrevesse os `.conf` a cada publicação, o TLS
quebraria na primeira renovação.

As cópias aqui são **espelho do que está no ar** (conferidas por `md5sum`) e
servem para reconstruir a máquina do zero — não para alimentar o deploy.

Ao mudar algo neles, aplique à mão e confira antes de recarregar:

```bash
sudo cp nginx/wecti.com.br.conf /etc/nginx/sites-available/wecti.com.br && sudo nginx -t && sudo systemctl reload nginx
```

`nginx -t` antes do `reload` não é zelo: uma configuração inválida derruba o
site inteiro, inclusive a API.

## As duas regras que não podem se perder

Vieram do `.htaccess` do cPanel e são a parte do roteamento que quebra em
silêncio:

```nginx
location = / { try_files /home.html =404; }          # a raiz entrega a landing
location /   { try_files $uri $uri/ /index.html; }   # o resto cai no React
```

Sem a primeira, quem abre `wecti.com.br` cai na tela de login em vez da página
do evento. Sem a segunda, **todo QR code escaneado devolve 404** — e isso só
aparece quando alguém aponta a câmera, no dia do evento. O `deploy.yml` testa
as duas ao fim de cada publicação, justamente por isso.

## Segredos

O `/opt/wecti/.env` **nunca** é enviado pelo deploy nem versionado. Ele existe
só na instância. Use `.env.production.exemplo` como modelo.

O `JWT_SECRET` assina os tokens de login: quem o tiver forja um token de admin.
Se vazar, gere outro e reinicie a API — todos os logins caem, o que é o
comportamento desejado.

## Primeira instalação numa máquina nova

1. Docker, nginx e certbot instalados; portas 80/443 abertas na Lightsail.
2. DNS de `wecti.com.br`, `www` e `api.wecti.com.br` apontando para o IP.
3. `/opt/wecti/.env` criado a partir do exemplo, com segredos novos.
4. `compose.prod.yml` e `scripts/` copiados para `/opt/wecti/`.
5. `nginx/*.conf` em `sites-available`, linkados em `sites-enabled`,
   `default` removido.
6. `sudo certbot --nginx -d wecti.com.br -d www.wecti.com.br` e
   `sudo certbot --nginx -d api.wecti.com.br`.
7. Cron do backup:
   `0 3 * * * /opt/wecti/scripts/backup-mysql.sh >> /opt/wecti/backups/backup.log 2>&1`
8. Swap de 2 GB (a máquina é de 2 GB; sem swap, um pico mata um processo em vez
   de deixar o sistema lento).
9. Disparar o workflow `Deploy` para publicar a primeira versão.

O primeiro usuário admin continua sendo criado à mão — ver `../docs/DEPLOY.md`.
