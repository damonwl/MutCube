import { copyFile, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const sdk = resolve(root, '../..');
const output = resolve(root, 'dist');
const manifest = JSON.parse(await readFile(resolve(root, '../todo/manifest.json'), 'utf8'));
manifest.template.id = 'example.vant-todo';
manifest.template.name = 'Todo · Vant 示例';
manifest.template.version = '1.0.0';
manifest.resources = {};
await writeFile(resolve(output, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
await mkdir(resolve(output, 'assets'), { recursive: true });
await copyFile(resolve(sdk, 'src/index.js'), resolve(output, 'assets/mutcube-sdk.js'));
await copyFile(resolve(sdk, 'src/mock-host.js'), resolve(output, 'assets/mock-host.js'));
for (const file of await readdir(resolve(output, 'templates/todo/assets'))) {
  if (!file.endsWith('.css')) continue;
  const path = resolve(output, 'templates/todo/assets', file);
  const withoutFallback = (await readFile(path, 'utf8')).replace(/,url\(\/\/at\.alicdn\.com[^)]*\)\s*format\("woff"\)/g, '');
  const embeddedFont = withoutFallback.match(/url\(data:font\/woff2;charset=utf-8;base64,([^)]*)\)\s*format\("woff2"\)/);
  if (!embeddedFont) throw Error('Vant icon font missing from bundle');
  await writeFile(resolve(output, 'templates/todo/assets/vant-icon.woff2'), Buffer.from(embeddedFont[1], 'base64'));
  const css = withoutFallback.replace(embeddedFont[0], 'url("./vant-icon.woff2") format("woff2")');
  if (/url\(\/\//.test(css)) throw Error(`CSS includes a remote asset: ${file}`);
  await writeFile(path, css);
}
const licenses = await Promise.all(['vant', '@vant/use', '@vant/popperjs', 'vue'].map(async name =>
  `${name}\n${await readFile(resolve(root, 'node_modules', name, 'LICENSE'), 'utf8')}`));
await writeFile(resolve(output, 'assets/THIRD_PARTY_NOTICES.txt'), licenses.join('\n\n'));
