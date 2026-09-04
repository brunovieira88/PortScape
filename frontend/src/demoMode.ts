/**
 * Set only by `npm run build:demo` (see vite.config.ts and .env.demo) -- the GitHub
 * Pages build, which has no backend behind it. Every network call is gated on it so
 * the static demo shows the sample city instead of a wall of failed fetches to an
 * /api that doesn't exist there.
 */
export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true';
