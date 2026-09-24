const ESTILOS: Record<string, string> = {
  ativa: 'bg-accent/10 text-accent border-accent/30',
  concluido: 'bg-accent/10 text-accent border-accent/30',
  cancelada: 'bg-white/5 text-text-muted border-border',
  cancelado: 'bg-white/5 text-text-muted border-border',
  // Neutro, e nao vermelho: desde setembro de 2026 faltar nao tira
  // ponto nenhum, entao alarmar o aluno com cor de erro seria mentir
  // sobre a consequencia.
  no_show: 'bg-white/5 text-text-muted border-border',
};

const LABELS: Record<string, string> = {
  ativa: 'Ativa',
  concluido: 'Concluído',
  cancelada: 'Cancelada',
  cancelado: 'Cancelado',
  no_show: 'Não compareceu',
};

export default function Badge({ status }: { status: string }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${
        ESTILOS[status] ?? 'border-border bg-white/5 text-text-muted'
      }`}
    >
      {LABELS[status] ?? status}
    </span>
  );
}
