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

// Sem link/token por email de proposito (nao ha servidor de email
// configurado, e o professor confirmou que nao precisa de mais
// autenticacao) - a identidade e confirmada com o RGM (aluno) ou CPF
// (professor/admin) que ja foi cadastrado, e a pessoa define a senha
// nova na hora. Serve tambem pro Professor/Admin (cadastrados por outro
// Admin com uma senha provisoria que ninguem sabe) definirem a primeira
// senha de verdade.
const schema = z
  .object({
    email: z.string().min(1, 'Informe o email').email('Email inválido'),
    identificador: z.string().min(1, 'Informe seu RGM (aluno) ou CPF (professor/admin)'),
    novaSenha: z.string().min(6, 'A senha deve ter pelo menos 6 caracteres'),
    confirmarSenha: z.string().min(1, 'Confirme a senha'),
  })
  .refine((dados) => dados.novaSenha === dados.confirmarSenha, {
    message: 'As senhas não coincidem',
    path: ['confirmarSenha'],
  });

type FormValues = z.infer<typeof schema>;

export default function RecuperarSenhaPage() {
  const { redefinirSenha } = useAuth();
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
      const usuario = await redefinirSenha({
        email: dados.email,
        identificador: dados.identificador,
        nova_senha: dados.novaSenha,
      });
      // Volta para o que a pessoa tentava abrir (tipicamente o link do
      // QR de check-in). Mandar direto para /eventos fazia o aluno
      // perder a confirmacao de presenca depois de recuperar a conta.
      navigate(destinoAposEntrar(location.state, usuario.perfil), { replace: true });
    } catch (erro) {
      setErroGeral(extrairMensagemErro(erro, 'Não foi possível redefinir a senha. Verifique os dados.'));
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
            <h1 className="text-lg font-bold text-text">Esqueci minha senha</h1>
            <p className="mt-1 text-sm text-text-muted">
              Confirme seu email e RGM (aluno) ou CPF (professor/admin) para definir uma senha nova
            </p>
          </div>

          <FormField
            label="Email"
            type="email"
            autoComplete="email"
            placeholder="voce@unicid.edu.br"
            erro={errors.email?.message}
            registro={register('email')}
          />
          <FormField
            label="RGM ou CPF"
            erro={errors.identificador?.message}
            registro={register('identificador')}
          />
          <FormField
            label="Nova senha"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••"
            erro={errors.novaSenha?.message}
            registro={register('novaSenha')}
          />
          <FormField
            label="Confirmar nova senha"
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
            Redefinir senha
          </Button>

          <p className="text-center text-sm text-text-muted">
            Lembrou a senha?{' '}
            <Link to="/login" className="font-medium text-accent hover:underline">
              Entrar
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
