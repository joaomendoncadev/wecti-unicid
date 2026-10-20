# WECTI - Frontend

Frontend web do sistema de controle de acesso a palestras e eventos da
WECTI (UNICID). Consome a API REST em `backend/` - nenhum acesso direto ao
banco de dados.

## Stack

- React 19 + Vite + TypeScript
- Tailwind CSS v4
- React Router DOM v6
- Axios (com interceptor de JWT)
- React Hook Form + Zod

## Rodando localmente

```bash
npm install
npm run dev
```

A aplicação sobe em `http://localhost:5173` (padrão do Vite).

Por padrão consome a API em `http://localhost:8080` (backend local, ver
`backend/README.md`). Para apontar para outra URL, copie `.env.example`
para `.env.local` e ajuste:

```bash
cp .env.example .env.local
```

```
VITE_API_URL=http://localhost:8080
```

Em produção (build), `VITE_API_URL` deve apontar para
`http://jadir9152.c44.integrator.host`.

## Build de produção

```bash
npm run build
```

Gera os arquivos estáticos em `dist/`, prontos para servir por qualquer
servidor HTTP (nginx, Apache, etc. - não precisa de Node em produção).

## Perfis de usuário

O perfil do usuário logado (`ADMIN`, `PROFESSOR` ou `ALUNO`) é extraído do
JWT retornado por `POST /auth/login` e decide quais rotas/menus aparecem:

- **Aluno**: `/eventos`, `/minhas-inscricoes`, `/historico`,
  `/certificados`, `/pontuacao`, `/perfil`
- **Admin/Professor**: `/admin/eventos`, `/admin/usuarios`,
  `/admin/checkin`

## Estrutura

```
src/
├── components/   Navbar, ProtectedRoute, EventoCard, etc.
├── context/      AuthContext (sessão/JWT) e ToastContext (notificações)
├── hooks/        useEventos, useInscricoes, usePontuacao
├── pages/        páginas por perfil (aluno/ e admin/)
├── services/     chamadas Axios por recurso da API
├── types/        tipos espelhando os DTOs JSON do backend
└── utils/        formatação de data e download de arquivo
```

## Observação sobre o contrato da API

O backend serializa o enum `Perfil` em maiúsculas (`ADMIN`/`PROFESSOR`/
`ALUNO`), embora `docs/openapi.yaml` (raiz do repositório) documente esse
enum em minúsculas. O frontend segue o comportamento real da API (ver
`src/types/index.ts`) - vale alinhar o contrato depois.
