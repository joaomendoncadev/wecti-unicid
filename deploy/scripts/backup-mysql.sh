#!/usr/bin/env bash
# Dump diario do MySQL (container "wecti-db"). Agendado via cron:
#   0 3 * * * /opt/wecti/scripts/backup-mysql.sh >> /opt/wecti/backups/backup.log 2>&1
#
# Para conferir que um dump presta, use restaurar-backup.sh. Backup que nunca
# foi restaurado nao conta como backup.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/wecti}"
BACKUP_DIR="$APP_DIR/backups"
KEEP_DAYS=14

set -a; source "$APP_DIR/.env"; set +a

mkdir -p "$BACKUP_DIR"
FINAL="$BACKUP_DIR/wecti-$(date +%F_%H%M).sql.gz"
PARCIAL="$FINAL.parcial"

# MYSQL_PWD em vez de -p: a senha nao aparece na lista de processos do
# container, e nao passa por interpretacao de shell - antes ela era
# interpolada dentro de um `sh -c`, o que quebrava com senha contendo $ ou
# aspas.
if ! docker exec -e MYSQL_PWD="$DB_ROOT_PASSWORD" wecti-db \
       mysqldump --single-transaction --quick -u root "${DB_NAME:-wecti}" \
     | gzip > "$PARCIAL"; then
  rm -f "$PARCIAL"
  echo "ERRO: mysqldump falhou; nenhum arquivo gravado." >&2
  exit 1
fi

# Um dump interrompido no meio deixa um .gz truncado que parece um backup bom
# ate a hora de restaurar. So vira arquivo definitivo depois de passar aqui.
if ! gzip -t "$PARCIAL"; then
  rm -f "$PARCIAL"
  echo "ERRO: o arquivo gerado esta corrompido; descartado." >&2
  exit 1
fi

mv "$PARCIAL" "$FINAL"
echo "Backup salvo em $FINAL ($(du -h "$FINAL" | cut -f1))"

find "$BACKUP_DIR" -name '*.sql.gz' -mtime "+$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name '*.parcial' -mtime +1 -delete
