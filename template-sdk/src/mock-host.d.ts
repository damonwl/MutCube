import type { HostBridge, TemplateProject } from './index.js';

export declare class MockHost implements HostBridge {
  constructor(options?: {
    project?: TemplateProject;
    handlers?: Record<string, (input: any, host: MockHost) => unknown | Promise<unknown>>;
    denied?: string[];
  });
  project: TemplateProject;
  handlers: Record<string, (input: any, host: MockHost) => unknown | Promise<unknown>>;
  onmessage: ((event: { data: string }) => void) | null;
  readonly denied: Set<string>;
  readonly logs: Array<{ at: string; capability: string; actionId?: string; ok: boolean }>;
  readonly theme: 'light' | 'dark';
  setDenied(capability: string, denied: boolean): void;
  setTheme(value: 'light' | 'dark'): void;
  postMessage(raw: string): void;
}

export declare function attachMockDevtools(mock: MockHost, options?: { capabilities?: string[] }): () => void;
