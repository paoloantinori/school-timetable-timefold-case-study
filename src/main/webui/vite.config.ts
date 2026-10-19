import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');

/**
 * Pacchetti usati SOLO dalla rotta lazy /storia: vanno lasciati fuori dal bucket
 * `vendor`, che e' in modulepreload su ogni pagina. Senza questa esclusione la regola
 * "tutto node_modules -> vendor" farebbe pagare il parser markdown (~47 kB gzip) anche
 * a chi non apre mai quella pagina; escludendoli, Rollup li assegna da solo al chunk
 * del loro unico importatore.
 */
const LAZY_ONLY_PACKAGES = [
  'react-markdown', 'remark-', 'rehype-', 'micromark', 'mdast-util', 'hast-util',
  'unist-util', 'unified', 'vfile', 'bail', 'trough', 'devlop', 'zwitch',
  'html-url-attributes', 'property-information', 'space-separated-tokens',
  'comma-separated-tokens', 'character-entities', 'decode-named-character-reference',
  'ccount', 'escape-string-regexp', 'markdown-table', 'longest-streak',
  'estree-util-is-identifier-name', 'style-to-js', 'style-to-object', 'inline-style-parser',
];

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // STORIA.md e RIAPPROCCIO.md vivono nella root del repo (unica copia, per la regola
    // "puntatori, non copie" di CLAUDE.md) e sono importati con ?raw: il dev server deve
    // poter leggere fuori da src/main/webui. Dichiarato qui invece di affidarsi al
    // default di Vite, che risale al primo .git trovato e non e' un contratto esplicito.
    fs: { allow: [repoRoot] },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks(id) {
          // Vendor chunk - React and core libraries
          if (id.includes('node_modules')) {
            // UI libraries
            if (id.includes('vis-timeline') || id.includes('react-window') || id.includes('@tanstack')) {
              return 'ui';
            }
            // Dipendenze della sola rotta lazy: le lascia assegnare a Rollup.
            // Il match e' ancorato al segmento del nome pacchetto: un `includes` nudo
            // prenderebbe anche un file omonimo dentro un pacchetto eager (nomi come
            // 'bail' o 'unified' sono corti e comuni), togliendolo da vendor in silenzio.
            if (LAZY_ONLY_PACKAGES.some(pkg => id.includes(`/node_modules/${pkg}`))) {
              return undefined;
            }
            // Everything else from node_modules (React, router, etc.)
            return 'vendor';
          }
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
});
