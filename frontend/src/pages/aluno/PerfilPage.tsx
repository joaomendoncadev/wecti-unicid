import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import FormField from '../../components/FormField';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import { atualizarMeuPerfil, buscarMeuUsuario } from '../../services/usuarios';
import { buscarMinhaPontuacao } from '../../services/pontuacao';
import { extrairMensagemErro } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import type { Usuario } from '../../types';

/*
 * Apesar de morar em pages/aluno, esta tela serve os DOIS perfis: a rota
 * /perfil é a única sem restrição de perfil (ver App.tsx). Cada um edita
 * o que é seu — aluno: RGM e curso; admin: CPF. Quem decide é o perfil
 * que vem da API, não o do token, para a tela refletir o cadastro real.
 *
 * O crachá à esquerda resume o cadastro e acompanha o formulário
 * enquanto a pessoa digita — assim ela vê o
 * resultado antes de salvar, em vez de descobrir o erro depois.
 */

const schema = z.object({
  nome: z.string().trim().min(1, 'Informe o nome'),
  rgm: z.string().optional(),
  cpf: z.string().optional(),
  // Só informativo (confirmado com o professor) - sem formato exigido.
  curso: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

/** Mesmos formatos exigidos pelo backend. Validados aqui só para a
 *  pessoa ver o erro antes de enviar; quem garante é o servidor. */
const RGM_VALIDO = /^\d{8}$/;
const CPF_VALIDO = /^\d{11}$/;

function valoresDe(usuario: Usuario): FormValues {
  return { nome: usuario.nome, rgm: usuario.rgm ?? '', cpf: usuario.cpf ?? '', curso: usuario.curso ?? '' };
}

export default function PerfilPage() {
  const { usuario: usuarioDaSessao, atualizarUsuario } = useAuth();
  const { notificarSucesso, notificarErro } = useToast();

  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [pontos, setPontos] = useState<number | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const ehAluno = usuario?.perfil === 'ALUNO';
  const ehAdmin = usuario?.perfil === 'ADMIN';

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    control,
    formState: { errors, isDirty },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const rascunho = useWatch({ control });

  /** RGM e CPF são só dígitos: descartar o resto na digitação evita o
   *  erro de formato para quem cola "123.456.789-00". */
  const somenteDigitos = (campo: 'rgm' | 'cpf', limite: number) => (e: { target: { value: string } }) => {
    const limpo = e.target.value.replace(/\D/g, '').slice(0, limite);
    if (limpo !== e.target.value) setValue(campo, limpo, { shouldDirty: true });
  };

  useEffect(() => {
    buscarMeuUsuario()
      .then((dados) => {
        setUsuario(dados);
        reset(valoresDe(dados));
        // Pontos só existem para aluno (o admin levaria 403). Se a
        // chamada falhar, o crachá simplesmente não mostra a linha -
        // não é motivo para travar a tela de cadastro.
        if (dados.perfil === 'ALUNO') {
          buscarMinhaPontuacao()
            .then((p) => setPontos(p.pontos_total))
            .catch(() => setPontos(null));
        }
      })
      .catch((e) => setErro(extrairMensagemErro(e, 'Não foi possível carregar seu perfil.')))
      .finally(() => setCarregando(false));
  }, [reset]);

  // Sair da página com alteração pendente perde o que foi digitado sem
  // aviso nenhum. O navegador mostra a própria mensagem de confirmação.
  useEffect(() => {
    if (!isDirty) return;
    const avisar = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', avisar);
    return () => window.removeEventListener('beforeunload', avisar);
  }, [isDirty]);

  const descartar = () => {
    if (usuario) reset(valoresDe(usuario));
  };

  const onSubmit = async (dados: FormValues) => {
    setSalvando(true);
    try {
      const atualizado = await atualizarMeuPerfil({
        nome: dados.nome.trim(),
        // Cada perfil manda só o campo que é dele. Mandar o do outro
        // vazio faria o @Pattern do DTO recusar, mesmo sendo um campo
        // que o backend ignoraria de qualquer jeito.
        ...(ehAluno ? { rgm: dados.rgm, curso: dados.curso?.trim() } : {}),
        ...(ehAdmin ? { cpf: dados.cpf } : {}),
      });
      setUsuario(atualizado);
      // A navbar mostra o nome a partir do usuário guardado na sessão,
      // não do token - sem isto ele só mudaria no próximo login.
      if (usuarioDaSessao?.id === atualizado.id) {
        atualizarUsuario(atualizado);
      }
      reset(valoresDe(atualizado));
      notificarSucesso('Alterações salvas.');
    } catch (erroSalvar) {
      notificarErro(extrairMensagemErro(erroSalvar, 'Não foi possível salvar as alterações.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <PageContainer titulo="Meu perfil" descricao="Seus dados de cadastro no WECTI">
      {carregando && <LoadingBlock mensagem="Carregando perfil..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} />}

      {!carregando && usuario && (
        <div className="grid items-start gap-8 lg:grid-cols-[320px_minmax(0,1fr)] lg:gap-12">
          <div className="mx-auto w-full max-w-[320px] lg:sticky lg:top-24">
            <Cracha
              perfil={usuario.perfil}
              nome={rascunho.nome ?? usuario.nome}
              curso={rascunho.curso ?? usuario.curso ?? ''}
              rgm={rascunho.rgm ?? usuario.rgm ?? ''}
              cpf={rascunho.cpf ?? usuario.cpf ?? ''}
              pontos={pontos}
            />
            <p className="mt-4 text-center text-xs text-text-muted">
              {isDirty ? 'Pré-visualização — salve para aplicar.' : 'O crachá muda conforme você edita.'}
            </p>
          </div>

          <form
            onSubmit={handleSubmit(onSubmit)}
            noValidate
            className="overflow-hidden rounded-card border border-border bg-surface"
          >
            <Secao
              titulo="Como você aparece"
              descricao={
                ehAluno
                  ? 'Nome usado na lista de presença e impresso no certificado.'
                  : 'Nome exibido para quem usa o sistema.'
              }
            >
              <FormField label="Nome completo" autoComplete="name" registro={register('nome')} erro={errors.nome?.message} />
              {ehAluno && (
                <FormField
                  label="Curso"
                  placeholder="Ex.: Ciência da Computação"
                  ajuda="Opcional."
                  registro={register('curso')}
                  erro={errors.curso?.message}
                />
              )}
            </Secao>

            {ehAluno && (
              <Secao titulo="Matrícula" descricao="Seu número de matrícula. Também confirma sua identidade ao redefinir a senha.">
                <FormField
                  label="RGM"
                  inputMode="numeric"
                  autoComplete="off"
                  className="font-mono tracking-[0.2em]"
                  ajuda="8 dígitos, só números."
                  registro={register('rgm', {
                    onChange: somenteDigitos('rgm', 8),
                    validate: (valor) => RGM_VALIDO.test(valor ?? '') || 'O RGM tem exatamente 8 dígitos.',
                  })}
                  erro={errors.rgm?.message}
                />
              </Secao>
            )}

            {ehAdmin && (
              <Secao titulo="Documento" descricao="Confirma sua identidade na recuperação de senha.">
                <FormField
                  label="CPF"
                  inputMode="numeric"
                  autoComplete="off"
                  className="font-mono tracking-[0.15em]"
                  ajuda="11 dígitos, só números."
                  registro={register('cpf', {
                    onChange: somenteDigitos('cpf', 11),
                    validate: (valor) => CPF_VALIDO.test(valor ?? '') || 'O CPF tem exatamente 11 dígitos.',
                  })}
                  erro={errors.cpf?.message}
                />
              </Secao>
            )}

            {/* Explica o que a tela NÃO faz, no lugar onde a pessoa vai
                procurar por isso, em vez de deixá-la caçar o botão. O
                admin precisa da ressalva extra: ele TEM como trocar o
                próprio e-mail, só que pela tela de Usuários. */}
            <Secao titulo="Acesso à conta" descricao="Estes dados não mudam por aqui.">
              <LinhaBloqueada
                rotulo="E-mail"
                valor={usuario.email}
                explicacao={
                  ehAdmin ? (
                    <>
                      É o seu login. Para corrigir, use a tela de{' '}
                      <Link to="/admin/usuarios" className="text-accent hover:underline">
                        Usuários
                      </Link>
                      .
                    </>
                  ) : (
                    'É o seu login. Para corrigir, fale com a organização do evento.'
                  )
                }
              />
              <LinhaBloqueada
                rotulo="Senha"
                valor="••••••••"
                explicacao={
                  <>
                    Para trocar, use{' '}
                    <Link to="/recuperar-senha" className="text-accent hover:underline">
                      Esqueci minha senha
                    </Link>
                    {ehAluno ? ' — você confirma com e-mail e RGM.' : ' — você confirma com e-mail e CPF.'}
                  </>
                }
              />
            </Secao>

            <div className="flex flex-col-reverse gap-3 border-t border-border bg-surface-2/40 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs text-text-muted" aria-live="polite">
                {isDirty ? (
                  <span className="inline-flex items-center gap-2 text-amber-300">
                    <span className="h-1.5 w-1.5 rounded-full bg-amber-300" aria-hidden />
                    Alterações não salvas
                  </span>
                ) : (
                  'Tudo salvo.'
                )}
              </p>
              <div className="flex flex-col-reverse gap-3 sm:flex-row">
                <Button type="button" variante="outline" onClick={descartar} disabled={!isDirty || salvando} className="w-full sm:w-auto">
                  Descartar
                </Button>
                <Button type="submit" carregando={salvando} disabled={!isDirty} className="w-full whitespace-nowrap sm:w-auto">
                  Salvar alterações
                </Button>
              </div>
            </div>
          </form>
        </div>
      )}
    </PageContainer>
  );
}

function Secao({ titulo, descricao, children }: { titulo: string; descricao: string; children: ReactNode }) {
  return (
    <section className="grid gap-4 border-b border-border px-6 py-6 last-of-type:border-b-0 md:grid-cols-[200px_minmax(0,1fr)] md:gap-8">
      <div>
        <h2 className="text-sm font-semibold text-text">{titulo}</h2>
        <p className="mt-1 text-xs leading-relaxed text-text-muted">{descricao}</p>
      </div>
      <div className="flex flex-col gap-4">{children}</div>
    </section>
  );
}

function LinhaBloqueada({ rotulo, valor, explicacao }: { rotulo: string; valor: string; explicacao: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5 text-sm">
      <span className="font-medium text-text">{rotulo}</span>
      <div className="flex items-center gap-3 rounded-lg border border-dashed border-border bg-bg/40 px-3.5 py-2.5 text-text-muted">
        <IconeCadeado />
        <span className="min-w-0 truncate">{valor}</span>
      </div>
      <span className="text-xs text-text-muted">{explicacao}</span>
    </div>
  );
}

function IconeCadeado() {
  return (
    <svg viewBox="0 0 16 16" className="h-3.5 w-3.5 shrink-0" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden>
      <rect x="3" y="7" width="10" height="7" rx="1.5" />
      <path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" />
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Crachá                                                              */
/* ------------------------------------------------------------------ */

interface CrachaProps {
  perfil: Usuario['perfil'];
  nome: string;
  curso: string;
  rgm: string;
  cpf: string;
  pontos: number | null;
}

function iniciais(nome: string) {
  const partes = nome.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '?';
  const primeira = partes[0][0];
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (primeira + ultima).toUpperCase();
}

/** "12345678" → "1234 5678": agrupar em quatro facilita conferir. */
function formatarRgm(rgm: string) {
  return rgm.replace(/(\d{4})(?=\d)/g, '$1 ');
}

/** Só o miolo do CPF fica visível no crachá — ele aparece na tela, e
 *  tela pode estar sendo projetada ou fotografada. */
function mascararCpf(cpf: string) {
  if (!CPF_VALIDO.test(cpf)) return '•••.•••.•••-••';
  return `•••.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-••`;
}

function Cracha({ perfil, nome, curso, rgm, cpf, pontos }: CrachaProps) {
  const ehAdmin = perfil === 'ADMIN';
  const nomeExibido = nome.trim() || 'Seu nome';
  const rgmCompleto = RGM_VALIDO.test(rgm);

  return (
    <div className="animate-cracha-in relative">
      {/* Fita do crachá */}
      <div className="mx-auto h-10 w-8 rounded-b-sm bg-gradient-to-b from-transparent via-accent/25 to-accent/40" aria-hidden />

      <article
        aria-label="Pré-visualização do crachá"
        className="relative -mt-1 overflow-hidden rounded-2xl border border-white/10 bg-surface-2 shadow-[0_24px_60px_-20px_rgba(0,232,196,0.25)]"
      >
        {/* Brilho do topo, na cor do evento */}
        <div
          className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-[radial-gradient(120%_100%_at_50%_0%,rgba(0,232,196,0.18),transparent_70%)]"
          aria-hidden
        />

        {/* Furo da presilha */}
        <div className="relative mx-auto mt-4 h-2 w-14 rounded-full bg-bg ring-1 ring-white/10" aria-hidden />

        <div className="relative px-6 pb-6 pt-5">
          <div className="flex items-center justify-between font-mono text-[10px] uppercase tracking-[0.2em] text-text-muted">
            <span className="text-accent">WECTI</span>
            <span>{ehAdmin ? 'Organização' : 'Participante'}</span>
          </div>

          <div className="mt-6 flex justify-center">
            <div className="rounded-full bg-gradient-to-br from-accent to-accent-dim p-[2px]">
              <div className="flex h-20 w-20 items-center justify-center rounded-full bg-surface text-2xl font-bold tracking-wide text-text">
                {iniciais(nome)}
              </div>
            </div>
          </div>

          <div className="mt-5 text-center">
            <p className={`break-words text-xl font-bold leading-tight ${nome.trim() ? 'text-text' : 'text-text-muted'}`}>
              {nomeExibido}
            </p>
            <p className="mt-1 min-h-5 text-sm text-text-muted">
              {ehAdmin ? 'Administrador' : curso.trim() || 'Curso não informado'}
            </p>
          </div>

          {/* Picote: separa quem a pessoa é do número que a identifica */}
          <div className="relative -mx-6 my-6" aria-hidden>
            <div className="absolute -left-2.5 top-1/2 h-5 w-5 -translate-y-1/2 rounded-full bg-bg" />
            <div className="absolute -right-2.5 top-1/2 h-5 w-5 -translate-y-1/2 rounded-full bg-bg" />
            <div className="mx-5 border-t border-dashed border-white/15" />
          </div>

          <div className="flex items-end justify-between gap-4">
            <div className="min-w-0">
              <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-text-muted">{ehAdmin ? 'CPF' : 'RGM'}</p>
              <p className="mt-1 font-mono text-lg font-medium tracking-wider text-text">
                {ehAdmin ? mascararCpf(cpf) : rgm ? formatarRgm(rgm) : '———— ————'}
              </p>
            </div>
            {!ehAdmin && pontos !== null && (
              <div className="shrink-0 text-right">
                <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-text-muted">Pontos</p>
                <p className="mt-1 text-lg font-bold text-accent">{pontos}</p>
              </div>
            )}
          </div>

          {!ehAdmin && (
            <div className="mt-5 h-12">
              {rgmCompleto ? (
                <CodigoBarras digitos={rgm} />
              ) : (
                <div className="flex h-full items-center justify-center rounded-md border border-dashed border-white/10 text-[11px] text-text-muted">
                  RGM incompleto
                </div>
              )}
            </div>
          )}
        </div>
      </article>
    </div>
  );
}

/*
 * Barras montadas a partir dos próprios dígitos do RGM, no padrão de
 * módulos do EAN-8 (codificação L na primeira metade, R na segunda, com
 * as guardas). É ilustrativo: o RGM não tem dígito verificador, então
 * leitor nenhum aceitaria — mas cada RGM gera um desenho diferente, e o
 * crachá muda de verdade quando o número muda.
 */
const CODIGO_L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011'];
const inverter = (padrao: string) => padrao.replace(/[01]/g, (b) => (b === '0' ? '1' : '0'));

function CodigoBarras({ digitos }: { digitos: string }) {
  const numeros = digitos.split('').map(Number);
  const modulos =
    '101' +
    numeros.slice(0, 4).map((d) => CODIGO_L[d]).join('') +
    '01010' +
    numeros.slice(4).map((d) => inverter(CODIGO_L[d])).join('') +
    '101';

  return (
    <svg
      viewBox={`0 0 ${modulos.length} 10`}
      preserveAspectRatio="none"
      className="h-full w-full text-text/80"
      aria-hidden
    >
      {modulos.split('').map((bit, i) =>
        bit === '1' ? <rect key={i} x={i} y={0} width={1} height={10} fill="currentColor" /> : null,
      )}
    </svg>
  );
}
