import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Button from '../../components/Button';
import FormField from '../../components/FormField';
import type { NovoUsuario, Usuario } from '../../types';

const schema = z
  .object({
    nome: z.string().min(1, 'Informe o nome'),
    email: z.string().min(1, 'Informe o email').email('Email inválido'),
    perfil: z.enum(['ADMIN', 'ALUNO']),
    rgm: z.string().optional(),
    cpf: z.string().optional(),
    // So informativo (confirmado com o professor) - sem formato exigido.
    curso: z.string().optional(),
  })
  .refine((dados) => dados.perfil !== 'ALUNO' || /^\d{8}$/.test(dados.rgm ?? ''), {
    message: 'RGM obrigatório (8 dígitos) para perfil Aluno',
    path: ['rgm'],
  })
  // CPF é o identificador usado no "esqueci minha senha"
  // (RecuperarSenhaPage) pro Admin - sem ele, um Admin cadastrado por
  // outro Admin nunca teria como definir a própria senha (a provisória
  // gerada no cadastro nunca é revelada pra ninguém).
  .refine((dados) => dados.perfil !== 'ADMIN' || /^\d{11}$/.test(dados.cpf ?? ''), {
    message: 'CPF obrigatório (11 dígitos) para perfil Administrador',
    path: ['cpf'],
  });

type FormValues = z.infer<typeof schema>;

const AJUDA_NUMERICO = 'Digite apenas números, sem pontos ou traços.';

interface Props {
  usuario?: Usuario | null;
  onSalvar: (dados: NovoUsuario) => Promise<void>;
  onFechar: () => void;
}

export default function UsuarioFormModal({ usuario, onSalvar, onFechar }: Props) {
  const [salvando, setSalvando] = useState(false);
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  // Ajuda ("digite só números...") só aparece enquanto o campo está
  // focado, como pedido - não fica poluindo a tela o tempo todo.
  const [campoFocado, setCampoFocado] = useState<'rgm' | 'cpf' | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    // Sem isso, trocar o Perfil no meio do preenchimento (ex.: Aluno ->
    // Admin) deixava um valor "" órfão em rgm/cpf - o campo some da tela
    // quando o perfil muda, mas o React Hook Form mantém o valor antigo
    // registrado por padrão, então esse "" ia junto no corpo da
    // requisição. O backend rejeita isso (o @Pattern do DTO valida ""
    // contra o formato, mesmo não sendo obrigatório pra esse perfil) -
    // shouldUnregister remove o valor do formulário assim que o campo
    // desmonta, corrigindo na raiz em vez de tratar cada campo.
    shouldUnregister: true,
    defaultValues: usuario
      ? {
          nome: usuario.nome,
          email: usuario.email,
          perfil: usuario.perfil,
          rgm: usuario.rgm ?? '',
          cpf: usuario.cpf ?? '',
          curso: usuario.curso ?? '',
        }
      : { perfil: 'ALUNO' },
  });

  const perfil = watch('perfil');

  /**
   * Manda só o campo de identificador do perfil escolhido, e nunca uma
   * string vazia.
   *
   * Sem isto, editar QUALQUER cadastro existente falhava: os
   * `defaultValues` transformam o campo ausente em `''` (`usuario.cpf ??
   * ''`), e esse `''` ia no corpo da requisição mesmo quando o campo
   * nunca chegou a aparecer na tela - `shouldUnregister` só limpa o que
   * foi montado e depois desmontado, não o que nunca montou. Do outro
   * lado, o `@Pattern` do backend valida o formato de toda string
   * presente, então recusava `""` com "CPF deve ter exatamente 11
   * digitos" num formulário de aluno que nem tem campo de CPF.
   */
  const onSubmit = async (dados: FormValues) => {
    setErroGeral(null);
    setSalvando(true);
    try {
      const ehAluno = dados.perfil === 'ALUNO';
      await onSalvar({
        nome: dados.nome,
        email: dados.email,
        perfil: dados.perfil,
        rgm: ehAluno ? dados.rgm : undefined,
        cpf: ehAluno ? undefined : dados.cpf,
        curso: ehAluno ? dados.curso || undefined : undefined,
      });
    } catch (e) {
      setErroGeral(e instanceof Error ? e.message : 'Não foi possível salvar o usuário.');
    } finally {
      setSalvando(false);
    }
  };

  const { onBlur: onBlurRgm, ...registroRgm } = register('rgm');
  const { onBlur: onBlurCpf, ...registroCpf } = register('cpf');

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 p-4" onClick={onFechar}>
      <form
        onSubmit={handleSubmit(onSubmit)}
        onClick={(e) => e.stopPropagation()}
        className="flex w-full max-w-md flex-col gap-4 rounded-card border border-border bg-surface p-6"
      >
        <h2 className="text-lg font-bold text-text">{usuario ? 'Editar usuário' : 'Novo usuário'}</h2>

        {/* Perfil primeiro - os campos específicos de cada perfil só
         *  fazem sentido depois de saber qual foi escolhido. Só Aluno e
         *  Admin (confirmado com o stakeholder do projeto). */}
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium text-text">Perfil</span>
          <select
            className="rounded-lg border border-border bg-surface px-3.5 py-2.5 text-text outline-none focus:border-accent"
            {...register('perfil')}
          >
            <option value="ALUNO">Aluno</option>
            <option value="ADMIN">Administrador</option>
          </select>
        </label>

        <FormField label="Nome" erro={errors.nome?.message} registro={register('nome')} />
        <FormField label="Email" type="email" erro={errors.email?.message} registro={register('email')} />

        {perfil === 'ALUNO' && (
          <>
            <div>
              <FormField
                label="RGM (8 dígitos)"
                maxLength={8}
                erro={errors.rgm?.message}
                registro={registroRgm}
                onFocus={() => setCampoFocado('rgm')}
                onBlur={(e) => {
                  setCampoFocado(null);
                  onBlurRgm(e);
                }}
              />
              {campoFocado === 'rgm' && <p className="mt-1 text-xs text-text-muted">{AJUDA_NUMERICO}</p>}
            </div>
            <FormField
              label="Curso (opcional)"
              placeholder="Ex.: Ciência da Computação"
              erro={errors.curso?.message}
              registro={register('curso')}
            />
          </>
        )}

        {perfil === 'ADMIN' && (
          <div>
            <FormField
              label="CPF (11 dígitos)"
              maxLength={11}
              erro={errors.cpf?.message}
              registro={registroCpf}
              onFocus={() => setCampoFocado('cpf')}
              onBlur={(e) => {
                setCampoFocado(null);
                onBlurCpf(e);
              }}
            />
            {campoFocado === 'cpf' && <p className="mt-1 text-xs text-text-muted">{AJUDA_NUMERICO}</p>}
          </div>
        )}

        {erroGeral && (
          <p className="rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-sm text-red-400">{erroGeral}</p>
        )}

        <div className="mt-2 flex justify-end gap-3">
          <Button type="button" variante="outline" onClick={onFechar}>
            Cancelar
          </Button>
          <Button type="submit" carregando={salvando}>
            Salvar
          </Button>
        </div>
      </form>
    </div>
  );
}
