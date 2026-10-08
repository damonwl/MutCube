import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const cli = join(root, 'scripts/mutcube-template');

function run(...args) {
  return spawnSync('python3', [cli, ...args], { cwd: root, encoding: 'utf8' });
}

test('independent developer can init, validate, generate types and pack', () => {
  const directory = mkdtempSync(join(tmpdir(), 'mutcube-cleanroom-'));
  assert.ok(directory.startsWith(join(tmpdir(), 'mutcube-cleanroom-')));
  try {
    const source = join(directory, 'sample');
    const output = join(directory, 'sample.mutcube-template');
    const types = join(directory, 'actions.d.ts');
    assert.equal(run('init', source, '--id', 'thirdparty.english-reader', '--developer', 'Independent developer').status, 0);
    const manifestPath = join(source, 'manifest.json');
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
    assert.equal(manifest.template.id, 'thirdparty.english-reader');
    assert.equal(manifest.developer.name, 'Independent developer');
    assert.equal(manifest.template.entry, 'templates/english/index.html');
    assert.ok(existsSync(join(source, 'templates/english/wordnet.js')));
    assert.ok(existsSync(join(source, 'assets/WORDNET_LICENSE.txt')));
    assert.ok(manifest.permissions.includes('speech.speak'));
    writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
    assert.equal(run('validate', source).status, 0);
    assert.equal(run('types', source, types).status, 0);
    const declarations = readFileSync(types, 'utf8');
    for (const action of ['profile.create', 'vocab.create', 'explain.generate', 'review.generate']) {
      assert.ok(declarations.includes(`"${action}"`));
    }
    assert.doesNotMatch(declarations, /todo\./);
    assert.equal(run('pack', source, output).status, 0);
    assert.ok(existsSync(output));
    assert.notEqual(run('pack', source, output).status, 0, 'pack must not overwrite an existing file');
    manifest.minHostVersion = '9999999999.0.0';
    writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
    assert.notEqual(run('validate', source).status, 0);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test('default init uses the English learning sample', () => {
  const directory = mkdtempSync(join(tmpdir(), 'mutcube-cleanroom-'));
  assert.ok(directory.startsWith(join(tmpdir(), 'mutcube-cleanroom-')));
  try {
    const source = join(directory, 'english');
    assert.equal(run('init', source).status, 0);
    const manifest = JSON.parse(readFileSync(join(source, 'manifest.json'), 'utf8'));
    assert.equal(manifest.template.id, 'example.english-reader');
    assert.equal(run('validate', source).status, 0);
    assert.notEqual(run('init', source).status, 0, 'init must not overwrite an existing directory');
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
