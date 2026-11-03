# Frontend

## Required skill

Before writing, reviewing, or refactoring any file in this directory, load the
`vercel-react-best-practices` skill with the skill tool and follow it.

It is vendored at `.opencode/skills/vercel-react-best-practices/` (Vercel, MIT,
v1.0.0). This applies to every frontend change: components, hooks, state, data
fetching, styling, and build config.

This app is a Vite + React SPA, not Next.js. The skill's `server-*`, RSC, and
hydration rules do not apply here. The relevant categories are `async-`,
`bundle-`, `client-`, `rerender-`, `rendering-`, `js-`, and `advanced-`.

## Security

After any change touching fetch logic, URL building, error rendering, or config,
run the `owasp-security` skill over the files you changed before reporting done.
Report findings in its format, or state plainly that there are none.

## Commands

- `npm run dev` — dev server on :5173, proxying `/api` to :8080
- `npm run build` — `tsc -b`, then the production build
