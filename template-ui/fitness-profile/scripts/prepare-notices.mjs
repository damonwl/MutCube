import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const cssPath = resolve(root, 'dist/profile-vant.css');
const output = resolve(root, 'dist/THIRD_PARTY_NOTICES.txt');
const withoutFallback = (await readFile(cssPath, 'utf8')).replace(/,url\(\/\/at\.alicdn\.com[^)]*\)\s*format\("woff"\)/g, '');
const embeddedFont = withoutFallback.match(/url\(data:font\/woff2;charset=utf-8;base64,([^)]*)\)\s*format\("woff2"\)/);
if (!embeddedFont) throw Error('Vant icon font missing from bundle');
await writeFile(resolve(root, 'dist/profile-vant.woff2'), Buffer.from(embeddedFont[1], 'base64'));
const css = withoutFallback.replace(embeddedFont[0], 'url("./profile-vant.woff2") format("woff2")');
if (/url\(\/\//.test(css)) throw Error('Vant CSS includes a remote asset');
await writeFile(cssPath, css);
const licenses = await Promise.all(['vant', '@vant/use', '@vant/popperjs', 'vue'].map(async name =>
  `${name}\n${await readFile(resolve(root, 'node_modules', name, 'LICENSE'), 'utf8')}`));
await writeFile(output, licenses.join('\n\n'));
