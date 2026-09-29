import { useEffect, useState } from 'react';
import Button from '../../components/Button';
import FormField from '../../components/FormField';
import { listarUsuarios } from '../../services/usuarios';
import { registrarPresencaManual } from '../../services/checkins';
import { extrairMensagemErro } from '../../services/api';
import type { Evento, Usuario } from '../../types';

interface Props {
  evento: Evento;
  onFechar: () => void;
  /** Chamado depois de registrar, para a lista de presença recarregar. */
  onRegistrado: (nomeDoAluno: string) => void;
}

/** Espera antes de buscar enquanto o admin ainda está digitando - sem
 *  isso, cada tecla vira uma chamada à API. */
const ESPERA_BUSCA_MS = 350;

/**
 * Admin afirma a presença de um aluno que participou da palestra e não
 * conseguiu ler o QR code - câmera com defeito, sinal fraco, ou conta
 * travada bem na hora do check-in.
 *
 * O que se registra aqui é a **presença**, não os pontos. Pontuação,
 * certificado e saída da lista de no-show derivam todos do check-in, então
 * gravá-lo faz as três coisas acontecerem sozinhas - e os pontos entram
 * como pontos da palestra, respeitando o teto de 2000, em vez de furá-lo
 * como fariam pontos extras de gincana.
 */
export default function RegistrarPresencaModal({ evento, onFechar, onRegistrado }: Props) {
  const [busca, setBusca] = useState('');
  const [resultados, setResultados] = useState<Usuario[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [selecionado, setSelecionado] = useState<Usuario | null>(null);

  const [justificativa, setJustificativa] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  // Busca só depois que o admin para de digitar, e ignora a resposta de
  // uma busca antiga que chegar atrasada (senão a lista pisca com o
  // resultado de um termo que já não está no campo).
  useEffect(() => {
    const termo = busca.trim();
    if (termo.length < 2) {
      setResultados([]);
      return;
    }

    let cancelado = false;
    setBuscando(true);
    const timer = setTimeout(() => {
      listarUsuarios({ perfil: 'ALUNO', busca: termo, size: 8 })
        .then((pagina) => {
          if (!cancelado) setResultados(pagina.conteudo);
        })
        .catch(() => {
          if (!cancelado) setResultados([]);
        })
        .finally(() => {
          if (!cancelado) setBuscando(false);
        });
    }, ESPERA_BUSCA_MS);

    return () => {
      cancelado = true;
      clearTimeout(timer);
    };
  }, [busca]);

  const registrar = async (e: React.FormEvent) => {
    e.preventDefault();
    setErro(null);

    if (!selecionado) {
      setErro('Escolha o aluno na lista acima.');
      return;
    }
    if (justificativa.trim().length === 0) {
      setErro('Explique o motivo - é a única explicação que vai sobrar para uma presença sem QR code.');
      return;
    }

    setSalvando(true);
    try {
      await registrarPresencaManual(evento.id, selecionado.id, justificativa.trim());
      onRegistrado(selecionado.nome);
    } catch (e2) {
      setErro(extrairMensagemErro(e2, 'Não foi possível registrar a presença.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 p-4" onClick={onFechar}>
      <div
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[90vh] w-full max-w-lg flex-col gap-4 overflow-y-auto rounded-card border border-border bg-surface p-6"
      >
        <div>
          <h2 className="text-lg font-bold text-text">Registrar presença</h2>
          <p className="text-sm text-text-muted">{evento.titulo}</p>
        </div>

        <p className="rounded-lg border border-accent/30 bg-accent/5 px-4 py-3 text-xs text-text-muted">
          Para quem participou da palestra e não conseguiu ler o QR code - de entrada, de saída ou os dois. O
          aluno passa a valer <span className="font-medium text-text">{evento.pontos} pontos</span> e consegue
          emitir o certificado. A entrada e a saída ficam com o horário do próprio evento.
        </p>

        <form onSubmit={registrar} className="flex flex-col gap-4">
          <FormField
            label="Aluno"
            value={busca}
            onChange={(e) => {
              setBusca(e.target.value);
              setSelecionado(null);
            }}
            placeholder="Busque por nome, RGM ou email"
            autoFocus
          />

          {selecionado ? (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-accent/40 bg-accent/5 px-4 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-text">{selecionado.nome}</p>
                <p className="text-xs text-text-muted">
                  {selecionado.rgm ? `RGM ${selecionado.rgm}` : 'sem RGM'} · {selecionado.email}
                </p>
              </div>
              <Button
                variante="outline"
                onClick={() => setSelecionado(null)}
                className="shrink-0 px-3 py-1.5 text-xs"
              >
                Trocar
              </Button>
            </div>
          ) : (
            <div className="flex flex-col gap-1.5">
              {buscando && <p className="text-sm text-text-muted">Buscando...</p>}

              {!buscando && busca.trim().length >= 2 && resultados.length === 0 && (
                <p className="text-sm text-text-muted">Nenhum aluno encontrado com esse termo.</p>
              )}

              {resultados.map((aluno) => (
                <button
                  key={aluno.id}
                  type="button"
                  onClick={() => setSelecionado(aluno)}
                  className="rounded-lg border border-border px-4 py-2.5 text-left transition-colors hover:border-accent hover:bg-accent/5"
                >
                  <p className="text-sm text-text">{aluno.nome}</p>
                  <p className="text-xs text-text-muted">
                    {aluno.rgm ? `RGM ${aluno.rgm}` : 'sem RGM'} · {aluno.email}
                  </p>
                </button>
              ))}
            </div>
          )}

          <FormField
            label="Motivo"
            value={justificativa}
            onChange={(e) => setJustificativa(e.target.value)}
            placeholder="Ex.: câmera do celular não lia o QR - presença conferida na porta"
            maxLength={200}
          />

          {erro && (
            <p className="rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-sm text-red-400">
              {erro}
            </p>
          )}

          <div className="flex justify-end gap-2">
            <Button variante="outline" onClick={onFechar} type="button">
              Cancelar
            </Button>
            <Button type="submit" carregando={salvando}>
              Registrar presença
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
