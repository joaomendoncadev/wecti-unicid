import { useCallback, useEffect, useState } from 'react';
import PageContainer from '../../components/PageContainer';
import Button from '../../components/Button';
import { LoadingBlock } from '../../components/LoadingSpinner';
import ErrorMessage from '../../components/ErrorMessage';
import EmptyState from '../../components/EmptyState';
import UsuarioFormModal from './UsuarioFormModal';
import { atualizarUsuario, criarUsuario, excluirUsuario, listarUsuarios } from '../../services/usuarios';
import { extrairMensagemErro } from '../../services/api';
import { useToast } from '../../context/ToastContext';
import type { NovoUsuario, Perfil, Usuario } from '../../types';

const LABEL_PERFIL: Record<string, string> = {
  ADMIN: 'Administrador',
  ALUNO: 'Aluno',
};

const TAMANHO_PAGINA = 20;

export default function AdminUsuariosPage() {
  const [usuarios, setUsuarios] = useState<Usuario[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [modalAberto, setModalAberto] = useState(false);
  const [usuarioEditando, setUsuarioEditando] = useState<Usuario | null>(null);
  const [excluindoId, setExcluindoId] = useState<string | null>(null);
  const { notificarSucesso, notificarErro } = useToast();

  // Filtros - crescem muito quando o sistema for liberado pros alunos,
  // por isso paginação em vez de carregar tudo de uma vez.
  const [filtroPerfil, setFiltroPerfil] = useState<Perfil | ''>('');
  const [buscaInput, setBuscaInput] = useState('');
  const [busca, setBusca] = useState('');
  const [pagina, setPagina] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [totalElementos, setTotalElementos] = useState(0);

  // Debounce da busca (400ms) - sem isso, cada tecla digitada disparava
  // uma requisição nova.
  useEffect(() => {
    const timer = setTimeout(() => {
      setBusca(buscaInput.trim());
      setPagina(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [buscaInput]);

  useEffect(() => {
    setPagina(0);
  }, [filtroPerfil]);

  const carregar = useCallback(() => {
    setCarregando(true);
    setErro(null);
    listarUsuarios({ perfil: filtroPerfil || undefined, busca: busca || undefined, page: pagina, size: TAMANHO_PAGINA })
      .then((resultado) => {
        setUsuarios(resultado.conteudo);
        setTotalPaginas(resultado.total_paginas);
        setTotalElementos(resultado.total_elementos);
      })
      .catch((e) => setErro(extrairMensagemErro(e, 'Não foi possível carregar os usuários.')))
      .finally(() => setCarregando(false));
  }, [filtroPerfil, busca, pagina]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const abrirNovo = () => {
    setUsuarioEditando(null);
    setModalAberto(true);
  };

  const abrirEdicao = (usuario: Usuario) => {
    setUsuarioEditando(usuario);
    setModalAberto(true);
  };

  const salvar = async (dados: NovoUsuario) => {
    try {
      if (usuarioEditando) {
        await atualizarUsuario(usuarioEditando.id, dados);
        notificarSucesso('Usuário atualizado.');
      } else {
        await criarUsuario(dados);
        notificarSucesso('Usuário criado.');
      }
      setModalAberto(false);
      carregar();
    } catch (e) {
      throw new Error(extrairMensagemErro(e, 'Não foi possível salvar o usuário.'));
    }
  };

  /**
   * Exclusão leva junto inscrições, presenças, certificados e pontos de
   * gincana do usuário - e nada disso volta. O aviso é explícito porque
   * o uso principal é apagar conta duplicada, e errar o alvo significa
   * destruir a conta boa de um aluno.
   *
   * Para conta duplicada, a ordem importa: lance as presenças na conta
   * CERTA (botão "Registrar presença", na tela de Check-in) antes de
   * apagar a errada - depois de apagar não há mais como saber em quais
   * palestras ele esteve.
   */
  const excluir = async (usuario: Usuario) => {
    const aviso =
      `Excluir "${usuario.nome}" definitivamente?\n\n` +
      'Serão apagados junto, sem possibilidade de desfazer:\n' +
      '• as inscrições dele\n' +
      '• os check-ins e check-outs\n' +
      '• os certificados já emitidos (um PDF baixado deixa de validar)\n' +
      '• os pontos de gincana lançados para ele\n\n' +
      'Se for conta duplicada: registre as presenças na conta correta ANTES de apagar esta.';
    if (!window.confirm(aviso)) return;

    setExcluindoId(usuario.id);
    try {
      await excluirUsuario(usuario.id);
      notificarSucesso(`"${usuario.nome}" foi excluído.`);
      carregar();
    } catch (e) {
      notificarErro(extrairMensagemErro(e, 'Não foi possível excluir o usuário.'));
    } finally {
      setExcluindoId(null);
    }
  };

  const semResultado = !carregando && !erro && usuarios.length === 0;
  const filtrosAtivos = Boolean(filtroPerfil || busca);

  return (
    <PageContainer
      titulo="Usuários"
      descricao="Alunos e administradores cadastrados"
      acao={<Button onClick={abrirNovo}>Novo usuário</Button>}
    >
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          type="search"
          value={buscaInput}
          onChange={(e) => setBuscaInput(e.target.value)}
          placeholder="Buscar por nome, email, RGM ou CPF..."
          className="flex-1 rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-text-muted/60 outline-none focus:border-accent"
        />
        <select
          value={filtroPerfil}
          onChange={(e) => setFiltroPerfil(e.target.value as Perfil | '')}
          className="rounded-lg border border-border bg-surface px-3.5 py-2.5 text-sm text-text outline-none focus:border-accent sm:w-48"
        >
          <option value="">Todos os perfis</option>
          <option value="ALUNO">Aluno</option>
          <option value="ADMIN">Administrador</option>
        </select>
      </div>

      {carregando && <LoadingBlock mensagem="Carregando usuários..." />}
      {!carregando && erro && <ErrorMessage mensagem={erro} onTentarNovamente={carregar} />}
      {semResultado && filtrosAtivos && (
        <EmptyState titulo="Nenhum usuário encontrado" descricao="Tente ajustar a busca ou o filtro de perfil." />
      )}
      {semResultado && !filtrosAtivos && (
        <EmptyState titulo="Nenhum usuário cadastrado" acao={<Button onClick={abrirNovo}>Cadastrar usuário</Button>} />
      )}
      {!carregando && !erro && usuarios.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-card border border-border bg-surface">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-border text-text-muted">
                  <th className="px-5 py-3 font-medium">Nome</th>
                  <th className="px-5 py-3 font-medium">Email</th>
                  <th className="px-5 py-3 font-medium">Perfil</th>
                  <th className="px-5 py-3 font-medium">RGM/CPF</th>
                  <th className="px-5 py-3 font-medium">Curso</th>
                  <th className="px-5 py-3 font-medium text-right">Ações</th>
                </tr>
              </thead>
              <tbody>
                {usuarios.map((usuario) => (
                  <tr key={usuario.id} className="border-b border-border last:border-0">
                    <td className="px-5 py-3 text-text">{usuario.nome}</td>
                    <td className="px-5 py-3 text-text-muted">{usuario.email}</td>
                    <td className="px-5 py-3 text-text-muted">{LABEL_PERFIL[usuario.perfil] ?? usuario.perfil}</td>
                    <td className="px-5 py-3 text-text-muted">{usuario.rgm ?? usuario.cpf ?? '-'}</td>
                    <td className="px-5 py-3 text-text-muted">{usuario.curso ?? '-'}</td>
                    <td className="px-5 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variante="outline" onClick={() => abrirEdicao(usuario)}>
                          Editar
                        </Button>
                        <Button
                          variante="danger"
                          carregando={excluindoId === usuario.id}
                          onClick={() => excluir(usuario)}
                        >
                          Excluir
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex flex-col items-center justify-between gap-3 sm:flex-row">
            <p className="text-sm text-text-muted">
              {totalElementos} usuário{totalElementos === 1 ? '' : 's'} - página {pagina + 1} de {Math.max(totalPaginas, 1)}
            </p>
            <div className="flex gap-2">
              <Button variante="outline" disabled={pagina === 0} onClick={() => setPagina((p) => Math.max(0, p - 1))}>
                Anterior
              </Button>
              <Button
                variante="outline"
                disabled={pagina + 1 >= totalPaginas}
                onClick={() => setPagina((p) => p + 1)}
              >
                Próxima
              </Button>
            </div>
          </div>
        </>
      )}

      {modalAberto && (
        <UsuarioFormModal usuario={usuarioEditando} onSalvar={salvar} onFechar={() => setModalAberto(false)} />
      )}
    </PageContainer>
  );
}
