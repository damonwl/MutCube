// Actual protocol-2 fitness page; isolated DOM/storage/native-confirmation doubles, no real user facts.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { randomUUID } = require('node:crypto');
const html = fs.readFileSync('app/src/main/assets/templates/fitness/index.html', 'utf8');
const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)][0][1];
const fixtureProfile = {heightCm:190,weightKg:94,goal:'增肌与力量',split:3,weeklyDays:3,experience:'隔离测试',equipment:'哑铃',limitations:'腿部保持',estimatedLoads:''};
const plan = date => ({name:'隔离推训练',targetDate:date,description:'仅供测试，不是用户计划',exercises:[{id:'press',name:'卧推',weightKg:12.5,sets:2,repsMin:10,repsMax:12,rirMin:1,rirMax:2,note:''}]});
function backend(){return {profiles:[],intakes:[],drafts:[],training:[],plans:[],requests:[],current:{revision:0,key:null,data:null},bases:new Map(),calls:[],clock:1000,failSave:false,failGenerate:false,allowConfirm:true,revoked:false};}
function load(store){
 const elements=new Map(),listeners={},timers=new Set();
 class Element{
  constructor(tag='div'){this.tag=tag;this.value='';this.dataset={};this.disabled=false;this.textContent='';this.children=[];this._html='';this.classList={add(){},remove(){}};}
  set innerHTML(text){this._html=text;parse(text);}get innerHTML(){return this._html;}
  addEventListener(type,fn){this['event_'+type]=fn;}insertAdjacentHTML(_,text){parse(text);}closest(){return this;}focus(){}querySelectorAll(){return [];}setAttribute(){}
 }
 function parse(text){for(const m of text.matchAll(/<(\w+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)){const [,tag,attrs,id]=m,e=new Element(tag);e.value=(attrs.match(/\bvalue="([^"]*)"/)||[])[1]||'';if(tag==='textarea')e.value=(text.slice(m.index+m[0].length).match(/^([\s\S]*?)<\/textarea>/)||[])[1]||'';
  if(tag==='select'){const content=(text.slice(m.index+m[0].length).match(/^([\s\S]*?)<\/select>/)||[])[1]||'',opts=[...content.matchAll(/<option([^>]*)>([^<]*)<\/option>/g)],chosen=opts.find(o=>/selected/.test(o[1]))||opts[0];if(chosen)e.value=(chosen[1].match(/value="([^"]*)"/)||[])[1]||chosen[2];}elements.set(id,e);}}
 parse(html.split('<script>')[0]);
 const document={getElementById:id=>elements.get(id)||null,querySelectorAll:()=>[],addEventListener:(type,fn)=>listeners[type]=fn};
 const bridge={postMessage(raw){const body=JSON.parse(raw);store.calls.push(body);let data,error;try{
  if(body.capability!=='action.run'){data={};}
  else{assert.equal(body.protocolVersion,2);if(store.revoked)throw Error('授权已撤销');const {actionId,data:input}=body.input;let [collection,verb]=actionId.split('.');if(collection==='profile'&&verb.startsWith('draft_')){collection='intake';verb=verb.slice(6)}
   const names={intake:'intakes',profile:'profiles',draft:'drafts',training:'training',plan:'plans'},rows=store[names[collection]];
   const append=(arr,value,key=body.requestId)=>{const old=arr.find(r=>r.key===key);if(old){assert.equal(JSON.stringify(old.data),JSON.stringify(value));return old;}const row={key,revision:1,data:structuredClone(value),updatedAt:++store.clock};arr.unshift(row);return row;};
   if(verb==='assist'){if(store.failGenerate)throw Error('fixture generation failed');data={reply:'已整理，身高190厘米、体重94公斤，请核对并保存。',ready:true,fields:{...input.form,heightCm:input.form.heightCm||'190',weightKg:input.form.weightKg||'94'}};}
   else if(verb==='list'){const filtered=rows.filter(r=>!input.beforeTime||r.updatedAt<input.beforeTime||(r.updatedAt===input.beforeTime&&r.key<input.beforeKey)),records=filtered.slice(0,20),last=records.at(-1);data={records,next:records.length===20?{beforeTime:last.updatedAt,beforeKey:last.key}:null};}
   else if(verb==='current')data=store.current;
   else if(verb==='save'||verb==='submit'){if(store.failSave)throw Error('fixture storage failure');data=append(rows,input);}
   else if(verb==='update'){if(store.failSave)throw Error('fixture storage failure');const row=rows.find(r=>r.key===input.key);assert.equal(row.revision,input.expectedRevision);row.data=structuredClone(input.data);row.revision++;row.updatedAt=++store.clock;rows.sort((a,b)=>b.updatedAt-a.updatedAt);data=row;}
   else if(verb==='generate'){assert.deepEqual(Object.keys(input).sort(),['baseRevision','request','targetDate']);assert.equal(input.baseRevision,store.current.revision);append(store.requests,input);if(store.failGenerate)throw Error('fixture generation failed');assert.ok(store.profiles.length);data=plan(input.targetDate);append(rows,data);store.bases.set(body.requestId,input.baseRevision);}
   else if(verb==='confirm'){if(!store.allowConfirm)data={confirmed:false};else{assert.equal(input.expectedRevision,store.current.revision);if(store.bases.get(input.key)!==input.expectedRevision)throw Error('Proposal source version changed');const row=rows.find(r=>r.key===input.key);store.current={revision:store.current.revision+1,key:row.key,data:row.data};data={confirmed:true,key:row.key,revision:store.current.revision};}}
   else throw Error('Unknown action '+actionId);
  }
 }catch(e){error=e.message}
 queueMicrotask(()=>bridge.onmessage({data:JSON.stringify(error?{requestId:body.requestId,error}:{requestId:body.requestId,result:{key:body.requestId,data,pendingConfirmation:body.input?.actionId==='plan.generate'}})}));}};
 const context=vm.createContext({document,MutCube:bridge,crypto:{randomUUID},window:{addEventListener:(type,fn)=>listeners[type]=fn},console,setTimeout:(fn,ms)=>{const t=setTimeout(()=>{timers.delete(t);fn()},ms);timers.add(t);return t;},clearTimeout:t=>{timers.delete(t);clearTimeout(t)}});
 vm.runInContext(script,context);
 const evaluate=s=>vm.runInContext(s,context),$=id=>document.getElementById(id);
 return {context,evaluate,$,async ready(){while(evaluate('busy'))await new Promise(r=>setTimeout(r,0));},click(data){const b=new Element('button');b.dataset=data;listeners.click({target:b});},input(field,value){const e=new Element('input');e.dataset.field=field;e.value=value;$('content').event_input({target:e});},dispose(){for(const t of timers)clearTimeout(t);}};
}
(async()=>{
 const intakeStore=backend();let intakeApp=load(intakeStore);await intakeApp.ready();assert.equal(intakeApp.evaluate('profileMode'),'choose');
 await intakeApp.$('startProfileAi').onclick();intakeApp.$('profileInput').value='身高190厘米，体重94公斤，想三分化增肌';
 intakeStore.failGenerate=true;await intakeApp.$('sendProfileAi').onclick();assert.equal(intakeStore.profiles.length,0);assert.equal(intakeStore.intakes[0].data.dialogue.length,1);
 intakeStore.failGenerate=false;await intakeApp.$('sendProfileAi').onclick();assert.equal(intakeApp.evaluate('profileEntry.heightCm'),'190');assert.equal(intakeStore.profiles.length,0);
 await intakeApp.$('reviewProfile').onclick();assert.equal(intakeApp.$('height').value,'190');intakeApp.$('height').value='181';await intakeApp.$('profileToAi').onclick();
 intakeApp.$('profileInput').value='补充器械信息';intakeApp.$('profileInput').oninput();assert.equal(await intakeApp.context.window.MutCubeBeforeLeave(),true);
 intakeApp.dispose();intakeApp=load(intakeStore);await intakeApp.ready();assert.equal(intakeApp.$('profileInput').value,'补充器械信息');await intakeApp.$('startProfileForm').onclick();assert.equal(intakeApp.$('height').value,'181');
 intakeApp.$('height').value='181.5';intakeStore.failSave=true;assert.equal(await intakeApp.context.window.MutCubeBeforeLeave(),false);intakeStore.failSave=false;intakeApp.$('height').value='181';
 await intakeApp.$('profileForm').onsubmit({preventDefault(){}});assert.equal(intakeStore.profiles.length,1);assert.equal(intakeStore.profiles[0].data.heightCm,181);intakeApp.dispose();
 const store=backend();let app=load(store);await app.ready();assert.equal(app.evaluate('page'),'profile');
 await app.$('startProfileForm').onclick();
 for(const [id,value] of Object.entries({height:'190',weight:'94',goal:'增肌与力量',split:'3',weekly:'3',experience:'隔离测试',equipment:'哑铃',limitations:'腿部保持',loads:''}))app.$(id).value=value;
 await app.$('profileForm').onsubmit({preventDefault(){}});assert.equal(store.profiles.length,1);assert.equal(app.evaluate('page'),'train');
 await app.$('generate').onclick();assert.equal(store.plans.length,1);assert.equal(store.current.key,null);
 store.allowConfirm=false;await app.$('confirmPlan').onclick();assert.equal(store.current.key,null);
 store.allowConfirm=true;app.evaluate('preview(candidates[0])');await app.$('confirmPlan').onclick();assert.equal(store.current.revision,1);assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'');
 assert.match(app.$('content').innerHTML,/待执行计划/);
 app.click({removeExercise:'0'});assert.ok(app.$('confirmRemoveExercise'));
 await app.$('confirmRemoveExercise').onclick();assert.equal(app.evaluate('draft.exercises.length'),0);assert.equal(store.current.data.exercises.length,1);
 assert.throws(()=>app.evaluate('actualTraining()'),/至少保留一个实际训练动作/);
 app.click({restoreExercise:'press'});await app.ready();assert.equal(app.evaluate('draft.exercises.length'),1);
 assert.match(app.$('content').innerHTML,/data-edit-set="0:0:reps"/);assert.doesNotMatch(app.$('content').innerHTML,/wheel-wrap|data-wheel-field/);
 app.click({editSet:'0:0:reps'});assert.equal(app.$('setValue').value,'10');assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'');
 app.$('cancelSetValue').onclick();assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'');
 app.click({editSet:'0:0:reps'});app.$('setValue').value='12.5';app.$('saveSetValue').onclick();assert.match(app.$('setValueError').textContent,/整数/);assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'');
 app.$('setValue').value='12';app.$('saveSetValue').onclick();assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'12');
 app.click({editSet:'0:0:rir'});assert.equal(app.$('setValue').value,'1');app.$('valuePlus').onclick();app.$('valuePlus').onclick();app.$('saveSetValue').onclick();assert.equal(app.evaluate('draft.exercises[0].sets[0].rir'),'2');
 app.click({editSet:'0:0:rir'});app.$('clearValue').onclick();assert.equal(app.evaluate('draft.exercises[0].sets[0].rir'),'');
 app.click({editSet:'0:0:rir'});app.$('setValue').value='2';app.$('saveSetValue').onclick();await app.evaluate('flushDraft()');assert.equal(store.drafts[0].data.exercises[0].sets[0].reps,'12');
 app.dispose();app=load(store);await app.ready();assert.equal(app.evaluate('page'),'train');assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'12');
 // Saving failure prevents navigation and preserves the input.
 app.click({editSet:'0:1:reps'});app.$('setValue').value='11';app.$('saveSetValue').onclick();app.click({editSet:'0:1:rir'});app.$('setValue').value='1';app.$('saveSetValue').onclick();store.failSave=true;assert.equal(await app.context.window.MutCubeBeforeLeave(),false);assert.match(app.$('status').textContent,/保存失败/);store.failSave=false;assert.equal(await app.context.window.MutCubeBeforeLeave(),true);
 await app.$('chat').onclick();assert.equal(store.calls.at(-1).capability,'navigation.openChat');
 // Activation switches the actual entry plan immediately but preserves the prior input for recovery.
 await app.$('generate').onclick();await app.$('confirmPlan').onclick();assert.equal(store.current.revision,2);assert.equal(app.evaluate('draft.planKey'),store.current.key);assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'');
 assert.match(app.$('content').innerHTML,/待执行计划/);
 assert.ok(app.$('restorePreviousDraft'));await app.$('restorePreviousDraft').onclick();assert.notEqual(app.evaluate('draft.planKey'),store.current.key);assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'12');
 await app.$('switchCurrentPlan').onclick();assert.equal(app.evaluate('draft.planKey'),store.current.key);assert.equal(store.drafts.length,2);
 await app.$('restorePreviousDraft').onclick();assert.equal(app.evaluate('draft.exercises[0].sets[0].reps'),'12');
 // Save fact first; a failing model must not lose it or enable a second submission.
 store.failGenerate=true;await app.$('submit').onclick();assert.equal(store.training.length,1);assert.match(app.$('status').textContent,/generation failed/);const submits=store.calls.filter(c=>c.input?.actionId==='training.submit').length;
 store.failGenerate=false;await app.$('generate').onclick();assert.equal(store.training.length,1);assert.equal(store.calls.filter(c=>c.input?.actionId==='training.submit').length,submits);
 assert.equal((app.$('content').innerHTML.match(/data-preview=/g)||[]).length,1);assert.ok(store.plans.length>1); // UI hides history without deleting versions.
 const savedDraftCount=store.drafts.length;
 await app.$('confirmPlan').onclick();assert.equal(app.evaluate('draft'),null);assert.equal(store.drafts.length,savedDraftCount);
 assert.match(app.$('content').innerHTML,/今天已有训练记录，这份新计划已启用/);
 app.evaluate("page='calendar';render()");assert.match(app.$('content').innerHTML,/12 次/);assert.match(app.$('content').innerHTML,/RIR 2/);
 app.evaluate('openCorrection(logs[0])');assert.ok(app.$('saveCorrection'));
 await app.$('saveCorrection').onclick();assert.equal(store.training.length,2);assert.equal(app.evaluate('logs.length'),1);
 assert.equal(app.evaluate('allLogs.length'),2);assert.match(app.$('status').textContent,/更正已保存/);
 // Complete pagination, more than one page and more than old 100-record limit.
 for(let i=0;i<103;i++)store.training.unshift({key:'fixture-'+i,revision:1,updatedAt:++store.clock,data:{...store.training[0].data,date:'2025-01-01'}});
 assert.equal((await app.evaluate("listAll('training.list')")).length,105);
 app.dispose();app=load(store);await app.ready();assert.equal(app.evaluate('allLogs.length'),105);assert.equal(app.evaluate('logs.length'),2);assert.equal(app.evaluate('draft'),null);assert.ok(app.$('content').innerHTML.includes('今日训练已记录'));
 assert.equal(app.evaluate('targetDate'),app.evaluate('tomorrow()')); // Today's submitted workout must not default to another plan for today after reopen.
 store.revoked=true;await app.$('refresh').onclick();assert.match(app.$('status').textContent,/授权已撤销/);
 app.dispose();
 // Yesterday's completed plan remains the CURRENT pointer until the user confirms a
 // new candidate. A fresh day must not recreate yesterday's exercise entry form.
 const rollover=backend(),yesterday=new Date();yesterday.setDate(yesterday.getDate()-1);
 const oldDate=[yesterday.getFullYear(),String(yesterday.getMonth()+1).padStart(2,'0'),String(yesterday.getDate()).padStart(2,'0')].join('-');
 const today=new Date(),todayKey=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');
 const currentPlan=plan(oldDate),nextPlan={...plan(todayKey),name:'下一次肩和腿'};
 rollover.profiles.push({key:'profile',revision:1,data:fixtureProfile,updatedAt:1});
 rollover.current={revision:1,key:'yesterday-plan',data:currentPlan};
 rollover.plans.push({key:'next-candidate',revision:1,data:nextPlan,updatedAt:3},{key:'yesterday-plan',revision:1,data:currentPlan,updatedAt:1});
 rollover.bases.set('next-candidate',1);
 rollover.training.push({key:'training-'+oldDate,revision:1,updatedAt:4,data:{date:oldDate,planKey:'yesterday-plan',plan:currentPlan,exercises:[{exerciseId:'press',name:'卧推',sets:[{weightKg:12.5,reps:12,rir:2}]}],note:''}});
 rollover.drafts.push({key:'finished-draft',revision:1,updatedAt:5,data:{date:oldDate,planKey:'yesterday-plan',plan:currentPlan,exercises:[],note:'',submitted:true}});
 let nextDay=load(rollover);await nextDay.ready();
 assert.equal(nextDay.evaluate('draft'),null);assert.equal(nextDay.$('submit'),null);
 assert.match(nextDay.$('content').innerHTML,/等待下一次训练计划/);
 assert.match(nextDay.$('content').innerHTML,/待执行计划<\/h3><p class="muted">暂无/);
 assert.doesNotMatch(nextDay.$('content').innerHTML,/当前已启用计划/);
 assert.match(nextDay.$('content').innerHTML,/下一次肩和腿/);
 assert.equal(nextDay.evaluate(`nextTrainingDate('${oldDate}')`),todayKey);
 nextDay.evaluate("page='mine';render()");assert.match(nextDay.$('content').innerHTML,/待执行计划：暂无/);
 assert.match(nextDay.$('content').innerHTML,/上一份已完成：隔离推训练/);
 nextDay.evaluate("page='train';render()");
 rollover.drafts.unshift({key:'stale-empty-draft',revision:1,updatedAt:6,data:{date:todayKey,planKey:'yesterday-plan',plan:currentPlan,exercises:currentPlan.exercises.map(a=>({exerciseId:a.id,name:a.name,sets:Array.from({length:a.sets},()=>({weightKg:String(a.weightKg),reps:'',rir:''}))})),note:'',submitted:false}});
 await nextDay.evaluate('refresh()');assert.equal(nextDay.evaluate('draft'),null);
 nextDay.evaluate('candidates=[];render()');assert.match(nextDay.$('content').innerHTML,/生成下一次计划/);
 await nextDay.evaluate('refresh()');assert.equal(nextDay.evaluate('draft'),null);
 nextDay.evaluate('preview(candidates[0])');await nextDay.$('confirmPlan').onclick();
 assert.equal(nextDay.evaluate('draft.planKey'),'next-candidate');assert.equal(nextDay.evaluate('draft.date'),nextDay.evaluate('TODAY'));
 nextDay.dispose();
 const backfill=backend();backfill.profiles.push({key:'profile',revision:1,data:fixtureProfile,updatedAt:1});
 backfill.current={revision:2,key:'new-plan',data:nextPlan};
 backfill.drafts.push({key:'new-draft',revision:1,updatedAt:9,data:{date:todayKey,planKey:'new-plan',plan:nextPlan,exercises:[{exerciseId:'press',name:'卧推',sets:[{weightKg:'12.5',reps:'',rir:''}]}],note:'',submitted:false}});
 backfill.drafts.push({key:'old-backfill',revision:1,updatedAt:8,data:{date:oldDate,planKey:'old-plan',plan:currentPlan,exercises:[{exerciseId:'press',name:'卧推',sets:[{weightKg:'12.5',reps:'12',rir:'2'}]}],note:'',submitted:false}});
 const backfillApp=load(backfill);await backfillApp.ready();assert.match(backfillApp.$('content').innerHTML,/恢复 .* 的旧计划草稿/);
 await backfillApp.$('restorePreviousDraft').onclick();assert.equal(backfillApp.evaluate('draft.date'),oldDate);
 backfillApp.dispose();console.log('健身协议2页面回归通过：计划与训练状态分离、跨天日期、已完成计划不重用、同日新候选不建草稿、旧日期草稿恢复及原有流程。');
})().catch(e=>{console.error(e);process.exitCode=1;});
