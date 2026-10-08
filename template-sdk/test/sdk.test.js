import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { connect, connectTyped } from '../src/index.js';
import { MockHost } from '../src/mock-host.js';

test('browser mock uses the same request envelope as Android', async () => {
  const client = connect(new MockHost({ handlers: { 'todo.add': value => ({ key: 'one', data: value, pendingConfirmation: false }) } }));
  assert.equal((await client.project.info()).name, '预览项目');
  assert.ok((await client.permissions.status()).granted.includes('data.records'));
  assert.deepEqual((await client.data.create('todo.add', { text: '训练' })).data, { text: '训练' });
  await assert.rejects(client.invoke('undeclared', {}), /Mock action not registered/);
});

test('typed connection has the same runtime behavior', async () => {
  const client = connectTyped(new MockHost({ handlers: {
    'todo.add': value => ({ key: 'one', data: value, pendingConfirmation: false }),
  } }));
  assert.equal((await client.invoke('todo.add', { text: 'review' })).data.text, 'review');
});

test('mock permission denial and theme preview do not log input data', async () => {
  const mock = new MockHost({ handlers: { 'todo.add': value => ({ key: 'one', data: value }) } });
  const client = connect(mock);
  mock.setDenied('action.run', true);
  await assert.rejects(client.data.create('todo.add', { secret: 'never-log-this' }), /permission denied/);
  assert.equal(JSON.stringify(mock.logs).includes('never-log-this'), false);
  mock.setDenied('action.run', false);
  mock.setTheme('dark');
  assert.equal((await client.ui.theme()).bg, '#17171b');
});

test('mock does not grant missing services or print notice contents', async () => {
  const mock = new MockHost();
  const client = connect(mock);
  assert.equal((await client.permissions.status()).available.includes('speech.recognize'), false);
  const original = console.info;
  let printed = false;
  console.info = () => { printed = true; };
  try { await client.ui.notice('private notice'); } finally { console.info = original; }
  assert.equal(printed, false);
  assert.equal(JSON.stringify(mock.logs).includes('private notice'), false);
  if (globalThis.confirm === undefined) assert.equal(await client.ui.confirm('Delete?', 'Private text'), false);
});

test('speech bridge returns only state or transcript and never logs content', async () => {
  const mock = new MockHost({ handlers: {
    'speech.speak': ({ text }) => { assert.equal(text, '私人文字'); return { state: 'finished' }; },
    'speech.stop': () => 'ok',
    'speech.recognize': ({ language }) => { assert.equal(language, 'en'); return { text: '转写结果' }; },
    'speech.recognize.stop': () => 'ok',
  } });
  const client = connect(mock);
  assert.deepEqual(await client.speech.speak('私人文字'), { state: 'finished' });
  assert.deepEqual(await client.speech.recognize({ language: 'en' }), { text: '转写结果' });
  await client.speech.stop();
  await client.speech.stopRecognition();
  assert.equal(JSON.stringify(mock.logs).includes('私人文字'), false);
  mock.setDenied('speech.recognize', true);
  await assert.rejects(client.speech.recognize({ language: 'en' }), /permission denied/);
});
