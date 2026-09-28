import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import Logo from '../components/Logo';
import Button from '../components/Button';
import FormField from '../components/FormField';
import { useAuth } from '../context/AuthContext';
import { extrairMensagemErro } from '../services/api';
import { destinoAposEntrar, homeDoPerfil } from '../utils/rotas';

const schema = z.object({
  email: z.string().min(1, 'Informe o email').email('Email invalido'),
  senha: z.string().min(1, 'Informe a senha'),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const { entrar, autenticado, perfil } = useAuth();
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
      const usuario = await entrar(dados);
      // Se o usuario veio de uma rota protegida (ex.: link do QR code de
      // check-in escaneado sem estar logado), volta pra ela em vez de
      // sempre mandar pra home do perfil.
      navigate(destinoAposEntrar(location.state, usuario.perfil), { replace: true });
    } catch (erro) {
      setErroGeral(extrairMensagemErro(erro, 'Nao foi possivel entrar. Verifique seus dados.'));
    } finally {
      setEnviando(false);
    }
  };

  // A landing publica e a porta de entrada do site, e o botao "Login"
  // dela aponta para ca sem saber se a pessoa ja esta logada. Sem isto,
  // quem ja tem sessao ativa cairia num formulario de login pedindo a
  // senha de novo, sem motivo.
  //
  // Depois dos hooks de proposito: sair antes mudaria a quantidade de
  // hooks executados entre um render e outro, que e erro em React.
  if (autenticado) {
    return <Navigate to={homeDoPerfil(perfil)} replace />;
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-bg px-4">
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
            <h1 className="text-lg font-bold text-text">Entrar</h1>
            <p className="mt-1 text-sm text-text-muted">Acesse com seu email e senha</p>
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
            label="Senha"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            erro={errors.senha?.message}
            registro={register('senha')}
          />

          <Link
            to="/recuperar-senha"
            state={location.state}
            className="-mt-1 self-end text-xs text-text-muted hover:text-accent hover:underline"
          >
            Esqueceu a senha?
          </Link>

          {erroGeral && (
            <p className="rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-sm text-red-400">
              {erroGeral}
            </p>
          )}

          <Button type="submit" carregando={enviando} className="mt-2 w-full">
            Entrar
          </Button>

          <p className="text-center text-sm text-text-muted">
            Primeiro acesso?{' '}
            <Link to="/cadastro" state={location.state} className="font-medium text-accent hover:underline">
              Crie sua conta
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
