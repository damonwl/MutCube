import { mount } from './main.js';

const result = document.getElementById('result');
mount(document.getElementById('profile'), {
  initial: {
    heightCm: '', weightKg: '', goal: '增肌与力量', split: 3, weeklyDays: 3,
    experience: '', equipment: '', limitations: '', estimatedLoads: '',
  },
  editing: false,
  onChange: () => {},
  onSave: data => { result.textContent = `预览提交：${data.heightCm} cm / ${data.weightKg} kg`; },
  onSwitch: () => { result.textContent = '预览：切换到 AI 交流'; },
  onCancel: () => { result.textContent = '预览：返回训练'; },
});
