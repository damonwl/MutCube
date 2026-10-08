import { connect } from '../../assets/mutcube-sdk.js';

const $ = id => document.getElementById(id);
let bridge = globalThis.MutCube;
if (!bridge) {
  const { MockHost, attachMockDevtools } = await import('../../assets/mock-host.js');
  const rows = [];
  bridge = new MockHost({ handlers: {
    'speech.speak': async () => ({ state: 'finished' }),
    'speech.stop': () => 'ok',
    'speech.recognize': () => ({ text: globalThis.prompt?.('Mock Host：输入模拟转写文字') ?? '' }),
    'speech.recognize.stop': () => 'ok',
    'note.list': () => ({ key: '', data: { records: rows, next: null }, pendingConfirmation: false }),
    'note.save': data => { const row = { key: crypto.randomUUID(), data }; rows.unshift(row); return { key: row.key, data: row, pendingConfirmation: false }; },
  } });
  attachMockDevtools(bridge);
}
const host = connect(bridge);
const status = value => { $('status').textContent = value; };
async function perform(task) { try { await task(); } catch (error) { status(`${error.code ?? 'ERROR'}：${error.message}`); } }
host.on('speech', ({ state }) => status(`语音状态：${state}`));
$('speak').onclick = () => perform(async () => status(`朗读${(await host.speech.speak($('text').value)).state}`));
$('stop').onclick = () => perform(async () => { await host.speech.stop(); status('已停止'); });
$('recognize').onclick = () => perform(async () => {
  const language = $('language').value;
  const result = await host.speech.recognize(language ? { language } : {});
  if ('text' in result) { $('text').value = result.text; status('转写完成'); }
  else status('录音已取消');
});
$('finish').onclick = () => perform(async () => { await host.speech.stopRecognition(); status('正在转写…'); });
$('save').onclick = () => perform(async () => {
  const text = $('text').value.trim();
  if (!text) return status('请先输入文字');
  await host.data.create('note.save', { text });
  await refresh(); status('已保存');
});
async function refresh() {
  const { data } = await host.data.query('note.list');
  $('history').replaceChildren(...data.records.map(row => {
    const item = document.createElement('li'); item.textContent = row.data.text; return item;
  }));
}
perform(refresh);
document.documentElement.dataset.speechReady = 'true';
