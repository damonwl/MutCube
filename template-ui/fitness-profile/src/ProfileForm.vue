<script setup>
import { reactive, watch, onMounted, onUnmounted, ref } from 'vue';
import { Button, CellGroup, ConfigProvider, Field, Radio, RadioGroup, Stepper } from 'vant';

const props = defineProps({
  initial: { type: Object, required: true },
  editing: { type: Boolean, default: false },
  onSave: { type: Function, required: true },
  onSwitch: { type: Function, required: true },
  onCancel: { type: Function, required: true },
  onChange: { type: Function, required: true },
});
const fields = reactive({ ...props.initial });
const theme = ref('light');
let observer;

function snapshot() {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, String(value ?? '')]));
}

function syncTheme() {
  const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim();
  const hex = bg.match(/^#([0-9a-f]{6})$/i)?.[1];
  if (!hex) return;
  theme.value = [0, 2, 4].reduce((sum, index) => sum + parseInt(hex.slice(index, index + 2), 16), 0) < 384
    ? 'dark' : 'light';
}

watch(fields, () => props.onChange(snapshot()), { deep: true });
onMounted(() => {
  observer = new MutationObserver(syncTheme);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style'] });
  syncTheme();
});
onUnmounted(() => observer?.disconnect());
defineExpose({ snapshot });

function save() {
  const data = {
    heightCm: Number(fields.heightCm),
    weightKg: Number(fields.weightKg),
    goal: fields.goal,
    split: Number(fields.split),
    weeklyDays: Number(fields.weeklyDays),
    experience: fields.experience.trim(),
    equipment: fields.equipment.trim(),
    limitations: fields.limitations.trim(),
    estimatedLoads: fields.estimatedLoads.trim(),
  };
  props.onSave(data);
}
</script>

<template>
  <ConfigProvider :theme="theme" class="fitness-profile">
    <Button block plain native-type="button" class="switch-button" @click="onSwitch(snapshot())">改用 AI 交流</Button>
    <CellGroup inset>
      <Field v-model="fields.heightCm" label="身高 · cm" type="number" inputmode="decimal" placeholder="100–250" />
      <Field v-model="fields.weightKg" label="体重 · kg" type="number" inputmode="decimal" placeholder="20–350" />
    </CellGroup>
    <section class="profile-options">
      <h3>训练目标</h3>
      <RadioGroup v-model="fields.goal" direction="horizontal">
        <Radio name="增肌与力量">增肌与力量</Radio>
        <Radio name="保持体能">保持体能</Radio>
        <Radio name="减脂与体能">减脂与体能</Radio>
      </RadioGroup>
    </section>
    <CellGroup inset>
      <Field label="训练分化">
        <template #input><Stepper v-model="fields.split" :min="1" :max="5" integer /></template>
      </Field>
      <Field label="每周训练天数">
        <template #input><Stepper v-model="fields.weeklyDays" :min="1" :max="6" integer /></template>
      </Field>
    </CellGroup>
    <CellGroup inset>
      <Field v-model="fields.experience" label="训练经验" placeholder="选填，例如训练半年" />
      <Field v-model="fields.equipment" label="可用器械" placeholder="选填，例如健身房、哑铃" />
      <Field v-model="fields.limitations" label="动作限制与偏好" type="textarea" rows="2" autosize placeholder="选填，例如腿部暂不加重" />
      <Field v-model="fields.estimatedLoads" label="熟悉动作的工作重量" type="textarea" rows="3" autosize placeholder="选填：推、拉、蹲或髋铰链的熟悉重量" />
    </CellGroup>
    <p class="profile-help">哑铃按单只重量，其他动作说明重量口径。不知道可留空。</p>
    <Button block type="primary" native-type="button" @click="save">保存基本信息</Button>
    <Button v-if="editing" block plain native-type="button" @click="onCancel(snapshot())">返回训练</Button>
  </ConfigProvider>
</template>
