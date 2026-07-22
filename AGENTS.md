# Repository Guidelines

## Project Structure & Module Organization

This repository is organized as a multi-part application. There are two separate React/TypeScript/Vite clients: `frontend-user/` (the citizen-facing app) and `frontend-admin/` (the admin console). Each is an independent Vite project with its own `package.json`; there is no shared workspace tooling between them. Application code for each lives under `<project>/src/`; place reusable images and imported assets in `<project>/src/assets/`, and files that must be served unchanged in `<project>/public/`. Vite writes production output to `<project>/dist/`, which should not be committed. Common code is intentionally duplicated between the two projects for now — do not introduce a shared package until real duplication becomes a problem.

The root-level `backend/`, `infra/`, and `docs/` directories are reserved for the Spring Boot service, Docker/Nginx/OpenVidu configuration, and project documentation respectively. Keep changes within the relevant area and document new top-level components in `README.md`.

## Build, Test, and Development Commands

Run frontend commands from `frontend-user/` or `frontend-admin/` (each project is independent, so run these separately in each):

- `npm ci` installs the exact dependency versions from `package-lock.json`.
- `npm run dev` starts the Vite development server with hot reload.
- `npm run build` type-checks the project and creates a production bundle in `dist/`.
- `npm run lint` checks TypeScript and React code with ESLint.
- `npm run preview` serves the production bundle for a local smoke test.

## Coding Style & Naming Conventions

Follow the existing TypeScript style: two-space indentation, single quotes, and no semicolons. Use PascalCase for React components and component files (`UserCard.tsx`), camelCase for functions and variables, and descriptive lowercase names for CSS classes. Keep component-specific styles near their component and prefer explicit asset imports. TypeScript enables unused-code and fallthrough checks; resolve these rather than suppressing them. Run `npm run lint` before committing.

## Testing Guidelines

No automated test framework or coverage threshold is configured yet. Until one is added, validate every change with `npm run lint`, `npm run build`, and a browser smoke test via `npm run dev`. When introducing tests, colocate them with source files using `*.test.ts` or `*.test.tsx`, add the runner to `package.json`, and document the command here.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commit-style subjects such as `chore: initialize React TypeScript project`. Use a lowercase type (`feat`, `fix`, `chore`, `docs`, or `refactor`), a colon, and a concise imperative summary. Keep commits focused.

Pull requests should explain the motivation and behavior change, identify the affected area, link related issues, and list verification performed. Include before/after screenshots or recordings for visible UI changes. Do not commit generated `dist/` content, dependencies, secrets, or local environment files.

## Technology Stack

The frontend uses React 19, TypeScript, Vite, React Router, Zustand,
TanStack Query, and SCSS through `sass-embedded`.

The backend is planned around Java 21 and Spring Boot 4.1.0.
Infrastructure work belongs under `infra/` and may include Docker,
Nginx, OpenVidu, Redis, deployment, and CI/CD configuration.

Reuse the existing project dependencies and conventions before adding
new libraries or architectural patterns.

## Branch and Repository Safety

- Never work directly on `main` or `develop`.
- Make changes only on the currently checked-out feature or chore branch.
- Check `git status` and the current branch before modifying files.
- Do not run `git push`, merge branches, delete branches, or rewrite Git
  history unless the user explicitly requests it.
- Keep changes limited to the requested task and relevant project area.
- Do not modify unrelated frontend, backend, infra, or documentation files.
- Explain changes that cross frontend, backend, data, or infrastructure
  boundaries before implementing them.
- Never commit `.env` files, credentials, private keys, access tokens,
  generated `dist/` files, `node_modules/`, or personal Codex settings.

## Frontend Architecture

Before creating new structures, inspect the existing organization under
`frontend-user/src/` (or `frontend-admin/src/` when working on the admin
console).

Use the agreed project structure: features are colocated (a page's API
client, types, and utils live next to it, e.g.
`pages/app/situations/situationApi.ts`), not split into global
`api/`/`hooks/`/`stores/`/`types/`/`utils/` trees. Do not create competing
folder structures for the same responsibility, and do not add a shared
package between `frontend-user` and `frontend-admin` without discussing
it first.

When modifying frontend code, run the following from the project you
changed (`frontend-user/` or `frontend-admin/`):

- `npm run lint`
- `npm run build`

For visible UI changes, also perform a browser smoke test with
`npm run dev`.