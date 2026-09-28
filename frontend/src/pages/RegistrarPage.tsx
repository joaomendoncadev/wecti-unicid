import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Logo from '../components/Logo';
import Button from '../components/Button';
import FormField from '../components/FormField';
import { useAuth } from '../context/AuthContext';
import { extrairMensagemErro } from '../services/api';
import { destinoAposEntrar } from '../utils/rotas';

// Sem campo de perfil aqui de proposito: o cadastro publico e sempre pra
// ALUNO (confirmado com o professor) - Professor continua sendo
// cadastrado so pelo Admin, na tela interna de Usuarios, pra nao dar pra
// qualquer um se autopromover. Ver CadastroAlunoRequest no backend.
const schema = z
  .object({
    nome: z.string().min(1, 'Informe o nome'),
    email: z.string().min(1, 'Informe o email').email('Email inválido'),
    senha: z.string().min(6, 'A senha deve ter pelo menos 6 caracteres'),
    confirmarSenha: z.string().min(1, 'Confirme a senha'),
    rgm: z
      .string()
      .min(1, 'Informe o RGM')
      .regex(/^\d{8}$/, 'RGM deve ter exatamente 8 dígitos'),
    curso: z.string().optional(),
  })
  .refine((dados) => dados.senha === dados.confirmarSenha, {
    message: 'As senhas não coincidem',
    path: ['confirmarSenha'],
  });

type FormValues = z.infer<typeof schema>;

export default function RegistrarPage() {
  const { registrar } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (dados: FormValues) => {
    setErroGeral(null);
    setEnviando(true);
    try {
      const usuario = await registrar({
        nome: dados.nome,
        email: dados.email,
        senha: dados.senha,
        rgm: dados.rgm,
        curso: dados.curso || undefined,
      });
      // Mesmo motivo do login: quem se cadastrou a partir do link do
      // QR precisa voltar para ele, e nao para a lista de eventos.
      navigate(destinoAposEntrar(location.state, usuario.perfil), { replace: true });
    } catch (erro) {
      setErroGeral(extrairMensagemErro(erro, 'Não foi possível criar sua conta. Verifique os dados.'));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-bg px-4 py-12">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'radial-gradient(ellipse 80% 60% at 60% 40%, rgba(0,212,176,0.10) 0%, transparent 70%), radial-gradient(ellipse 60% 50% at 20% 70%, rgba(0,168,138,0.10) 0%, transparent 60%)',
        }}
      />

      <div className="relative z-10 w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="flex flex-col gap-4 rounded-card border border-border bg-surface p-8"
        >
          <div className="mb-2 text-center">
            <h1 className="text-lg font-bold text-text">Criar conta</h1>
            <p className="mt-1 text-sm text-text-muted">Cadastro de aluno - acesso a eventos e palestras</p>
          </div>

          <FormField label="Nome" autoComplete="name" erro={errors.nome?.message} registro={register('nome')} />
          <FormField
            label="Email"
            type="email"
            autoComplete="email"
            placeholder="voce@unicid.edu.br"
            erro={errors.email?.message}
            registro={register('email')}
          />
          <FormField
            label="RGM (8 dígitos)"
            maxLength={8}
            erro={errors.rgm?.message}
            registro={register('rgm')}
          />
          <FormField
            label="Curso (opcional)"
            placeholder="Ex.: Ciência da Computação"
            erro={errors.curso?.message}
            registro={register('curso')}
          />
          <FormField
            label="Senha"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••"
            erro={errors.senha?.message}
            registro={register('senha')}
          />
          <FormField
            label="Confirmar senha"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••"
            erro={errors.confirmarSenha?.message}
            registro={register('confirmarSenha')}
          />

          {erroGeral && (
            <p className="rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-sm text-red-400">
              {erroGeral}
            </p>
          )}

          <Button type="submit" carregando={enviando} className="mt-2 w-full">
            Criar conta
          </Button>

          <p className="text-center text-sm text-text-muted">
            Já tem conta?{' '}
            <Link to="/login" className="font-medium text-accent hover:underline">
              Entrar
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
