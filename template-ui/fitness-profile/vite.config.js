import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    cssCodeSplit: false,
    target: 'es2020',
    lib: {
      entry: 'src/main.js',
      name: 'FitnessProfileVant',
      formats: ['iife'],
      fileName: () => 'profile-vant.js',
      cssFileName: 'profile-vant',
    },
  },
});
