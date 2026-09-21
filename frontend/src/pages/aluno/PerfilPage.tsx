import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import FormField from '../../components/FormField';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import { atualizarMeuPerfil, buscarMeuUsuario } from '../../services/usuarios';
import { extrairMensagemErro } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import type { Usuario } from '../../types';

const LABEL_PERFIL: Record<string, string> = {
  ADMIN: 'Administrador',
  ALUNO: 'Aluno',
};

/*
 * Apesar de morar em pages/aluno, esta tela serve os DOIS perfis: a rota
 * /perfil é a única sem restrição de perfil (ver App.tsx). Cada um edita
 * o que é seu — aluno: RGM e curso; admin: CPF. Quem decide é o perfil
 * que vem da API, não o do token, para a tela refletir o cadastro real.
 */

const schema = z.object({
  nome: z.string().min(1, 'Informe o nome'),
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

const AJUDA_NUMERICO = 'Digite apenas números, sem pontos ou traços.';

export default function PerfilPage() {
  const { usuario: usuarioDaSessao, atualizarUsuario } = useAuth();
  const { notificarSucesso, notificarErro } = useToast();

  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [editando, setEditando] = useState(false);
  const [salvando, setSalvando] = useState(false);

  const ehAluno = usuario?.perfil === 'ALUNO';
  const ehAdmin = usuario?.perfil === 'ADMIN';

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    buscarMeuUsuario()
      .then((dados) => {
        setUsuario(dados);
        reset({ nome: dados.nome, rgm: dados.rgm ?? '', cpf: dados.cpf ?? '', curso: dados.curso ?? '' });
      })
      .catch((e) => setErro(extrairMensagemErro(e, 'Não foi possível carregar seu perfil.')))
      .finally(() => setCarregando(false));
  }, [reset]);

  const cancelar = () => {
    // Volta o formulário para os valores salvos: sem isso, reabrir a
    // edição depois de desistir traria o texto rascunhado de antes.
    if (usuario) {
      reset({ nome: usuario.nome, rgm: usuario.rgm ?? '', cpf: usuario.cpf ?? '', curso: usuario.curso ?? '' });
    }
    setEditando(false);
  };

  const onSubmit = async (dados: FormValues) => {
    setSalvando(true);
    try {
      const atualizado = await atualizarMeuPerfil({
        nome: dados.nome,
        // Cada perfil manda só o campo que é dele. Mandar o do outro
        // vazio faria o @Pattern do DTO recusar, mesmo sendo um campo
        // que o backend ignoraria de qualquer jeito.
        ...(ehAluno ? { rgm: dados.rgm, curso: dados.curso } : {}),
        ...(ehAdmin ? { cpf: dados.cpf } : {}),
      });
      setUsuario(atualizado);
      // A navbar mostra o nome a partir do usuário guardado na sessão,
      // não do token - sem isto ele só mudaria no próximo login.
      if (usuarioDaSessao?.id === atualizado.id) {
        atualizarUsuario(atualizado);
      }
      reset({
        nome: atualizado.nome,
        rgm: atualizado.rgm ?? '',
        cpf: atualizado.cpf ?? '',
        curso: atualizado.curso ?? '',
      });
      setEditando(false);
      notificarSucesso('Dados atualizados.');
    } catch (erroSalvar) {
      notificarErro(extrairMensagemErro(erroSalvar, 'Não foi possível salvar seus dados.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <PageContainer titulo="Meu perfil" descricao="Confira e corrija seus dados de cadastro">
      {carregando && <LoadingBlock mensagem="Carregando perfil..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} />}

      {!carregando && usuario && !editando && (
        <div className="max-w-md rounded-card border border-border bg-surface p-6">
          <dl className="flex flex-col gap-4">
            <div>
              <dt className="text-xs uppercase tracking-wider text-text-muted">Nome</dt>
              <dd className="mt-1 text-text">{usuario.nome}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wider text-text-muted">Email</dt>
              <dd className="mt-1 text-text">{usuario.email}</dd>
            </div>
            {ehAluno && (
              <>
                <div>
                  <dt className="text-xs uppercase tracking-wider text-text-muted">RGM</dt>
                  <dd className="mt-1 text-text">{usuario.rgm ?? '-'}</dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wider text-text-muted">Curso</dt>
                  <dd className="mt-1 text-text">{usuario.curso || '-'}</dd>
                </div>
              </>
            )}
            {ehAdmin && (
              <div>
                <dt className="text-xs uppercase tracking-wider text-text-muted">CPF</dt>
                <dd className="mt-1 text-text">{usuario.cpf ?? '-'}</dd>
              </div>
            )}
            <div>
              <dt className="text-xs uppercase tracking-wider text-text-muted">Perfil</dt>
              <dd className="mt-1 text-text">{LABEL_PERFIL[usuario.perfil] ?? usuario.perfil}</dd>
            </div>
          </dl>

          <Button className="mt-6 w-full" onClick={() => setEditando(true)}>
            Editar meus dados
          </Button>
        </div>
      )}

      {!carregando && usuario && editando && (
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="flex max-w-md flex-col gap-4 rounded-card border border-border bg-surface p-6"
        >
          <FormField
            label="Nome"
            registro={register('nome')}
            erro={errors.nome?.message}
            autoFocus
          />

          {ehAluno && (
            <>
              <FormField
                label="RGM"
                inputMode="numeric"
                maxLength={8}
                placeholder={AJUDA_NUMERICO}
                registro={register('rgm', {
                  validate: (valor) => RGM_VALIDO.test(valor ?? '') || 'RGM deve ter exatamente 8 dígitos',
                })}
                erro={errors.rgm?.message}
              />
              <FormField label="Curso" registro={register('curso')} erro={errors.curso?.message} />
            </>
          )}

          {ehAdmin && (
            <FormField
              label="CPF"
              inputMode="numeric"
              maxLength={11}
              placeholder={AJUDA_NUMERICO}
              registro={register('cpf', {
                validate: (valor) => CPF_VALIDO.test(valor ?? '') || 'CPF deve ter exatamente 11 dígitos',
              })}
              erro={errors.cpf?.message}
            />
          )}

          {/* Explica o que a tela NÃO faz, no lugar onde a pessoa vai
              procurar por isso, em vez de deixá-la caçar o botão. O
              admin precisa da ressalva extra: ele TEM como trocar o
              próprio e-mail, só que pela tela de Usuários. */}
          <p className="rounded-lg border border-border bg-bg/40 px-3.5 py-3 text-xs leading-relaxed text-text-muted">
            O e-mail é o seu login e não muda por aqui —{' '}
            {ehAdmin ? (
              <>
                use a tela de <span className="text-text">Usuários</span> se precisar
                corrigi-lo.
              </>
            ) : (
              <>fale com a organização se precisar corrigi-lo.</>
            )}{' '}
            Para trocar a senha, use{' '}
            <span className="text-text">&ldquo;Esqueci minha senha&rdquo;</span> na tela de acesso.
          </p>

          <div className="mt-2 flex gap-3">
            <Button type="submit" carregando={salvando} className="flex-1">
              Salvar
            </Button>
            <Button type="button" variante="outline" onClick={cancelar} className="flex-1">
              Cancelar
            </Button>
          </div>
        </form>
      )}
    </PageContainer>
  );
}
