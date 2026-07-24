# Repository Guidelines

## Working Principles

These behavioral guidelines reduce common AI-agent coding mistakes. They bias
toward caution over speed; for trivial changes (typo fixes, obvious one-liners),
use judgment.

**Think before coding — don't assume, don't hide confusion, surface tradeoffs.**

- State assumptions explicitly. If uncertain, ask rather than guess.
- If multiple interpretations exist, present them — don't pick one silently.
- If a simpler approach exists, say so, and push back when warranted.
- If something is unclear, stop, name what is confusing, and ask.

**Simplicity first — write the minimum code that solves the problem.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or configurability that wasn't requested.
- No error handling for impossible scenarios.
- If 200 lines could be 50, rewrite it. If a senior engineer would call it
  overcomplicated, simplify.
- Reuse current dependencies and conventions before adding new libraries or
  patterns (see Technology Stack).

**Surgical changes — touch only what you must.**

Beyond keeping changes within the requested scope (see Branch and Repository
Safety), when editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match the existing style even if you'd do it differently (see Coding Style &
  Naming Conventions).
- If you notice unrelated dead code, mention it — don't delete it.
- Remove only the imports, variables, or functions that your own change made
  unused; leave pre-existing dead code unless asked to remove it.

Every changed line should trace directly to the requested task.

**Goal-driven execution — define success criteria, then loop until met.**

Vague goals ("make it work") force constant back-and-forth; concrete, verifiable
criteria let the work run to completion. For multi-step work, state a brief plan
with a verification step for each step. "Verify" means running the project's
existing checks:

- Frontend: `npm run lint` and `npm run build` in the changed project (no test
  framework yet), plus a browser smoke test via `npm run dev` for visible UI.
- Backend: `./gradlew build` in `backend/`, which compiles and runs the JUnit
  test suite. Since real tests exist here, prefer reproducing a bug with a
  failing test first, then making it pass.

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