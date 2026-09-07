#!/usr/bin/env bash
# Restaura um dump do backup-mysql.sh para um banco SEPARADO, para conferir que
# o backup presta antes de precisar dele de verdade.
#
# Backup que nunca foi restaurado nao conta como backup: so se descobre que ele
# nao serve na hora em que ele e a unica coisa que sobrou.
#
# Uso:
#   ./restaurar-backup.sh <arquivo.sql.gz> [nome_do_banco_destino]
#
# Exemplo:
#   ./restaurar-backup.sh /opt/wecti/backups/wecti-2026-09-07_0300.sql.gz
#
# O banco destino padrao e "wecti_teste_restauracao". O script RECUSA
# sobrescrever o banco de producao - restaurar por cima da producao e uma
# operacao destrutiva e nao pode acontecer por engano de digitacao.
#
# O banco de teste NAO e removido no fim: quem restaurou decide quando
# descartar, depois de conferir. Para remover:
#   docker exec -e MYSQL_PWD=<senha> wecti-db mysql -u root -e "DROP DATABASE wecti_teste_restauracao;"
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/wecti}"
ARQUIVO="${1:-}"
DESTINO="${2:-wecti_teste_restauracao}"

if [[ -z "$ARQUIVO" ]]; then
  echo "uso: $0 <arquivo.sql.gz> [nome_do_banco_destino]" >&2
  echo >&2
  echo "backups disponiveis:" >&2
  ls -1t "$APP_DIR"/backups/*.sql.gz 2>/dev/null | head -10 >&2 || echo "  (nenhum)" >&2
  exit 1
fi

if [[ ! -f "$ARQUIVO" ]]; then
  echo "erro: arquivo nao encontrado: $ARQUIVO" >&2
  exit 1
fi

set -a; source "$APP_DIR/.env"; set +a
PROD="${DB_NAME:-wecti}"

if [[ "$DESTINO" == "$PROD" ]]; then
  cat >&2 <<MSG
erro: recusando restaurar por cima do banco de producao ("$PROD").

Este script existe para CONFERIR o backup, nao para aplicar um.
Se a intencao e mesmo restaurar a producao depois de um desastre, faca na mao,
com o sistema parado e um dump novo do estado atual guardado antes:

  docker compose -f $APP_DIR/docker-compose.prod.yml stop api
  gunzip -c "$ARQUIVO" | docker exec -i wecti-db mysql -u root -p'<senha>' "$PROD"
  docker compose -f $APP_DIR/docker-compose.prod.yml start api
MSG
  exit 1
fi

echo "== Restaurando =="
echo "  origem:  $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"
echo "  destino: $DESTINO (banco de teste, separado da producao)"
echo

docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root \
  -e "DROP DATABASE IF EXISTS \`$DESTINO\`; CREATE DATABASE \`$DESTINO\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

gunzip -c "$ARQUIVO" | docker exec -i -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root "$DESTINO"

echo "== Conferencia: linhas por tabela no banco restaurado =="
docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root "$DESTINO" -N -e "
SELECT 'usuarios',          COUNT(*) FROM usuarios
UNION ALL SELECT 'eventos',          COUNT(*) FROM eventos
UNION ALL SELECT 'inscricoes',       COUNT(*) FROM inscricoes
UNION ALL SELECT 'checkins',         COUNT(*) FROM checkins
UNION ALL SELECT 'certificados',     COUNT(*) FROM certificados
UNION ALL SELECT 'sessoes_checkin',  COUNT(*) FROM sessoes_checkin
UNION ALL SELECT 'pontuacoes_extras',COUNT(*) FROM pontuacoes_extras;" \
  | awk -F'\t' '{printf "  %-20s %s\n", $1, $2}'

echo
echo "== Versao do schema no backup (Flyway) =="
docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db mysql -u root "$DESTINO" -N -e \
  "SELECT CONCAT('  V', version, '  ', description, '  success=', success)
   FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"

echo
echo "Restauracao concluida no banco \"$DESTINO\". A producao (\"$PROD\") nao foi tocada."
echo "Compare as contagens acima com o esperado antes de considerar o backup bom."
