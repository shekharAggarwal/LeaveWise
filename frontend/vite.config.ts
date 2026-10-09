/// <reference types="vitest/config" />
import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import {astryxStylex} from '@astryxdesign/build/vite';

const isVitest = process.env.VITEST === 'true';

// Setup follows the official Astryx example (facebook/astryx, apps/example-vite).
// Under Vitest, StyleX's Babel plugin compiles our own stylex.create() calls instead: the
// unplugin-based Astryx wrapper leaves file handles open, so the test run takes 10 s to exit.
// Astryx components come prebuilt from the package, so tests still render the real components.
export default defineConfig({
  plugins: [
    ...(isVitest ? [] : astryxStylex()),
    react(
      isVitest
        ? {babel: {plugins: [['@stylexjs/babel-plugin', {dev: true, runtimeInjection: true}]]}}
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
  },
});
