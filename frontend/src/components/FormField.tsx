import type { InputHTMLAttributes } from 'react';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  erro?: string;
  /** Dica curta abaixo do campo; some quando há erro, que ocupa o lugar. */
  ajuda?: string;
  registro?: object;
}

export default function FormField({ label, erro, ajuda, registro, className = '', ...rest }: Props) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="font-medium text-text">{label}</span>
      <input
        className={`rounded-lg border bg-surface px-3.5 py-2.5 text-text placeholder:text-text-muted/60 outline-none transition focus:border-accent ${
          erro ? 'border-red-500/60' : 'border-border'
        } ${className}`}
        {...registro}
        {...rest}
      />
      {erro ? (
        <span className="text-xs text-red-400">{erro}</span>
      ) : (
        ajuda && <span className="text-xs text-text-muted">{ajuda}</span>
      )}
    </label>
  );
}
