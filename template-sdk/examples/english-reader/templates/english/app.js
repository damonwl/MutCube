import { connect } from '../../assets/mutcube-sdk.js';
import { readings } from './content.js';
import { wordnet } from './wordnet.js';

const $ = id => document.getElementById(id);
let bridge = globalThis.MutCube;
if (!bridge) {
  const { MockHost, attachMockDevtools } = await import('../../assets/mock-host.js');
  const rows = { profiles: [], vocabulary: [], explanations: [], reviews: [] };
  const page = name => ({ key: '', data: { records: [...rows[name]].reverse(), next: null }, pendingConfirmation: false });
  const create = name => data => { const row = { key: crypto.randomUUID(), revision: 1, data }; rows[name].push(row); return { key: row.key, data, pendingConfirmation: false }; };
  const update = name => ({ key, expectedRevision, data }) => {
    const row = rows[name].find(item => item.key === key);
    if (!row || row.revision !== expectedRevision) throw Error('数据已变化，请刷新后重试');
    row.data = data; row.revision++;
    return { key, data, pendingConfirmation: false };
  };
  bridge = new MockHost({ handlers: {
    'profile.list': () => page('profiles'), 'profile.create': create('profiles'), 'profile.update': update('profiles'),
    'vocab.list': () => page('vocabulary'), 'vocab.create': create('vocabulary'), 'vocab.update': update('vocabulary'),
    'vocab.delete': ({ key, expectedRevision }) => { const i = rows.vocabulary.findIndex(item => item.key === key && item.revision === expectedRevision); if (i < 0) throw Error('数据已变化'); rows.vocabulary.splice(i, 1); return { key, data: {}, pendingConfirmation: false }; },
    'explain.generate': ({ term, context }) => ({ key: crypto.randomUUID(), data: { term, meaningZh: '浏览器预览：请在 MutCube 中配置模型后查看真实解释。', meaningEn: term, usage: context, contextMeaning: context, examTip: '请结合上下文判断词义。' }, pendingConfirmation: false }),
    'review.generate': ({ words }) => ({ key: crypto.randomUUID(), data: { question: `请用 ${words.split(',')[0]} 造一个句子。`, answer: '答案应符合语境和语法。', hint: '回想阅读时的原句。' }, pendingConfirmation: false }),
    'network.fetch': () => ({ status: 503, body: '', contentType: 'application/json' }),
    'speech.speak': () => ({ state: 'finished' }), 'speech.stop': () => 'stopped'
  } });
  attachMockDevtools(bridge);
}
const host = connect(bridge);
const state = { profile: null, profileRow: null, vocab: [], article: null, term: '', sentence: '', dictionary: {}, currentReview: null, busy: false };
const DAY = 86_400_000;
function toast(message) { $('toast').textContent = message; $('toast').hidden = false; clearTimeout(toast.timer); toast.timer = setTimeout(() => { $('toast').hidden = true; }, 3200); }
function show(page) {
  for (const section of document.querySelectorAll('.page')) section.hidden = section.id !== page;
  for (const tab of document.querySelectorAll('.tabs button')) tab.classList.toggle('active', tab.dataset.page === page);
  if (page === 'vocab') renderVocab();
}
document.querySelectorAll('.tabs button').forEach(button => button.onclick = () => show(button.dataset.page));
function applyTheme(theme) { for (const [key, value] of Object.entries(theme)) document.documentElement.style.setProperty(`--${key}`, value); }
async function loadRows(action) {
  const records = []; let cursor = {};
  for (let i = 0; i < 20; i++) {
    const response = await host.data.query(action, cursor);
    records.push(...(response.data.records ?? []));
    if (!response.data.next) break;
    cursor = response.data.next;
  }
  return records;
}
async function refresh() {
  const [profiles, vocab] = await Promise.all([loadRows('profile.list'), loadRows('vocab.list')]);
  state.profileRow = profiles[0] ?? null; state.profile = state.profileRow?.data ?? null; state.vocab = vocab;
  if (state.profile) { $('goal').value = state.profile.goal; $('level').value = state.profile.level; $('minutes').value = state.profile.dailyMinutes; }
  $('goal-summary').textContent = state.profile ? `${state.profile.goal} · ${state.profile.level} · 每天 ${state.profile.dailyMinutes} 分钟` : '先设置目标，让文章更贴近你的需求';
  renderReadings(); renderVocab();
}
$('profile-form').onsubmit = async event => {
  event.preventDefault();
  const profile = { goal: $('goal').value, level: $('level').value, dailyMinutes: Number($('minutes').value) };
  if (!Number.isInteger(profile.dailyMinutes) || profile.dailyMinutes < 5 || profile.dailyMinutes > 120) return toast('每天阅读时间请设为 5–120 分钟');
  try {
    if (state.profileRow) await host.data.update('profile.update', state.profileRow.key, state.profileRow.revision, profile);
    else await host.data.create('profile.create', profile);
    await refresh(); show('reading'); toast('学习目标已保存');
  } catch (error) { toast(error.message); }
};
function recommendedReading(reading) {
  if (!state.profile) return true;
  return reading.goals.includes(state.profile.goal) && ['B1', 'B2', 'C1'].indexOf(reading.level) <= ['B1', 'B2', 'C1'].indexOf(state.profile.level);
}
function renderReadings() {
  $('reading-list').replaceChildren();
  const ordered = [...readings].sort((a, b) => Number(recommendedReading(b)) - Number(recommendedReading(a)));
  for (const reading of ordered) {
    const button = document.createElement('button'); button.className = 'reading-card';
    const badge = recommendedReading(reading) ? '推荐 · ' : '';
    const title = document.createElement('strong'); title.textContent = reading.title;
    const meta = document.createElement('span'); meta.textContent = `${badge}${reading.level} · ${reading.source}`;
    button.append(title, meta); button.onclick = () => openReading(reading); $('reading-list').append(button);
  }
}
function openReading(reading) {
  state.article = reading; $('reading-list').hidden = true; $('article').hidden = false; $('lookup').hidden = true;
  $('article-title').textContent = reading.title; $('article-source').textContent = reading.source;
  $('question').textContent = reading.question; $('answer').textContent = reading.answer; $('answer').hidden = true;
  $('paragraphs').replaceChildren();
  for (const paragraph of reading.paragraphs) {
    const p = document.createElement('p');
    for (const sentence of paragraph.match(/[^.!?]+[.!?]+|[^.!?]+$/g) ?? []) {
      for (const token of sentence.match(/[A-Za-z]+(?:'[A-Za-z]+)?|[^A-Za-z]+/g) ?? []) {
        if (!/^[A-Za-z]/.test(token)) { p.append(document.createTextNode(token)); continue; }
        const word = document.createElement('button'); word.className = 'token'; word.textContent = token;
        word.setAttribute('aria-label', `查看 ${token} 的词义`);
        word.onclick = () => lookup(token, sentence.trim()); p.append(word);
      }
      const speak = document.createElement('button'); speak.className = 'sentence-speak'; speak.textContent = '♪';
      speak.setAttribute('aria-label', `朗读句子 ${sentence.trim()}`); speak.onclick = () => speakText(sentence.trim()); p.append(speak);
      const explain = document.createElement('button'); explain.className = 'sentence-speak'; explain.textContent = '释';
      explain.setAttribute('aria-label', `解释句子 ${sentence.trim()}`); explain.onclick = () => lookup(sentence.trim(), sentence.trim()); p.append(explain);
    }
    $('paragraphs').append(p);
  }
}
$('back-to-list').onclick = () => { $('article').hidden = true; $('reading-list').hidden = false; $('lookup').hidden = true; };
$('show-answer').onclick = () => { $('answer').hidden = !$('answer').hidden; };
$('close-lookup').onclick = () => { $('lookup').hidden = true; };
async function lookup(term, sentence) {
  const isWord = /^[a-z]+(?:'[a-z]+)?$/i.test(term);
  state.term = isWord ? term.toLowerCase() : term; state.sentence = sentence; $('lookup').hidden = false;
  $('lookup-term').textContent = term; $('ai-result').hidden = true;
  $('source-en').href = `https://en.wiktionary.org/wiki/${encodeURIComponent(state.term)}`;
  $('source-zh').href = `https://zh.wiktionary.org/wiki/${encodeURIComponent(state.term)}`;
  $('save-word').hidden = !isWord;
  $('wordnet').textContent = !isWord ? '整句释义由 AI 结合原文提供。' : state.dictionary[state.term] ? `WordNet · ${state.dictionary[state.term].join(' / ')}` : 'WordNet 离线摘录暂无此词。';
  if (!isWord) { $('wiktionary').textContent = ''; $('zh-wiktionary').textContent = ''; return; }
  $('wiktionary').textContent = '正在查询 Wiktionary 英英释义…';
  $('zh-wiktionary').textContent = '正在查询 Wiktionary 英汉释义…';
  $('lookup').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  async function englishDefinition() { try {
    const url = `https://en.wiktionary.org/api/rest_v1/page/definition/${encodeURIComponent(state.term)}`;
    const result = await host.network.fetch(url);
    if (state.term !== term.toLowerCase()) return;
    if (result.status !== 200) throw Error(`HTTP ${result.status}`);
    const entries = JSON.parse(result.body).en ?? [];
    const raw = entries.flatMap(entry => entry.definitions ?? []).map(item => item.definition).find(Boolean);
    if (!raw) throw Error('无英文释义');
    const text = new DOMParser().parseFromString(raw, 'text/html').body.textContent.trim();
    $('wiktionary').textContent = `Wiktionary · ${text.slice(0, 400)}`;
  } catch { if (state.term === term.toLowerCase()) $('wiktionary').textContent = '英英在线词义暂不可用。'; } }
  async function chineseDefinition() { try {
    const url = `https://zh.wiktionary.org/w/api.php?action=parse&prop=wikitext&format=json&page=${encodeURIComponent(state.term)}`;
    const result = await host.network.fetch(url);
    if (state.term !== term.toLowerCase()) return;
    if (result.status !== 200) throw Error(`HTTP ${result.status}`);
    const source = JSON.parse(result.body).parse?.wikitext?.['*'] ?? '';
    const english = source.match(/==\s*英[语語]\s*==([\s\S]*?)(?=\n==[^=]|$)/)?.[1] ?? '';
    const raw = english.split('\n').find(line => /^#(?![:*])/.test(line)) ?? '';
    const meaning = raw.replace(/^#+\s*/, '').replace(/\[\[([^\]|]+)\|([^\]]+)\]\]/g, '$2')
      .replace(/\[\[([^\]]+)\]\]/g, '$1').replace(/\{\{[^{}]*\}\}/g, '').replace(/<[^>]+>/g, '').trim();
    if (!meaning || !/[\u3400-\u9fff]/.test(meaning)) throw Error('无中文释义');
    $('zh-wiktionary').textContent = `Wiktionary 英汉 · ${meaning.slice(0, 200)}`;
  } catch { if (state.term === term.toLowerCase()) $('zh-wiktionary').textContent = '英汉在线词义暂不可用；可使用 AI 中文解释。'; } }
  await Promise.all([englishDefinition(), chineseDefinition()]);
}
async function speakText(text) { try { await host.speech.speak(text); } catch (error) { toast(`朗读不可用：${error.message}`); } }
$('speak-term').onclick = () => speakText(state.term);
$('ai-explain').onclick = async () => {
  if (state.busy) return;
  state.busy = true; $('ai-explain').disabled = true; $('ai-explain').textContent = '正在解读…';
  const selected = state.term;
  try {
    const { data } = await host.ai.generate('explain.generate', { term: selected, context: state.sentence, goal: state.profile?.goal ?? '通用英语' });
    if (selected !== state.term) return;
    const panel = $('ai-result'); panel.replaceChildren();
    for (const [label, value] of [['语境释义', data.contextMeaning], ['中文', data.meaningZh], ['English', data.meaningEn], ['用法', data.usage], ['考点', data.examTip]]) {
      const p = document.createElement('p'); const strong = document.createElement('strong'); strong.textContent = `${label}：`;
      p.append(strong, document.createTextNode(value ?? '')); panel.append(p);
    }
    panel.hidden = false;
  } catch (error) { toast(`AI 解读失败：${error.message}`); }
  finally { state.busy = false; $('ai-explain').disabled = false; $('ai-explain').textContent = 'AI 解读'; }
};
$('save-word').onclick = async () => {
  const word = state.term;
  if (state.vocab.some(row => row.data.word === word)) return toast('该词已在生词本');
  try {
    await host.data.create('vocab.create', { word, context: state.sentence, meaning: state.dictionary[word]?.join(' / ') ?? '', source: state.article?.id ?? '', dueAt: Date.now(), intervalDays: 0, repetitions: 0 });
    await refresh(); toast('已加入生词本');
  } catch (error) { toast(error.message); }
};
function renderVocab() {
  $('vocab-list').replaceChildren();
  const due = state.vocab.filter(row => row.data.dueAt <= Date.now()).sort((a, b) => a.data.dueAt - b.data.dueAt);
  state.currentReview = due[0] ?? null; $('review-box').hidden = !state.currentReview;
  if (state.currentReview) { $('review-word').textContent = state.currentReview.data.word; $('review-meaning').textContent = state.currentReview.data.meaning || state.currentReview.data.context; $('review-meaning').hidden = true; $('review-actions').hidden = true; }
  for (const row of state.vocab) {
    const item = document.createElement('div'); item.className = 'vocab-item';
    const body = document.createElement('div'); const title = document.createElement('strong'); title.textContent = row.data.word;
    const context = document.createElement('small'); context.textContent = row.data.context;
    const dueText = document.createElement('small'); dueText.textContent = row.data.dueAt <= Date.now() ? '待复习' : `下次复习：${new Date(row.data.dueAt).toLocaleDateString()}`;
    body.append(title, context, dueText);
    const remove = document.createElement('button'); remove.textContent = '删除'; remove.setAttribute('aria-label', `删除 ${row.data.word}`);
    remove.onclick = async () => { if (!await host.ui.confirm('删除生词', `确定从生词本删除 ${row.data.word}？`)) return; try { await host.data.delete('vocab.delete', row.key, row.revision); await refresh(); } catch (error) { toast(error.message); } };
    item.append(body, remove); $('vocab-list').append(item);
  }
}
$('reveal-meaning').onclick = () => { $('review-meaning').hidden = false; $('review-actions').hidden = false; };
async function review(remembered) {
  const row = state.currentReview; if (!row) return;
  const repetitions = remembered ? row.data.repetitions + 1 : 0;
  const intervalDays = remembered ? (repetitions === 1 ? 1 : Math.min(30, Math.max(2, row.data.intervalDays * 2))) : 0;
  const dueAt = Date.now() + (remembered ? intervalDays * DAY : 10 * 60_000);
  try { await host.data.update('vocab.update', row.key, row.revision, { ...row.data, repetitions, intervalDays, dueAt }); await refresh(); toast(remembered ? '已安排下次复习' : '10 分钟后再练习'); }
  catch (error) { toast(error.message); }
}
$('review-again').onclick = () => review(false); $('review-remember').onclick = () => review(true);
$('generate-review').onclick = async () => {
  if (!state.vocab.length) return toast('先在阅读中保存一些生词');
  const button = $('generate-review'); button.disabled = true; button.textContent = '正在出题…';
  try {
    const due = state.vocab.filter(row => row.data.dueAt <= Date.now());
    const words = (due.length ? due : state.vocab).slice(0, 5).map(row => row.data.word).join(', ');
    const { data } = await host.ai.generate('review.generate', { goal: state.profile?.goal ?? '通用英语', words });
    const panel = $('review-ai'); panel.replaceChildren();
    const title = document.createElement('strong'); title.textContent = 'AI 练习 · 非真题';
    const question = document.createElement('p'); question.textContent = data.question;
    const hint = document.createElement('p'); hint.textContent = `提示：${data.hint}`;
    const answer = document.createElement('p'); answer.textContent = `参考答案：${data.answer}`; answer.hidden = true;
    const reveal = document.createElement('button'); reveal.className = 'secondary'; reveal.textContent = '显示答案'; reveal.onclick = () => { answer.hidden = false; };
    panel.append(title, question, hint, reveal, answer); panel.hidden = false;
  } catch (error) { toast(`出题失败：${error.message}`); }
  finally { button.disabled = false; button.textContent = '用 AI 出一道复习题'; }
};
try {
  $('project').textContent = (await host.project.info()).name;
  applyTheme(await host.ui.theme());
  state.dictionary = wordnet;
  await refresh(); show(state.profile ? 'reading' : 'settings');
} catch (error) { toast(`初始化失败：${error.message}`); }
