/** Browser-only simulator; never used as a source of authorization in Android. */
export class MockHost {
  constructor({ project = { id: 'mock-project', name: '预览项目' }, handlers = {}, denied = [] } = {}) {
    this.project = project;
    this.handlers = handlers;
    this.onmessage = null;
    this.records = new Map();
    this.denied = new Set(denied);
    this.logs = [];
    this.theme = 'light';
    this.onchange = null;
  }
  setDenied(capability, denied) { denied ? this.denied.add(capability) : this.denied.delete(capability); this.onchange?.(); }
  setTheme(value) { this.theme = value === 'dark' ? 'dark' : 'light'; this.onchange?.(); }
  postMessage(raw) {
    const request = JSON.parse(raw);
    queueMicrotask(async () => {
      let result;
      try {
        if (this.denied.has(request.capability)) throw Error(`Mock permission denied: ${request.capability}`);
        if (request.capability === 'project.info') result = this.project;
        else if (request.capability === 'host.permissions') {
          const basic = ['host.permissions', 'data.records', 'project.info', 'ui.theme', 'ui.notice', 'ui.confirm'];
          const optional = ['file.pick', 'media.image.pick', 'clipboard.read', 'network.fetch', 'speech.speak', 'speech.recognize']
            .filter(item => typeof this.handlers[item] === 'function');
          const granted = [...basic, ...optional].filter(item => !this.denied.has(item));
          result = { granted, available: granted, networkDomains: [] };
        }
        else if (request.capability === 'ui.theme') result = this.theme === 'dark'
          ? { bg: '#17171b', fg: '#f4f4f6', card: '#25252b', muted: '#a4a4ad', action: '#a7b8ff', onaction: '#151824' }
          : { bg: '#ffffff', fg: '#222222', card: '#ffffff', muted: '#777777', action: '#4466dd', onaction: '#ffffff' };
        else if (request.capability === 'ui.notice') result = 'ok';
        else if (request.capability === 'ui.confirm') result = globalThis.confirm?.(`${request.input.title}\n${request.input.message}`) ?? false;
        else if (request.capability === 'clipboard.read') result = await this.handlers['clipboard.read']?.() ?? '';
        else if (request.capability === 'file.pick' || request.capability === 'media.image.pick') {
          const handler = this.handlers[request.capability];
          if (!handler) throw Error(`Mock ${request.capability} handler not registered`);
          result = await handler(request.input, this);
        }
        else if (request.capability === 'network.fetch') {
          const handler = this.handlers['network.fetch'];
          if (!handler) throw Error('Mock network.fetch handler not registered');
          result = await handler(request.input, this);
        }
        else if (request.capability.startsWith('speech.')) {
          const handler = this.handlers[request.capability];
          if (!handler) throw Error(`Mock ${request.capability} handler not registered`);
          result = await handler(request.input, this);
        }
        else if (request.capability === 'action.run') {
          const handler = this.handlers[request.input.actionId];
          if (!handler) throw Error(`Mock action not registered: ${request.input.actionId}`);
          result = await handler(request.input.data, this);
        } else throw Error(`Mock capability not registered: ${request.capability}`);
        this.logs.unshift({ at: new Date().toISOString(), capability: request.capability, actionId: request.input?.actionId, ok: true });
        this.logs.length = Math.min(this.logs.length, 100);
        this.onchange?.();
        this.onmessage?.({ data: JSON.stringify({ requestId: request.requestId, result }) });
      } catch (error) {
        this.logs.unshift({ at: new Date().toISOString(), capability: request.capability, actionId: request.input?.actionId, ok: false });
        this.logs.length = Math.min(this.logs.length, 100);
        this.onchange?.();
        this.onmessage?.({ data: JSON.stringify({ requestId: request.requestId, error: String(error.message ?? error), errorCode: 'MOCK_ERROR' }) });
      }
    });
  }
}

/** Browser-only overlay. Never serializes inputs or API keys into logs. */
export function attachMockDevtools(mock, { capabilities = ['host.permissions', 'project.info', 'ui.theme', 'ui.notice', 'ui.confirm', 'action.run', 'file.pick', 'media.image.pick', 'clipboard.read', 'network.fetch', 'speech.speak', 'speech.recognize'] } = {}) {
  const button = document.createElement('button');
  button.type = 'button'; button.textContent = '模板调试';
  button.style.cssText = 'position:fixed;right:12px;bottom:12px;z-index:9999;padding:9px 14px;border-radius:12px;background:#242733;color:white';
  const panel = document.createElement('section');
  panel.style.cssText = 'display:none;position:fixed;right:12px;bottom:55px;z-index:9999;width:min(420px,calc(100vw - 24px));max-height:65vh;overflow:auto;background:#fff;color:#222;border:1px solid #aaa;border-radius:12px;padding:14px;box-shadow:0 8px 30px #0003;font:14px system-ui';
  button.onclick = () => { panel.style.display = panel.style.display === 'none' ? 'block' : 'none'; };
  const render = () => {
    panel.replaceChildren();
    const title = document.createElement('h3'); title.textContent = 'Mock Host · 脱敏调试'; panel.append(title);
    const theme = document.createElement('button'); theme.textContent = `主题：${mock.theme === 'light' ? '浅色' : '深色'}`;
    theme.onclick = () => { mock.setTheme(mock.theme === 'light' ? 'dark' : 'light'); const colors = mock.theme === 'dark'
      ? { bg: '#17171b', fg: '#f4f4f6', card: '#25252b', muted: '#a4a4ad', action: '#a7b8ff', onaction: '#151824' }
      : { bg: '#ffffff', fg: '#222222', card: '#ffffff', muted: '#777777', action: '#4466dd', onaction: '#ffffff' };
      for (const [key, value] of Object.entries(colors)) document.documentElement.style.setProperty(`--${key}`, value);
    }; panel.append(theme);
    const heading = document.createElement('p'); heading.textContent = '权限模拟：'; panel.append(heading);
    for (const capability of capabilities) {
      const label = document.createElement('label'); label.style.display = 'block';
      const check = document.createElement('input'); check.type = 'checkbox'; check.checked = !mock.denied.has(capability);
      check.onchange = () => mock.setDenied(capability, !check.checked);
      label.append(check, document.createTextNode(` ${capability}`)); panel.append(label);
    }
    const actionHeading = document.createElement('p'); actionHeading.textContent = 'Action 调试：'; panel.append(actionHeading);
    const actionId = document.createElement('input'); actionId.placeholder = 'actionId'; actionId.style.width = '95%'; panel.append(actionId);
    const actionInput = document.createElement('textarea'); actionInput.value = '{}'; actionInput.style.cssText = 'width:95%;height:64px'; panel.append(actionInput);
    const run = document.createElement('button'); run.textContent = '调用 Action';
    run.onclick = () => { try {
      const data = JSON.parse(actionInput.value);
      mock.postMessage(JSON.stringify({ protocolVersion: 2, requestId: crypto.randomUUID(), capability: 'action.run', input: { actionId: actionId.value, data } }));
    } catch { actionInput.setCustomValidity('请输入合法 JSON'); actionInput.reportValidity(); } }; panel.append(run);
    const logs = document.createElement('pre'); logs.style.cssText = 'white-space:pre-wrap;word-break:break-all';
    logs.textContent = mock.logs.map(row => `${row.at} ${row.ok ? '✓' : '✗'} ${row.capability}${row.actionId ? '/' + row.actionId : ''}${row.error ? ': ' + row.error : ''}`).join('\n') || '尚无调用';
    panel.append(logs);
  };
  mock.onchange = render; render(); document.body.append(panel, button);
  return () => { mock.onchange = null; panel.remove(); button.remove(); };
}
