const DEFAULT_TIMEOUT = 220000;

export class TemplateHostError extends Error {
  constructor(message, code = 'HOST_ERROR') { super(message); this.name = 'TemplateHostError'; this.code = code; }
}

/** Only the host is trusted to inject a project identity. */
export class MutCubeTemplateClient {
  constructor(bridge = globalThis.MutCube) {
    if (!bridge || typeof bridge.postMessage !== 'function') throw new TemplateHostError('MutCube host is unavailable', 'NO_HOST');
    this.bridge = bridge;
    this.pending = new Map();
    bridge.onmessage = event => {
      let response;
      try { response = JSON.parse(event.data); } catch { return; }
      const waiting = this.pending.get(response.requestId);
      if (!waiting) return;
      clearTimeout(waiting.timer);
      this.pending.delete(response.requestId);
      response.error ? waiting.reject(new TemplateHostError(response.error, response.errorCode ?? 'HOST_ERROR')) : waiting.resolve(response.result);
    };
    this.data = Object.freeze({
      query: (actionId, cursor = {}) => this.invoke(actionId, cursor),
      create: (actionId, value, requestId) => this.invoke(actionId, value, requestId),
      update: (actionId, key, expectedRevision, value) => this.invoke(actionId, { key, expectedRevision, data: value }),
      delete: (actionId, key, expectedRevision) => this.invoke(actionId, { key, expectedRevision }),
    });
    this.ai = Object.freeze({ generate: (actionId, input, requestId) => this.invoke(actionId, input, requestId) });
    this.project = Object.freeze({ info: () => this.call('project.info', {}) });
    this.permissions = Object.freeze({ status: () => this.call('host.permissions', {}) });
    this.ui = Object.freeze({
      theme: () => this.call('ui.theme', {}),
      notice: message => this.call('ui.notice', { message }),
      confirm: (title, message) => this.call('ui.confirm', { title, message }),
    });
    this.navigation = Object.freeze({ openChat: () => this.call('navigation.openChat', {}) });
    this.file = Object.freeze({ pick: () => this.call('file.pick', {}) });
    this.media = Object.freeze({ pickImage: () => this.call('media.image.pick', {}) });
    this.clipboard = Object.freeze({ read: () => this.call('clipboard.read', {}) });
    this.network = Object.freeze({ fetch: url => this.call('network.fetch', { url }) });
    this.speech = Object.freeze({
      speak: text => this.call('speech.speak', { text }),
      stop: () => this.call('speech.stop', {}),
      recognize: (options = {}) => this.call('speech.recognize', options),
      stopRecognition: () => this.call('speech.recognize.stop', {}),
    });
  }

  call(capability, input, requestId = crypto.randomUUID()) {
    if (this.pending.has(requestId)) return Promise.reject(new TemplateHostError('Duplicate request ID', 'DUPLICATE_REQUEST'));
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new TemplateHostError('Host response timed out; inspect the run record before retrying', 'TIMEOUT'));
      }, capability.startsWith('speech.') ? 600000 : DEFAULT_TIMEOUT);
      this.pending.set(requestId, { resolve, reject, timer });
      try { this.bridge.postMessage(JSON.stringify({ protocolVersion: 2, requestId, capability, input })); }
      catch (error) { clearTimeout(timer); this.pending.delete(requestId); reject(error); }
    });
  }

  invoke(actionId, data, requestId) { return this.call('action.run', { actionId, data }, requestId); }
  on(event, listener) {
    const handler = detail => listener(detail.detail);
    globalThis.addEventListener(`mutcube:${event}`, handler);
    return () => globalThis.removeEventListener(`mutcube:${event}`, handler);
  }
}

export function connect(bridge) { return new MutCubeTemplateClient(bridge); }
export function connectTyped(bridge) { return connect(bridge); }
