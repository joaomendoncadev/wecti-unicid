import { useEffect, useState } from 'react';

interface Props {
  /** PNG do QR em base64, como a API devolve. */
  pngBase64: string;
  /** Instante em que este QR deixa de ser o exibido (`codigo_expira_em`). */
  expiraEm: string;
  /** Duração de uma janela inteira, em segundos (`janela_segundos`). */
  janelaSegundos: number;
}

const LADO = 288; // 18rem - o mesmo tamanho que o QR já tinha
const BORDA = 6;
/** Distância entre a moldura e o quadrado branco do QR. */
const FOLGA = 14;

function formatarRestante(segundos: number): string {
  if (segundos <= 0) return 'renovando...';
  const min = Math.floor(segundos / 60);
  const seg = segundos % 60;
  if (min === 0) return `${seg}s`;
  return `${min}:${String(seg).padStart(2, '0')}`;
}

/**
 * QR de check-in com uma moldura que esvazia conforme o tempo passa.
 *
 * Mostra quanto falta para **este** QR ser trocado pelo próximo. Quando
 * zera, a tela busca o seguinte sozinha e a moldura enche de novo - o
 * admin não precisa fazer nada.
 *
 * Duas decisões que valem explicação:
 *
 * - **O denominador é a janela inteira, não o tempo que sobrava quando a
 *   tela abriu.** As janelas são alinhadas ao relógio (trocam em :00,
 *   :15, :30, :45), então um QR gerado às 14:14 vive só 1 minuto. Se a
 *   barra usasse esse minuto como total, ela apareceria cheia e
 *   despencaria, dando a impressão de que algo está errado. Com a janela
 *   como denominador, ela aparece quase vazia - que é a verdade.
 *
 * - **Zerar não significa que o aluno perdeu o check-in.** O servidor
 *   aceita também o código da janela anterior, então quem fotografou a
 *   tela ainda tem, no mínimo, uma janela inteira para confirmar. Por
 *   isso a moldura muda de cor no fim em vez de virar alerta vermelho:
 *   não é um prazo fatal, é a hora da troca.
 */
export default function QrCodeComTempo({ pngBase64, expiraEm, janelaSegundos }: Props) {
  const [restanteMs, setRestanteMs] = useState(() =>
    Math.max(0, new Date(expiraEm).getTime() - Date.now()),
  );

  useEffect(() => {
    const calcular = () => setRestanteMs(Math.max(0, new Date(expiraEm).getTime() - Date.now()));
    calcular();
    // 1s basta: a janela é de minutos, e um timer mais rápido só gastaria
    // renderização numa tela que fica horas aberta e projetada.
    const timer = setInterval(calcular, 1000);
    return () => clearInterval(timer);
  }, [expiraEm]);

  const totalMs = janelaSegundos * 1000;
  const fracao = totalMs > 0 ? Math.min(1, Math.max(0, restanteMs / totalMs)) : 0;
  const segundosRestantes = Math.ceil(restanteMs / 1000);

  // Retângulo arredondado: o traço percorre o perímetro, e dashoffset
  // "apaga" a parte já consumida.
  const raio = 16;
  const lado = LADO - BORDA;
  const perimetro = 2 * (lado - 2 * raio) * 2 + 2 * Math.PI * raio;

  // Aviso nos últimos 30s, mas nunca em mais de 10% da janela: com a
  // janela de 15 min isso dá os 30s mesmo; numa janela curta (que dá
  // para configurar) um limiar fixo deixaria metade do tempo em cor de
  // alerta, e alerta que fica aceso o tempo todo ninguém mais lê.
  const limiarAviso = Math.min(30, janelaSegundos * 0.1);
  const acabando = segundosRestantes <= limiarAviso;

  return (
    <div className="flex flex-col items-center gap-2">
      <div className="relative" style={{ width: LADO, height: LADO }}>
        <svg
          className="absolute inset-0 -rotate-90"
          width={LADO}
          height={LADO}
          viewBox={`0 0 ${LADO} ${LADO}`}
          aria-hidden
        >
          {/* Trilho. Cor própria em vez de `text-border`: aquele token é
              quase transparente (8% de branco) e sumia no fundo escuro,
              dando a impressão de que a moldura tinha desaparecido em vez
              de estar vazia. */}
          <rect
            x={BORDA / 2}
            y={BORDA / 2}
            width={lado}
            height={lado}
            rx={raio}
            fill="none"
            stroke="rgba(255,255,255,0.18)"
            strokeWidth={BORDA}
          />
          {/* tempo restante */}
          <rect
            x={BORDA / 2}
            y={BORDA / 2}
            width={lado}
            height={lado}
            rx={raio}
            fill="none"
            stroke="currentColor"
            strokeWidth={BORDA}
            strokeLinecap="round"
            strokeDasharray={perimetro}
            strokeDashoffset={perimetro * (1 - fracao)}
            className={acabando ? 'text-amber-400' : 'text-accent'}
            style={{ transition: 'stroke-dashoffset 1s linear, color 0.4s' }}
          />
        </svg>

        {/* O <img> vai dentro de uma div posicionada, e não posicionado
            ele mesmo: imagem é elemento substituído, então com `inset` e
            `width: auto` ela usa a largura intrínseca do PNG e ignora o
            recuo - o QR vazava por cima da moldura e do rótulo. */}
        <div className="absolute" style={{ inset: FOLGA }}>
          <img
            src={`data:image/png;base64,${pngBase64}`}
            alt="QR code de check-in"
            className="h-full w-full rounded-lg bg-white p-3"
          />
        </div>
      </div>

      <p
        className={`text-xs font-medium tabular-nums ${acabando ? 'text-amber-400' : 'text-text-muted'}`}
        aria-live="off"
      >
        Este QR muda em {formatarRestante(segundosRestantes)}
      </p>
    </div>
  );
}
