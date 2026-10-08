<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import { Button, Cell, Checkbox, ConfigProvider, Empty, Field, Loading, Tag } from 'vant';
import { connect } from '../../../src/index.js';
import { MockHost, attachMockDevtools } from '../../../src/mock-host.js';

const rows = ref([]);
const projectName = ref('正在读取项目…');
const entry = ref('');
const feedback = ref('');
const busy = ref(false);
const themeMode = ref('light');
let host;
let stopDevtools;
let themeObserver;

function applyColors(colors) {
  for (const [key, value] of Object.entries(colors)) {
    document.documentElement.style.setProperty(`--${key}`, value);
  }
}

function syncTheme() {
  const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim();
  const hex = bg.match(/^#([0-9a-f]{6})$/i)?.[1];
  if (!hex) return;
  const channels = [0, 2, 4].map(offset => parseInt(hex.slice(offset, offset + 2), 16));
  themeMode.value = channels.reduce((sum, channel) => sum + channel, 0) < 384 ? 'dark' : 'light';
}

async function refresh() {
  const result = await host.data.query('todo.list');
  rows.value = result.data.records;
}

async function perform(action, success) {
  if (busy.value) return;
  busy.value = true;
  feedback.value = '';
  try {
    await action();
    await refresh();
    feedback.value = success;
  } catch (error) {
    feedback.value = `操作失败：${error.message}`;
  } finally {
    busy.value = false;
  }
}

function add() {
  const text = entry.value.trim();
  if (!text) return;
  perform(async () => {
    await host.data.create('todo.create', { text, done: false });
    entry.value = '';
  }, '已保存');
}

function toggle(row) {
  perform(() => host.data.update('todo.update', row.key, row.revision, {
    ...row.data,
    done: !row.data.done,
  }), '已更新');
}

function remove(row) {
  perform(async () => {
    if (await host.ui.confirm('删除待办', `确定删除「${row.data.text}」吗？`)) {
      await host.data.delete('todo.delete', row.key, row.revision);
    }
  }, '操作完成');
}

onMounted(async () => {
  let bridge = globalThis.MutCube;
  if (!bridge) {
    const records = [];
    bridge = new MockHost({ handlers: {
      'todo.list': () => ({ key: '', data: { records: [...records], next: null }, pendingConfirmation: false }),
      'todo.create': data => {
        const row = { key: crypto.randomUUID(), revision: 1, data };
        records.unshift(row);
        return { key: row.key, data: row, pendingConfirmation: false };
      },
      'todo.update': ({ key, expectedRevision, data }) => {
        const row = records.find(item => item.key === key && item.revision === expectedRevision);
        if (!row) throw Error('记录已变化，请刷新后重试');
        row.data = data;
        row.revision++;
        return { key, data: row, pendingConfirmation: false };
      },
      'todo.delete': ({ key, expectedRevision }) => {
        const index = records.findIndex(item => item.key === key && item.revision === expectedRevision);
        if (index < 0) throw Error('记录已变化，请刷新后重试');
        records.splice(index, 1);
        return { key, data: {}, pendingConfirmation: false };
      },
    } });
    stopDevtools = attachMockDevtools(bridge);
  }
  host = connect(bridge);
  themeObserver = new MutationObserver(syncTheme);
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['style'] });
  try {
    applyColors(await host.ui.theme());
    syncTheme();
    projectName.value = (await host.project.info()).name;
    await refresh();
  } catch (error) {
    feedback.value = `初始化失败：${error.message}`;
  }
});

onUnmounted(() => {
  themeObserver?.disconnect();
  stopDevtools?.();
});
</script>

<template>
  <ConfigProvider :theme="themeMode" class="page">
    <header class="heading">
      <div class="symbol" aria-hidden="true"><span /></div>
      <div>
        <h1>待办清单</h1>
        <p>{{ projectName }}</p>
      </div>
    </header>

    <form class="composer" @submit.prevent="add">
      <Field v-model="entry" maxlength="200" placeholder="添加一件待办事项" aria-label="待办内容" />
      <Button type="primary" native-type="submit" :loading="busy" :disabled="!entry.trim()">添加</Button>
    </form>

    <p class="section-label">我的待办 <Tag plain>{{ rows.length }}</Tag></p>
    <Empty v-if="!rows.length && !busy" description="暂无待办，从上方添加第一件事" />
    <div v-else class="list" aria-live="polite">
      <Cell v-for="row in rows" :key="row.key" class="task-cell">
        <template #title>
          <Checkbox :model-value="row.data.done" :disabled="busy" @click.prevent="toggle(row)">
            <span :class="{ completed: row.data.done }">{{ row.data.text }}</span>
          </Checkbox>
        </template>
        <template #right-icon>
          <Button size="small" plain :disabled="busy" :aria-label="`删除${row.data.text}`" @click="remove(row)">删除</Button>
        </template>
      </Cell>
    </div>
    <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
    <Loading v-if="busy" class="progress" size="18px">正在处理…</Loading>
  </ConfigProvider>
</template>
