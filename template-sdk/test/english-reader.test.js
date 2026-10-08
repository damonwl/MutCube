import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { readings } from '../examples/english-reader/templates/english/content.js';
import { wordnet } from '../examples/english-reader/templates/english/wordnet.js';

const root = new URL('../examples/english-reader/', import.meta.url);
const readJson = async name => JSON.parse(await readFile(new URL(name, root), 'utf8'));

test('English example contains original labelled readings and answer keys', () => {
  assert.ok(readings.length >= 3);
  assert.equal(new Set(readings.map(reading => reading.id)).size, readings.length);
  for (const reading of readings) {
    assert.match(reading.source, /原创.*非考试真题/);
    assert.ok(reading.paragraphs.length >= 2);
    assert.ok(reading.question && reading.answer);
  }
});

test('English example declares scoped sources and no chat mutations', async () => {
  const manifest = await readJson('manifest.json');
  assert.deepEqual(manifest.networkDomains.sort(), ['en.wiktionary.org', 'zh.wiktionary.org']);
  assert.ok(manifest.permissions.includes('speech.speak'));
  assert.ok(manifest.permissions.includes('ai.generate'));
  for (const action of manifest.template.actions) {
    if (action.channels.includes('CHAT')) assert.ok(['LIST', 'CURRENT', 'GENERATE'].includes(action.mode));
  }
  assert.ok(Object.keys(wordnet).length >= 100);
  assert.ok(wordnet.cooperation.some(gloss => /joint|operation|act/.test(gloss)));
  assert.equal(wordnet.the, undefined);
});
