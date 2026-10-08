import { connectTyped } from '../src/index.js';

interface EnglishActions {
  'profile.create': {
    input: { goal: string; level: string; dailyMinutes: number };
    output: { key: string; revision: number; data: { goal: string; level: string; dailyMinutes: number } };
  };
}

const host = connectTyped<EnglishActions>();
const result = host.invoke('profile.create', { goal: 'IELTS', level: 'B1', dailyMinutes: 20 });
result.then(value => {
  const revision: number = value.data.revision;
  return revision;
});

// @ts-expect-error Study duration must be a number.
host.invoke('profile.create', { goal: 'IELTS', level: 'B1', dailyMinutes: '20' });
// @ts-expect-error The typed client rejects undeclared Action IDs.
host.invoke('profile.missing', {});
