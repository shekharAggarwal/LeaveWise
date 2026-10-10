/// <reference types="vitest/config" />
import {defineConfig, type Plugin} from 'vite';
import react from '@vitejs/plugin-react';
import {astryxStylex} from '@astryxdesign/build/vite';
import {writeFileSync} from 'node:fs';
import {join, resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

const isVitest = process.env.VITEST === 'true';

/**
 * Keeps the production page within the strict CSP (style-src 'self', docs/API.md): no inline
 * <style> at all. The Astryx build injects its cascade-layer order as an inline <style>, and
 * Astryx's popover layer injects static @position-try rules at runtime unless an element with id
 * "astryx-layer-slide-rules" already exists. Both become same-origin stylesheets instead.
 */
function cspSafeAstryxStyles(): Plugin {
  const LAYER_STYLE = /<style>(@layer [^<]+;)<\/style>/;
  let layerOrder = '';
  return {
    name: 'leavewise:csp-safe-astryx-styles',
    apply: 'build',
    enforce: 'post',
    transformIndexHtml: {
      order: 'post',
      handler(html) {
        const match = LAYER_STYLE.exec(html);
        if (!match?.[1]) {
          throw new Error(
            'Expected the Astryx cascade-layer <style> in index.html; check the Astryx build plugin.',
          );
        }
        layerOrder = match[1];
        return {
          html: html.replace(LAYER_STYLE, ''),
          tags: [
            {
              tag: 'link',
              attrs: {rel: 'stylesheet', href: '/layers.css'},
              injectTo: 'head-prepend',
            },
            {
              tag: 'link',
              attrs: {
                id: 'astryx-layer-slide-rules',
                rel: 'stylesheet',
                href: '/astryx-layer-slide-rules.css',
              },
              injectTo: 'head',
            },
          ],
        };
      },
    },
    async generateBundle() {
      // Astryx's own generator, so the file always matches the installed version. Imported by
      // path: the package's exports map doesn't expose it.
      const generator = resolve(
        import.meta.dirname,
        'node_modules/@astryxdesign/core/dist/Layer/layerSlideRules.js',
      );
      const rules = (await import(pathToFileURL(generator).href)) as {
        slideRulesCss: () => string;
      };
      this.emitFile({
        type: 'asset',
        fileName: 'astryx-layer-slide-rules.css',
        source: rules.slideRulesCss(),
      });
    },
    closeBundle() {
      if (!layerOrder) throw new Error('No cascade-layer order captured from index.html.');
    },
    writeBundle(options) {
      if (layerOrder && options.dir) {
        writeFileSync(join(options.dir, 'layers.css'), `${layerOrder}\n`);
      }
    },
  };
}

// Setup follows the official Astryx example (facebook/astryx, apps/example-vite).
// Under Vitest, StyleX's Babel plugin compiles our own stylex.create() calls instead: the
// unplugin-based Astryx wrapper leaves file handles open, so the test run takes 10 s to exit.
// Astryx components come prebuilt from the package, so tests still render the real components.
export default defineConfig({
  plugins: [
    ...(isVitest ? [] : astryxStylex()),
    cspSafeAstryxStyles(),
    react(
      isVitest
        ? {
            babel: {
              plugins: [
                [
                  '@stylexjs/babel-plugin',
                  {
                    dev: true,
                    runtimeInjection: true,
                    // Lets tests import theme tokens (defineVars) from @astryxdesign/core.
                    unstable_moduleResolution: {type: 'commonJS', rootDir: import.meta.dirname},
                  },
                ],
              ],
            },
          }
        : {},
    ),
  ],
  server: {
    // The Spring Boot backend runs on 8080 during development.
    proxy: {'/api': 'http://localhost:8080'},
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    // Far from UTC, so a date helper that used local time would show the wrong day (code review 9).
    env: {TZ: 'America/Los_Angeles'},
  },
});
