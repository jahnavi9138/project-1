'use strict';

const $ = selector => document.querySelector(selector);
const main = $('#main');
const editor = $('#editor');
const directoryFilter = { q: '', department: '', semester: '0', page: 1 };
const attendanceFilter = { date: '', department: '', semester: '0' };
let session, profile, listing, view = '', viewSequence = 0, listSequence = 0;
let formSubmit, searchTimer, noticeTimer;

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}
function dateLabel(s, options = { day: 'numeric', month: 'short', year: 'numeric' }) {
  return new Intl.DateTimeFormat('en-IN', options).format(new Date(s + 'T12:00:00'));
}
function inform(message) {
  const n = $('#notice');
  clearTimeout(noticeTimer); n.textContent = message; n.hidden = false;
  noticeTimer = setTimeout(() => { n.hidden = true; }, 3500);
}
function errorAt(el, msg) { el.textContent = msg; el.hidden = !msg; }
function panelError(error) {
  return `<div class="empty"><p role="alert">${esc(error.message)}</p><button class="btn secondary" data-action="retry">Try again</button></div>`;
}
async function api(path, method = 'GET', data) {
  const options = { method, credentials: 'same-origin', cache: 'no-store' };
  if (method !== 'GET') {
    options.headers = {'Content-Type':'application/json','X-CSRF-Token':session?.csrf || ''};
    options.body = JSON.stringify(data || {});
  }
  let response;
  try { response = await fetch('/api' + path, options); }
  catch { throw new Error('Cannot reach the register. Check that the host laptop is running the app.'); }
  const result = await response.json();
  if (!response.ok) {
    if ((response.status === 401 || response.status === 403) && session?.signedIn && path !== '/login') {
      if (editor.open) editor.close();
      await start();
    }
    throw new Error(result.error || 'The request could not be completed.');
  }
  return result;
}

function options(values, selected) {
  return values.map(([value, label]) => `<option value="${esc(value)}" ${String(value) === String(selected) ? 'selected' : ''}>${esc(label)}</option>`).join('');
}
function departmentOptions(selected, all = true) {
  const pairs = session.departments.map(d => [d.code, all ? d.code : d.code + ' · ' + d.name]);
  return options([...(all ? [['', 'All departments']] : [['', 'Select department']]), ...pairs], selected);
}
function semesterOptions(selected, all = true) {
  const pairs = Array.from({length:8}, (_, i) => [i + 1, 'Semester ' + (i + 1)]);
  return options(all ? [['0','All semesters'], ...pairs] : pairs, selected);
}
function departmentName(code) { return session.departments.find(d => d.code === code)?.name || code; }
function badge(value) { return `<span class="badge ${esc(value.toLowerCase())}">${esc(value)}</span>`; }
function person(row) {
  const parts = row.name.trim().split(/\s+/);
  const initials = [parts[0], ...(parts.length > 1 ? [parts[parts.length - 1]] : [])].map(p => Array.from(p)[0]).join('');
  return `<div class="person"><span class="avatar tone${row.id % 4}" aria-hidden="true">${esc(initials)}</span><div>
    <a href="#student/${row.id}">${esc(row.name)}</a><span class="miniroll">${esc(row.roll)}</span></div></div>`;
}
function metrics(items) {
  return `<div class="metrics">${items.map(([value,label]) => `<div class="metric"><b>${esc(value)}</b><span>${esc(label)}</span></div>`).join('')}</div>`;
}
function formField(label, name, value, attrs = '', wide = false) {
  return `<label class="${wide ? 'wide' : ''}">${label}<input name="${name}" value="${esc(value)}" ${attrs}></label>`;
}
function openForm(title, fields, submitLabel, callback, { danger = false, extra = '' } = {}) {
  if (editor.open) editor.close();
  $('#dialog-title').textContent = title;
  $('#dialog-fields').innerHTML = fields;
  $('#dialog-extra').innerHTML = extra;
  $('#dialog-submit').textContent = submitLabel;
  $('#dialog-submit').className = 'btn ' + (danger ? 'danger' : 'primary');
  $('#dialog-submit').disabled = false;
  errorAt($('#dialog-error'), '');
  formSubmit = callback;
  editor.showModal();
  const first = $('#dialog-fields input, #dialog-fields select');
  if (first) first.focus();
}
$('#editor-form').addEventListener('submit', async e => {
  e.preventDefault();
  const button = $('#dialog-submit');
  if (button.disabled) return;
  button.disabled = true;
  errorAt($('#dialog-error'), '');
  try {
    await formSubmit(Object.fromEntries(new FormData(e.target)));
    if (editor.open) editor.close();
  } catch (err) { if (editor.open) errorAt($('#dialog-error'), err.message); }
  finally { button.disabled = false; }
});
editor.addEventListener('close', () => {
  if (editor.open) return;
  $('#dialog-fields').replaceChildren();
  $('#dialog-extra').replaceChildren();
  formSubmit = undefined;
});
editor.addEventListener('cancel', e => { if ($('#dialog-submit').disabled) e.preventDefault(); });
editor.querySelectorAll('[data-close]').forEach(b => b.onclick = () => { if (!$('#dialog-submit').disabled) editor.close(); });

async function start() {
  try {
    session = await api('/session');
    $('#navigation').hidden = $('#signout').hidden = !session.signedIn;
    if (session.signedIn) {
      attendanceFilter.date ||= session.today;
      await renderRoute();
    } else {
      view = 'auth'; ++viewSequence; renderAuth();
    }
  } catch (e) { main.innerHTML = panelError(e); }
}
function renderAuth() {
  const setup = session.setupRequired;
  const remoteSetup = setup && !session.onHost;
  const title = setup ? 'Set up staff access' : 'Staff sign-in';
  document.title = title + ' · Campus Register';
  main.innerHTML = `<section class="auth">
    <div class="auth-art" aria-hidden="true"><div class="notebook"><i></i></div></div>
    <div class="auth-copy"><h1>${title}</h1>
    <p>${remoteSetup ? 'On the host laptop, open localhost in your browser and set the first staff password.' :
      setup ? 'Choose the password your team will use to open the student register.' : 'Enter the team password to open the student register.'}</p>
    ${remoteSetup ? '<button class="btn secondary" data-action="retry">Check again</button>' : `<form id="auth-form">
    <label>Staff password<input type="password" name="password" required ${setup ? 'minlength="10"' : ''} maxlength="128" autocomplete="${setup ? 'new-password' : 'current-password'}" ${setup ? 'placeholder="At least 10 characters"' : ''}></label>
    ${setup ? '<label>Confirm password<input type="password" name="confirm" required minlength="10" maxlength="128" autocomplete="new-password"></label>' : ''}
    <p class="error" id="auth-error" role="alert" hidden></p>
    <button class="btn primary">${setup ? 'Create staff access' : 'Sign in'}</button></form>`}</div></section>`;
  const form = $('#auth-form');
  if (!form) return;
  form.onsubmit = async event => {
    event.preventDefault();
    const fields = new FormData(form), password = fields.get('password'), error = $('#auth-error');
    if (setup && password !== fields.get('confirm')) { errorAt(error, 'The two passwords must match.'); return; }
    const submit = form.querySelector('button'); submit.disabled = true; errorAt(error, '');
    try {
      session = await api(setup ? '/setup' : '/login', 'POST', {password});
      await start();
    } catch (e) { errorAt(error, e.message); submit.disabled = false; }
  };
}
$('#signout').onclick = async () => {
  try {
    await api('/logout','POST');
    directoryFilter.q = ''; directoryFilter.department = ''; directoryFilter.semester = '0'; directoryFilter.page = 1;
    profile = listing = undefined;
    location.hash = 'directory';
    await start();
  } catch (e) { inform(e.message); }
};

async function renderRoute() {
  if (!session?.signedIn) return;
  const hash = location.hash.slice(1);
  const student = /^student\/([1-9][0-9]*)$/.exec(hash);
  view = student ? 'profile' : hash === 'attendance' ? 'attendance' : 'directory';
  const seq = ++viewSequence;
  clearTimeout(searchTimer);
  if (editor.open && !$('#dialog-submit').disabled) editor.close();
  for (const a of document.querySelectorAll('nav a')) {
    if (a.dataset.nav === (view === 'profile' ? 'directory' : view)) a.setAttribute('aria-current','page');
    else a.removeAttribute('aria-current');
  }
  document.title = (view === 'profile' ? 'Student profile' : view === 'attendance' ? 'Attendance register' : 'Student directory') + ' · Campus Register';
  if (view === 'directory') { renderDirectory(); await loadDirectory(seq); }
  else if (view === 'attendance') { renderAttendance(); await loadAttendance(seq); }
  else {
    main.innerHTML = '<p class="loading">Opening student profile…</p>';
    try {
      const result = await api('/students/' + student[1]);
      if (seq !== viewSequence) return;
      profile = result; renderProfile();
    } catch (e) {
      if (seq === viewSequence) main.innerHTML = '<a class="back" href="#directory">← Directory</a>' + panelError(e);
    }
  }
}
$('.skip').addEventListener('click', e => {
  e.preventDefault(); main.focus(); main.scrollIntoView();
});
window.addEventListener('hashchange', () => {
  window.scrollTo(0, 0);
  renderRoute();
});

const addStudentButton = '<button class="btn accent" data-action="add-student"><span class="plus" aria-hidden="true">+</span> Add student</button>';
function renderDirectory() {
  const f = directoryFilter;
  main.innerHTML = `<section class="hero"><div class="hero-top"><div>
    <span class="hero-date">${dateLabel(session.today,{weekday:'long',day:'numeric',month:'long',year:'numeric'})}</span>
    <h1>Student directory</h1></div>${addStudentButton}</div><div id="counts">${metrics([['–','Registered students'],['–','Active students'],['–','Departments represented']])}</div></section>
    <section class="panel" aria-label="Student directory records"><div class="toolbar">
    <label class="search-label">Search students<span class="search-box"><input id="search" type="search" value="${esc(f.q)}" maxlength="100" placeholder="Name or roll number"></span></label>
    <label>Department<select id="dept" aria-label="Department">${departmentOptions(f.department)}</select></label>
    <label>Semester<select id="semester" aria-label="Semester">${semesterOptions(f.semester)}</select></label>
    <button class="textbtn" id="clear-filters" data-action="clear" hidden>Clear filters</button></div>
    <div id="results" aria-live="polite"><p class="loading">Loading students…</p></div></section>`;
  $('#search').oninput = e => {
    f.q = e.target.value; f.page = 1; ++listSequence;
    clearTimeout(searchTimer); searchTimer = setTimeout(() => loadDirectory(viewSequence), 160);
  };
  $('#dept').onchange = e => { f.department = e.target.value; f.page = 1; loadDirectory(viewSequence); };
  $('#semester').onchange = e => { f.semester = e.target.value; f.page = 1; loadDirectory(viewSequence); };
}
async function loadDirectory(seq) {
  if (view !== 'directory') return;
  const query = ++listSequence;
  $('#clear-filters').hidden = !directoryFilter.q && !directoryFilter.department && directoryFilter.semester === '0';
  try {
    const data = await api('/students?' + new URLSearchParams(directoryFilter));
    if (seq !== viewSequence || query !== listSequence) return;
    listing = data; directoryFilter.page = data.page;
    const s = data.summary;
    $('#counts').innerHTML = metrics([[s.total,'Registered students'],[s.active,'Active students'],[s.departments,'Departments represented']]);
    if (!s.total) {
      $('#results').innerHTML = `<div class="empty"><h3>No students yet</h3><p>Add your first student or try the register with eight fictional records.</p>
        <button class="btn secondary" data-action="demo">Load demo records</button></div>`;
      return;
    }
    if (!data.matches) {
      $('#results').innerHTML = `<div class="empty"><h3>No matching students</h3><p>Try another name, roll number or filter.</p><button class="btn secondary" data-action="clear">Clear filters</button></div>`;
      return;
    }
    const rows = data.items.map(s => `<tr><td class="person-cell">${person(s)}</td><td class="roll">${esc(s.roll)}</td>
      <td class="dept-cell"><abbr title="${esc(departmentName(s.department))}">${esc(s.department)}</abbr><span class="mobile-label"> · Sem ${s.semester}</span></td>
      <td class="semester-cell">Sem ${s.semester}</td><td class="status-cell">${badge(s.status)}</td></tr>`).join('');
    const first = (data.page-1)*data.pageSize+1;
    $('#results').innerHTML = `<table class="directory-table" aria-label="Students"><thead><tr><th>Student</th><th>Roll number</th><th>Department</th><th>Semester</th><th>Status</th></tr></thead><tbody>${rows}</tbody></table>
      <div class="table-footer"><span>${first}–${first + data.items.length-1} of ${data.matches} students</span>
      <div class="pager"><button class="btn secondary" data-action="prev" ${data.page === 1 ? 'disabled' : ''}>Previous</button>
      <button class="btn secondary" data-action="next" ${first + data.items.length > data.matches ? 'disabled' : ''}>Next</button></div></div>`;
  } catch (e) { if (seq === viewSequence && query === listSequence) $('#results').innerHTML = panelError(e); }
}

function openStudent(student) {
  const s = student || {name:'',roll:'',department:'',semester:1,email:'',status:'Active'};
  const fields = `<div class="fields">
    ${formField('Full name','name',s.name,'required maxlength="90" autocomplete="off"',true)}
    ${formField('Roll number','roll',s.roll,'required maxlength="24" placeholder="CSE24-001"')}
    <label>Semester<select name="semester" aria-label="Semester">${semesterOptions(s.semester,false)}</select></label>
    <label class="wide">Department<select name="department" aria-label="Department" required>${departmentOptions(s.department,false)}</select></label>
    ${formField('Email (optional)','email',s.email,'type="email" maxlength="160"',true)}
    <label class="wide">Status<select name="status" aria-label="Status">${options(['Active','Graduated','Withdrawn'].map(i=>[i,i]),s.status)}</select></label></div>`;
  openForm(student ? 'Edit student' : 'Add student', fields, 'Save student', async values => {
    const result = await api(student ? '/students/' + s.id : '/students', student ? 'PUT' : 'POST',
      student ? {...values,version:s.version} : values);
    inform(student ? 'Student updated.' : 'Student added.');
    if (view === 'profile' && profile.student.id === result.id) await renderRoute();
    else location.hash = 'student/' + result.id;
  });
}
function renderProfile() {
  const {student:s,marks,history,markedDays,presentDays} = profile;
  document.title = s.name + ' · Campus Register';
  const markRows = marks.map(m => `<tr><td class="subject">${esc(m.subject)}</td><td class="assessment">${esc(m.assessment)}</td>
    <td>${esc(m.score)} / ${esc(m.maximum)}</td><td><button class="textbtn" data-action="edit-mark" data-id="${m.id}" aria-label="Edit ${esc(m.subject)} ${esc(m.assessment)}">Edit</button></td></tr>`).join('');
  main.innerHTML = `<a class="back" href="#directory">← Student directory</a><section class="hero">
    <div class="hero-top"><div><h1>${esc(s.name)}</h1><p class="profile-line"><span>${esc(s.roll)}</span>${badge(s.status)}</p></div>
    <button class="btn accent" data-action="edit-student">Edit student</button></div></section>
    <section class="panel" aria-label="Enrolment details"><dl class="details">
    <div><dt>Department</dt><dd>${esc(departmentName(s.department))} (${esc(s.department)})</dd></div>
    <div><dt>Semester</dt><dd>${s.semester}</dd></div><div><dt>Email</dt><dd>${esc(s.email || 'No email recorded')}</dd></div></dl></section>
    <div class="profile-columns"><section class="panel"><div class="section-head"><h2>Assessment marks</h2>
    <button class="btn secondary" data-action="add-mark"><span aria-hidden="true" class="plus">+</span> Add marks</button></div>
    ${marks.length ? `<table class="marks-table"><thead><tr><th>Subject</th><th>Assessment</th><th>Marks</th><th><span class="sr-only">Edit</span></th></tr></thead><tbody>${markRows}</tbody></table>` :
    '<p class="small-empty">No marks recorded yet.</p>'}</section>
    <section class="panel"><div class="section-head"><h2>Attendance</h2></div>
    ${markedDays ? `<p class="att-summary"><strong>${Math.round(100*presentDays/markedDays)}% attendance</strong> · ${presentDays} present of ${markedDays} marked ${markedDays === 1 ? 'day' : 'days'}</p>
    <ul class="day-list" aria-label="Up to five most recent entries">${history.map(d=>`<li>${dateLabel(d.date)} ${badge(d.state)}</li>`).join('')}</ul>` :
    '<p class="small-empty">No attendance recorded yet.</p>'}</section></div>
    <div class="delete-record"><button class="textbtn danger" data-action="delete-student">Delete student</button></div>`;
}

function openMark(mark) {
  const m = mark || {subject:'',assessment:'',score:'',maximum:100};
  const id = profile.student.id;
  const path = '/students/' + id + '/marks' + (mark ? '/' + m.id : '');
  const fields = `<div class="fields">
    ${formField('Subject','subject',m.subject,'required maxlength="80" placeholder="Advanced Java"',true)}
    ${formField('Assessment','assessment',m.assessment,'required maxlength="60" placeholder="Practical 1"',true)}
    ${formField('Marks earned','score',m.score,'required type="number" min="0" max="99999.99" step="0.01"')}
    ${formField('Maximum marks','maximum',m.maximum,'required type="number" min="0.01" max="99999.99" step="0.01"')}</div>`;
  openForm(mark ? 'Edit marks' : 'Add marks',fields,'Save marks',async values => {
    await api(path,mark ? 'PUT' : 'POST',mark ? {...values,version:m.version} : values);
    inform('Marks saved.'); await renderRoute();
  },{extra:mark ? `<button type="button" class="textbtn danger" data-action="delete-mark" data-id="${m.id}">Delete marks</button>` : ''});
}
function removeStudent() {
  const s = profile.student;
  openForm('Delete student?', `<p>Delete <strong>${esc(s.name)} (${esc(s.roll)})</strong> and their attendance and marks? This is permanent.</p>`, 'Delete student', async () => {
    await api('/students/' + s.id,'DELETE',{version:s.version});
    location.hash = 'directory'; inform('Student deleted.');
  }, {danger:true});
}
function removeMark(id) {
  const m = profile.marks.find(i => i.id === id);
  openForm('Delete marks?', `<p>Delete <strong>${esc(m.subject)} · ${esc(m.assessment)}</strong> from this student’s record?</p>`, 'Delete marks', async () => {
    await api('/students/' + profile.student.id + '/marks/' + m.id,'DELETE',{version:m.version});
    inform('Marks deleted.'); await renderRoute();
  }, {danger:true});
}

function renderAttendance() {
  const f = attendanceFilter;
  main.innerHTML = `<section class="hero"><div class="hero-top"><div><span class="hero-date" id="register-date">${dateLabel(f.date,{weekday:'long',day:'numeric',month:'long',year:'numeric'})}</span>
    <h1>Attendance register</h1></div></div><div id="counts">${metrics([['–','Present'],['–','Absent'],['–','Unmarked']])}</div></section>
    <section class="panel" aria-label="Daily attendance"><div class="toolbar">
    <label class="date-label">Register date<input type="date" id="register-day" value="${esc(f.date)}" max="${esc(session.today)}" required></label>
    <label>Department<select id="dept" aria-label="Department">${departmentOptions(f.department)}</select></label>
    <label>Semester<select id="semester" aria-label="Semester">${semesterOptions(f.semester)}</select></label></div>
    <div id="results"><p class="loading">Loading attendance…</p></div></section>`;
  $('#register-day').onchange = e => {
    if (e.target.validity.valid && e.target.value) {
      f.date = e.target.value; $('#register-date').textContent = dateLabel(f.date,{weekday:'long',day:'numeric',month:'long',year:'numeric'});
      loadAttendance(viewSequence);
    } else { e.target.reportValidity(); e.target.value = f.date; }
  };
  $('#dept').onchange = e => { f.department = e.target.value; loadAttendance(viewSequence); };
  $('#semester').onchange = e => { f.semester = e.target.value; loadAttendance(viewSequence); };
}
async function loadAttendance(seq) {
  if (view !== 'attendance') return;
  const query = ++listSequence;
  const date = attendanceFilter.date;
  // Disable outdated controls as soon as staff switch date or cohort.
  $('#results').querySelectorAll('select').forEach(item => item.disabled = true);
  try {
    const rows = await api('/attendance?' + new URLSearchParams(attendanceFilter));
    if (seq !== viewSequence || query !== listSequence) return;
    const present = rows.filter(r=>r.state === 'Present').length, absent = rows.filter(r=>r.state === 'Absent').length;
    $('#counts').innerHTML = metrics([[present,'Present'],[absent,'Absent'],[rows.length-present-absent,'Unmarked']]);
    if (!rows.length) {
      $('#results').innerHTML = '<div class="empty"><h3>No students in this register</h3><p>Choose another filter or add an active student in the directory.</p><a class="btn secondary" href="#directory">Open directory</a></div>';
      return;
    }
    const lines = rows.map(s => `<tr><td class="person-cell">${person(s)}</td><td class="roll">${esc(s.roll)}</td>
      <td class="dept-cell">${esc(s.department)}<span class="mobile-label"> · Sem ${s.semester}</span></td><td class="semester-cell">Sem ${s.semester}</td>
      <td class="status-cell"><select class="att-control" aria-label="Attendance for ${esc(s.name)}" data-id="${s.id}" data-date="${esc(date)}" data-state="${esc(s.state)}">
      ${options([['','Unmarked'],['Present','Present'],['Absent','Absent']],s.state)}</select></td></tr>`).join('');
    $('#results').innerHTML = `<table class="attendance-table" aria-label="Attendance roster"><thead><tr><th>Student</th><th>Roll number</th><th>Department</th><th>Semester</th><th>Attendance</th></tr></thead><tbody>${lines}</tbody></table>
      <div class="table-footer"><span>${rows.length} ${rows.length === 1 ? 'student' : 'students'}</span><span>Changes save automatically</span></div>`;
  } catch (e) { if (seq === viewSequence && query === listSequence) $('#results').innerHTML = panelError(e); }
}
main.addEventListener('change', async event => {
  const select = event.target.closest('.att-control');
  if (!select) return;
  const seq = viewSequence, {id,date,state} = select.dataset;
  select.disabled = true;
  try {
    await api('/attendance/' + id, 'PUT', {date,previous:state,state:select.value});
    inform('Attendance saved.');
  } catch (e) { inform(e.message); }
  if (view === 'attendance' && seq === viewSequence) await loadAttendance(seq);
});
document.addEventListener('click', async event => {
  const button = event.target.closest('[data-action]');
  if (!button || button.disabled) return;
  const action = button.dataset.action;
  if (action === 'retry') { await start(); return; }
  if (action === 'clear') {
    Object.assign(directoryFilter,{q:'',department:'',semester:'0',page:1});
    await renderRoute(); $('#search')?.focus(); return;
  }
  if (action === 'prev' || action === 'next') {
    directoryFilter.page = Math.max(1, directoryFilter.page + (action === 'next' ? 1 : -1));
    await loadDirectory(viewSequence); return;
  }
  if (action === 'add-student') openStudent(null);
  if (action === 'edit-student') openStudent(profile.student);
  if (action === 'delete-student') removeStudent();
  if (action === 'add-mark') openMark(null);
  if (action === 'edit-mark') openMark(profile.marks.find(m=>m.id === Number(button.dataset.id)));
  if (action === 'delete-mark') removeMark(Number(button.dataset.id));
  if (action === 'demo') {
    button.disabled = true;
    try { await api('/demo','POST'); await renderRoute(); inform('Eight demo students added.'); }
    catch (e) { inform(e.message); button.disabled = false; }
  }
});
start();
