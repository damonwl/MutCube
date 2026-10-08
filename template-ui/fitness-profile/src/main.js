import { createApp } from 'vue';
import ProfileForm from './ProfileForm.vue';
import 'vant/lib/index.css';
import './style.css';

export function mount(element, options) {
  const app = createApp(ProfileForm, options);
  const instance = app.mount(element);
  return {
    snapshot: () => instance.snapshot(),
    unmount: () => app.unmount(),
  };
}
