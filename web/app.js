'use strict';

/* ================= 工具 ================= */
const $ = sel => document.querySelector(sel);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[c]));

let people = [];          // 人物缓存
let lastStories = [];     // 编辑端故事缓存（含私密）
let currentPersonId = null;
let editPassword = sessionStorage.getItem('editPassword') || '';

/** 统一 API 请求；遇到 401 时提示输入编辑口令并重试一次。 */
async function api(path, options = {}) {
  const headers = Object.assign({ 'X-Edit-Password': editPassword }, options.headers);
  if (options.body) headers['Content-Type'] = 'application/json';
  let res = await fetch(path, Object.assign({}, options, { headers }));
  if (res.status === 401) {
    const pw = prompt('该操作需要编辑口令（若服务端未设置 EDIT_PASSWORD，直接点“确定”）');
    if (pw === null) throw new Error('已取消');
    editPassword = pw;
    sessionStorage.setItem('editPassword', pw);
    headers['X-Edit-Password'] = pw;
    res = await fetch(path, Object.assign({}, options, { headers }));
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error((data && data.error) || ('请求失败：HTTP ' + res.status));
  return data;
}

/** 包装异步调用，出错时弹窗提示。 */
const guard = p => p.catch(err => alert(err.message));

function personName(id) {
  const p = people.find(x => x.id === id);
  return p ? p.name : '（已删除）';
}

function years(p) {
  if (p.birthYear && p.deathYear) return p.birthYear + '—' + p.deathYear;
  if (p.birthYear) return p.birthYear + ' 年生';
  if (p.deathYear) return '？—' + p.deathYear;
  return '';
}

function storyYears(s) {
  if (!s.yearStart) return '年代不详';
  if (s.yearEnd && s.yearEnd !== s.yearStart) return s.yearStart + '—' + s.yearEnd;
  return String(s.yearStart);
}

function storyCard(s) {
  const names = (s.personIds || []).map(personName).join('、');
  return `
    <article class="story-card ${s.public ? '' : 'private'}">
      <header>
        <h4>${s.public ? '' : '🔒 '}${esc(s.title)}</h4>
        <span class="meta">${esc(storyYears(s))}${names ? ' · ' + esc(names) : ''}</span>
      </header>
      <p>${esc(s.content)}</p>
    </article>`;
}

async function refresh() {
  people = await api('/api/people');
  people.sort((a, b) => (a.generation - b.generation) || ((a.birthYear || 9999) - (b.birthYear || 9999)));
}

/* ================= 浏览：按人物 ================= */
function renderPersonList() {
  const box = $('#person-list');
  if (!people.length) {
    box.innerHTML = '<p class="hint">还没有人物。<br>请到「编辑」页添加。</p>';
    return;
  }
  const byGen = {};
  people.forEach(p => { (byGen[p.generation] = byGen[p.generation] || []).push(p); });
  box.innerHTML = Object.keys(byGen).sort((a, b) => a - b).map(g => `
    <div class="gen-group">
      <div class="gen-title">第 ${esc(g)} 世</div>
      ${byGen[g].map(p => `
        <button class="person-item ${p.id === currentPersonId ? 'active' : ''}" data-id="${esc(p.id)}">
          <span class="pname">${esc(p.name)}</span>
          <span class="pyears">${esc(years(p))}</span>
        </button>`).join('')}
    </div>`).join('');
  box.querySelectorAll('.person-item').forEach(btn => {
    btn.onclick = () => {
      currentPersonId = btn.dataset.id;
      renderPersonList();
      guard(renderPersonDetail(currentPersonId));
    };
  });
}

async function renderPersonDetail(id) {
  const p = people.find(x => x.id === id);
  const box = $('#person-detail');
  if (!p) { box.innerHTML = '<p class="hint">人物不存在</p>'; return; }
  // 浏览端只取公开故事
  const stories = await api('/api/stories?publicOnly=true&personId=' + encodeURIComponent(id));
  const father = people.find(x => x.id === p.fatherId);
  const mother = people.find(x => x.id === p.motherId);
  const children = people.filter(x => x.fatherId === id || x.motherId === id);
  box.innerHTML = `
    <h2>${esc(p.name)}${p.gender ? ` <small>${esc(p.gender)}</small>` : ''}</h2>
    <p class="meta">第 ${p.generation} 世${years(p) ? ' · ' + esc(years(p)) : ''}</p>
    ${father || mother ? `<p class="meta">父母：${father ? esc(father.name) : '？'} ／ ${mother ? esc(mother.name) : '？'}</p>` : ''}
    ${children.length ? `<p class="meta">子女：${children.map(c => esc(c.name)).join('、')}</p>` : ''}
    ${p.bio ? `<p class="bio">${esc(p.bio)}</p>` : ''}
    <h3>相关故事 <small>（仅公开，共 ${stories.length} 篇）</small></h3>
    ${stories.length ? stories.map(storyCard).join('') : '<p class="hint">暂无公开故事</p>'}`;
}

/* ================= 浏览：按年代 ================= */
async function renderEraView() {
  const box = $('#browse-era');
  const stories = await api('/api/stories?publicOnly=true');
  if (!stories.length) { box.innerHTML = '<p class="hint">暂无公开故事</p>'; return; }
  const groups = {};
  stories.forEach(s => {
    const k = s.decade == null ? 'unknown' : String(s.decade);
    (groups[k] = groups[k] || []).push(s);
  });
  const keys = Object.keys(groups).sort((a, b) =>
    (a === 'unknown' ? 99999 : +a) - (b === 'unknown' ? 99999 : +b));
  box.innerHTML = keys.map(k => `
    <section class="era-group">
      <h3 class="era-title">${k === 'unknown' ? '年代不详' : esc(k) + ' 年代'}</h3>
      ${groups[k].sort((a, b) => (a.yearStart || 9999) - (b.yearStart || 9999)).map(storyCard).join('')}
    </section>`).join('');
}

/* ================= 编辑：人物管理 ================= */
function renderPeopleTable() {
  const tbody = $('#people-table tbody');
  tbody.innerHTML = people.map(p => {
    const father = people.find(x => x.id === p.fatherId);
    const mother = people.find(x => x.id === p.motherId);
    const parents = [father && father.name, mother && mother.name].filter(Boolean).join(' ／ ');
    return `<tr>
      <td>${esc(p.name)}</td>
      <td>${esc(p.gender || '')}</td>
      <td>第 ${p.generation} 世</td>
      <td>${esc(years(p))}</td>
      <td>${esc(parents)}</td>
      <td class="ops">
        <button class="small" data-act="edit" data-id="${esc(p.id)}">编辑</button>
        <button class="small danger" data-act="del" data-id="${esc(p.id)}">删除</button>
      </td>
    </tr>`;
  }).join('') || '<tr><td colspan="6" class="hint">还没有人物，点击「新增人物」。</td></tr>';
  tbody.querySelectorAll('button').forEach(b => {
    b.onclick = () => b.dataset.act === 'edit' ? openPersonModal(b.dataset.id) : guard(deletePerson(b.dataset.id));
  });
}

function openPersonModal(id) {
  const form = $('#person-form');
  form.reset();
  const el = form.elements;
  const opts = people.filter(p => p.id !== id)
    .map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`).join('');
  el.fatherId.innerHTML = '<option value="">（无）</option>' + opts;
  el.motherId.innerHTML = '<option value="">（无）</option>' + opts;
  $('#person-modal-title').textContent = id ? '编辑人物' : '新增人物';
  const p = id ? people.find(x => x.id === id) : null;
  el.id.value = p ? p.id : '';
  if (p) {
    el.name.value = p.name || '';
    el.gender.value = p.gender || '';
    el.generation.value = p.generation || 1;
    el.birthYear.value = p.birthYear ?? '';
    el.deathYear.value = p.deathYear ?? '';
    el.fatherId.value = p.fatherId || '';
    el.motherId.value = p.motherId || '';
    el.bio.value = p.bio || '';
  }
  $('#person-modal').classList.remove('hidden');
}

async function submitPerson(e) {
  e.preventDefault();
  const el = e.target.elements;
  const body = {
    id: el.id.value || null,
    name: el.name.value.trim(),
    gender: el.gender.value,
    generation: parseInt(el.generation.value, 10) || 1,
    birthYear: el.birthYear.value ? parseInt(el.birthYear.value, 10) : null,
    deathYear: el.deathYear.value ? parseInt(el.deathYear.value, 10) : null,
    fatherId: el.fatherId.value || null,
    motherId: el.motherId.value || null,
    bio: el.bio.value.trim()
  };
  if (body.id) await api('/api/people/' + encodeURIComponent(body.id), { method: 'PUT', body: JSON.stringify(body) });
  else await api('/api/people', { method: 'POST', body: JSON.stringify(body) });
  closeModals();
  await refresh();
  renderPeopleTable();
}

async function deletePerson(id) {
  const p = people.find(x => x.id === id);
  if (!confirm(`确定删除人物「${p ? p.name : id}」吗？\n相关故事会保留，但不再关联此人物。`)) return;
  await api('/api/people/' + encodeURIComponent(id), { method: 'DELETE' });
  if (currentPersonId === id) currentPersonId = null;
  await refresh();
  renderPeopleTable();
}

/* ================= 编辑：故事管理（含私密） ================= */
async function renderStoryList() {
  const box = $('#story-list');
  lastStories = await api('/api/stories?publicOnly=false'); // 编辑端取全部
  box.innerHTML = lastStories.map(s => `
    <div class="story-row ${s.public ? '' : 'private'}">
      <div class="story-row-main">
        <strong>${s.public ? '' : '🔒 '}${esc(s.title)}</strong>
        <span class="meta">${esc(storyYears(s))} · ${(s.personIds || []).map(personName).map(esc).join('、') || '未关联人物'}</span>
      </div>
      <div class="ops">
        <button class="small" data-act="edit" data-id="${esc(s.id)}">编辑</button>
        <button class="small danger" data-act="del" data-id="${esc(s.id)}">删除</button>
      </div>
    </div>`).join('') || '<p class="hint">还没有故事，点击「新增故事」开始记录。</p>';
  box.querySelectorAll('button').forEach(b => {
    b.onclick = () => b.dataset.act === 'edit' ? openStoryModal(b.dataset.id) : guard(deleteStory(b.dataset.id));
  });
}

function openStoryModal(id) {
  const form = $('#story-form');
  form.reset();
  const el = form.elements;
  $('#story-person-checks').innerHTML = people.map(p =>
    `<label class="check"><input type="checkbox" value="${esc(p.id)}"> ${esc(p.name)}</label>`).join('')
    || '<span class="hint">请先在「人物管理」中添加人物</span>';
  $('#story-modal-title').textContent = id ? '编辑故事' : '新增故事';
  const s = id ? lastStories.find(x => x.id === id) : null;
  el.id.value = s ? s.id : '';
  el.isPublic.checked = s ? !!s.public : true;
  if (s) {
    el.title.value = s.title || '';
    el.yearStart.value = s.yearStart ?? '';
    el.yearEnd.value = s.yearEnd ?? '';
    el.content.value = s.content || '';
    $('#story-person-checks').querySelectorAll('input[type=checkbox]').forEach(cb => {
      cb.checked = (s.personIds || []).includes(cb.value);
    });
  }
  $('#story-modal').classList.remove('hidden');
}

async function submitStory(e) {
  e.preventDefault();
  const el = e.target.elements;
  const personIds = [...$('#story-person-checks').querySelectorAll('input:checked')].map(cb => cb.value);
  const body = {
    id: el.id.value || null,
    title: el.title.value.trim(),
    yearStart: el.yearStart.value ? parseInt(el.yearStart.value, 10) : null,
    yearEnd: el.yearEnd.value ? parseInt(el.yearEnd.value, 10) : null,
    personIds,
    public: el.isPublic.checked,
    content: el.content.value.trim()
  };
  if (body.id) await api('/api/stories/' + encodeURIComponent(body.id), { method: 'PUT', body: JSON.stringify(body) });
  else await api('/api/stories', { method: 'POST', body: JSON.stringify(body) });
  closeModals();
  guard(renderStoryList());
}

async function deleteStory(id) {
  const s = lastStories.find(x => x.id === id);
  if (!confirm(`确定删除故事「${s ? s.title : id}」吗？此操作不可恢复。`)) return;
  await api('/api/stories/' + encodeURIComponent(id), { method: 'DELETE' });
  guard(renderStoryList());
}

/* ================= 界面切换 ================= */
function closeModals() {
  document.querySelectorAll('.modal').forEach(m => m.classList.add('hidden'));
}

function switchMain(name) {
  $('#tab-browse').classList.toggle('active', name === 'browse');
  $('#tab-edit').classList.toggle('active', name === 'edit');
  $('#view-browse').classList.toggle('hidden', name !== 'browse');
  $('#view-edit').classList.toggle('hidden', name !== 'edit');
  if (name === 'browse') {
    renderPersonList();
    if (currentPersonId) guard(renderPersonDetail(currentPersonId));
    if (!$('#browse-era').classList.contains('hidden')) guard(renderEraView());
  } else {
    renderPeopleTable();
    guard(renderStoryList());
  }
}

function switchBrowse(name) {
  $('#sub-person').classList.toggle('active', name === 'person');
  $('#sub-era').classList.toggle('active', name === 'era');
  $('#browse-person').classList.toggle('hidden', name !== 'person');
  $('#browse-era').classList.toggle('hidden', name !== 'era');
  if (name === 'era') guard(renderEraView());
}

function switchEdit(name) {
  $('#sub-edit-people').classList.toggle('active', name === 'people');
  $('#sub-edit-stories').classList.toggle('active', name === 'stories');
  $('#edit-people').classList.toggle('hidden', name !== 'people');
  $('#edit-stories').classList.toggle('hidden', name !== 'stories');
}

function bind() {
  $('#tab-browse').onclick = () => switchMain('browse');
  $('#tab-edit').onclick = () => switchMain('edit');
  $('#sub-person').onclick = () => switchBrowse('person');
  $('#sub-era').onclick = () => switchBrowse('era');
  $('#sub-edit-people').onclick = () => switchEdit('people');
  $('#sub-edit-stories').onclick = () => switchEdit('stories');
  $('#btn-add-person').onclick = () => openPersonModal(null);
  $('#btn-add-story').onclick = () => openStoryModal(null);
  $('#person-form').onsubmit = e => guard(submitPerson(e));
  $('#story-form').onsubmit = e => guard(submitStory(e));
  document.querySelectorAll('.modal .cancel').forEach(b => { b.onclick = closeModals; });
  document.querySelectorAll('.modal').forEach(m => {
    m.addEventListener('click', e => { if (e.target === m) closeModals(); });
  });
}

/* ================= 启动 ================= */
// 启动时只初始化浏览端（公开数据）；编辑端数据（含私密故事）
// 在用户切换到「编辑」页时由 switchMain('edit') 按需加载，
// 避免首页加载就请求 publicOnly=false 导致私密数据下发 / 弹出口令框。
(async function init() {
  bind();
  try {
    await refresh();
    renderPersonList();
  } catch (err) {
    alert('初始化失败：' + err.message);
  }
})();
