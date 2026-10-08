import { connect } from '../../assets/mutcube-sdk.js';

const $ = id => document.getElementById(id);
let bridge = globalThis.MutCube;
if (!bridge) {
  const { MockHost, attachMockDevtools } = await import('../../assets/mock-host.js');
  const rows = [];
  bridge = new MockHost({ handlers: {
    'todo.list': () => ({ key: '', data: { records: rows, next: null }, pendingConfirmation: false }),
    'todo.create': data => { const row = { key: crypto.randomUUID(), revision: 1, data }; rows.unshift(row); return { key: row.key, data: row, pendingConfirmation: false }; },
    'todo.update': ({ key, data }) => { const row = rows.find(item => item.key === key); row.data = data; row.revision++; return { key, data: row, pendingConfirmation: false }; },
    'todo.delete': ({ key }) => { rows.splice(rows.findIndex(item => item.key === key), 1); return { key, data: {}, pendingConfirmation: false }; },
  } });
  attachMockDevtools(bridge);
}
const host = connect(bridge);
const status = text => { $('status').textContent = text; };

async function refresh() {
  const { data } = await host.data.query('todo.list');
  $('list').replaceChildren();
  for (const row of data.records) {
    const item = document.createElement('div'); item.className = 'item';
    const check = document.createElement('input'); check.type = 'checkbox'; check.checked = row.data.done;
    check.onchange = async () => { await host.data.update('todo.update', row.key, row.revision, { ...row.data, done: check.checked }); await refresh(); };
    const title = document.createElement('span'); title.textContent = row.data.text;
    if (row.data.done) title.style.textDecoration = 'line-through';
    const remove = document.createElement('button'); remove.textContent = '删除';
    remove.onclick = async () => { await host.data.delete('todo.delete', row.key, row.revision); await refresh(); };
    item.append(check, title, remove); $('list').append(item);
  }
}
$('form').onsubmit = async event => {
  event.preventDefault();
  const text = $('text').value.trim(); if (!text) return;
  try { await host.data.create('todo.create', { text, done: false }); $('text').value = ''; await refresh(); status('已保存'); }
  catch (error) { status(error.message); }
};
try { $('project').textContent = (await host.project.info()).name; await refresh(); } catch (error) { status(error.message); }
