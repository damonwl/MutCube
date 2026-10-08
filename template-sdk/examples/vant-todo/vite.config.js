import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  base: './',
  build: {
    outDir: 'dist/templates/todo',
    emptyOutDir: true,
    cssCodeSplit: false,
    target: 'es2020',
  },
});
