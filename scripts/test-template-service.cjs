// Execute the actual page script against an isolated bridge/DOM. Never touches user data or Providers.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { randomUUID } = require('node:crypto');
class Element {
  constructor(tag = 'div') { this.tag = tag; this.children = []; this.value = ''; this.textContent = ''; }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  focus() {}
  showModal() { this.open = true; }
  close() { this.open = false; }
}
const elements = new Map();
const document = {
  getElementById(id) { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); },
  createElement(tag) { return new Element(tag); },
  querySelectorAll() { return []; },
};
const $ = id => document.getElementById(id);
const notes = [], history = [], calls = [];
let current = null, allowDelete = false, failGenerate = false;
const bridge = {
  postMessage(raw) {
    const body = JSON.parse(raw); calls.push(body);
    assert.equal(body.protocolVersion, 2);
    assert.equal(body.capability, 'action.run');
    assert.deepEqual(Object.keys(body.input).sort(), ['actionId', 'data']);
    const { actionId, data } = body.input;
    let result, error;
    switch (actionId) {
      case 'notes.save': {
        const row = { key: body.requestId, revision: 1, data, updatedAt: Date.now() }; notes.unshift(row); result = row; break;
      }
      case 'notes.list': result = { records: [...notes], next: null }; break;
      case 'notes.update': {
        const row = notes.find(row => row.key === data.key); assert.equal(row.revision, data.expectedRevision);
        row.data = data.data; row.revision++; result = { ...row }; break;
      }
      case 'notes.delete': {
        if (allowDelete) { const index = notes.findIndex(row => row.key === data.key); assert.equal(notes[index].revision, data.expectedRevision); notes.splice(index, 1); }
        result = { confirmed: allowDelete }; break;
      }
      case 'document.current': result = current ?? { revision: 0, key: null, data: null }; break;
      case 'document.list': result = { records: [...history], next: null }; break;
      case 'document.generate': {
        assert.equal(data.baseRevision, current?.revision ?? 0);
        if (failGenerate) { error = 'fixture generation failed'; break; }
        result = { text: '整理后的隔离笔记' }; history.unshift({ key: body.requestId, data: result, revision: 1 }); break;
      }
      case 'document.confirm': {
        const row = history.find(row => row.key === data.key); current = { key: row.key, revision: 1, data: row.data };
        result = { confirmed: true, key: row.key, revision: 1 }; break;
      }
      default: throw Error('Undeclared operation: ' + actionId);
    }
    queueMicrotask(() => bridge.onmessage({ data: JSON.stringify(error ? { requestId: body.requestId, error } : {
      requestId: body.requestId, result: { key: body.requestId, data: result, pendingConfirmation: actionId === 'document.generate' },
    }) }));
  },
};
const context = vm.createContext({ document, MutCube: bridge, crypto: { randomUUID }, setTimeout, clearTimeout, window: { addEventListener() {} } });
const html = fs.readFileSync('app/src/main/assets/templates/notes/index.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]; assert.equal(scripts.length, 1);
vm.runInContext(scripts[0][1], context);
(async () => {
  while (vm.runInContext('busy', context)) await new Promise(resolve => setTimeout(resolve, 0));
  $('text').value = '隔离笔记'; await $('save').onclick(); assert.equal(notes.length, 1);
  await $('notes').children[0].children[1].onclick(); $('text').value = '编辑后的笔记'; await $('save').onclick();
  assert.equal(notes[0].data.text, '编辑后的笔记'); assert.equal(notes[0].revision, 2);
  $('request').value = '整理'; await $('generate').onclick(); assert.equal(history.length, 1); assert.equal(current, null);
  context.window.MutCubeLaunchTarget={collection:'documents',recordKey:history[0].key};
  await vm.runInContext('openLaunchTarget()', context);
  assert.equal(current,null);assert.equal($('candidate').children[0].children[0].textContent,history[0].data.text);
  await $('candidate').children[0].children[1].onclick(); assert.equal(current.revision, 1);
  failGenerate = true; await $('generate').onclick(); assert.equal(history.length, 1); assert.equal(notes.length, 1);
  assert.equal($('status').textContent, 'fixture generation failed');
  await $('notes').children[0].children[2].onclick(); assert.equal(notes.length, 1);
  allowDelete = true; await $('notes').children[0].children[2].onclick(); assert.equal(notes.length, 0); assert.equal(history.length, 1);
  $('text').value = '尚未保存'; let leave = context.window.MutCubeBeforeLeave(); $('leaveNo').onclick(); assert.equal(await leave, false);
  leave = context.window.MutCubeBeforeLeave(); $('leaveYes').onclick(); assert.equal(await leave, true);
  console.log('模板页面隔离回归通过：协议 2、CRUD、候选/确认、失败保留、取消删除及未保存退出。');
})().catch(error => { console.error(error); process.exitCode = 1; });
