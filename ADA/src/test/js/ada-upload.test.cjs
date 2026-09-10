const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/ada-upload.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

function harness(options) {
    options = options || {};
    const nodes = {}, requests = [], transfers = [], timers = new Map(), rows = []; let timerId = 0;
    function withCommon(attrs) {
        const classes = new Set(), events = {};
        const node = { disabled: false, textContent: '', style: {}, value: '', className: '', events,
            classList: { add(...a) { a.forEach(x => classes.add(x)); }, remove(...a) { a.forEach(x => classes.delete(x)); }, contains(x) { return classes.has(x); } },
            addEventListener(type, handler) { events[type] = handler; },
            getAttribute(k) { return attrs[k]; }, setAttribute(k, v) { attrs[k] = v; },
            fire(type) { return events[type].call(node, { preventDefault() {} }); }
        };
        return node;
    }
    function element(id) { return nodes[id] = withCommon({}); }
    function row(dumpId, status, analysisStatus) {
        const attrs = { 'data-dump-id': dumpId, 'data-status': status, 'data-expired': 'false', 'data-analysis-status': analysisStatus || '' };
        const node = withCommon(attrs);
        node.querySelector = () => ({ checked: false });
        node.children = [{}, { textContent: '' }];
        rows.push(node);
        return node;
    }
    ['adaUploadModal','adaUploadForm','adaUploadSubmitBtn','adaUploadCancelBtn','adaUploadResult','adaListResult',
        'adaProgressWrap','adaProgressBar','adaProgressText','adaDownloadBtn','adaCancelSelectedBtn',
        'adaReuploadBtn','adaUploadOpenBtn','adaUploadCloseBtn','adaAnalyzeBtn',
        'adaAnalysisCancelBtn','adaAnalysisRetryBtn','adaAnalyzeResultBtn'].forEach(element);
    (options.rows || []).forEach(r => row(r.id, r.status, r.analysisStatus));
    const form = nodes.adaUploadForm;
    form.dumpType = {value:'THREAD'}; form.deptNm = {value:'dept'}; form.taskNm = {value:'task'}; form.hostNm = {value:'host'};
    form.dumpFile = { files: [{name:'threads.tar.gz', size:1024}], value:'selected-file' }; form.reset = () => {};
    class Xhr {
        constructor() { this.upload = {}; this.headers = {}; this.aborts = 0; transfers.push(this); }
        open(method, url) { this.method = method; this.url = url; }
        setRequestHeader(k, v) { this.headers[k] = v; }
        send(body) { this.body = body; }
        abort() { this.aborts++; if (this.onabort) this.onabort(); }
    }
    const context = { window: { ADA_CONTEXT_PATH:'/ADA', ADA_CSRF:'csrf', addEventListener() {} },
        document: { getElementById:id => nodes[id], querySelectorAll:sel => sel === '.ada-row' ? rows.slice() : [] },
        URLSearchParams, XMLHttpRequest:Xhr, FormData:class { constructor() { this.items = []; } append(...a) { this.items.push(a); } },
        setTimeout:fn => { timers.set(++timerId, fn); return timerId; }, clearTimeout:id => timers.delete(id),
        fetch:(url, options) => new Promise(resolve => requests.push({url, options, reply(json, ok = true) { resolve({ok, json:() => Promise.resolve(json)}); }}))
    };
    vm.runInNewContext(source, context);
    const result = {nodes, form, requests, transfers, timers, rows,
        location() { return context.window.location === undefined ? null : context.window.location; },
        async start() {
            form.fire('submit'); assert.equal(requests.length, 1);
            requests.shift().reply({success:true, dumpId:'id1', statusCd:'UPLOAD_READY'}); await flush();
        },
        tick() { const first = timers.entries().next().value; assert.ok(first, 'poll timer exists'); timers.delete(first[0]); first[1](); },
        tickCount() { return timers.size; },
        state(statusCd, terminal = false, retryAllowed = false) { return {success:true,dumpId:'id1',statusCd,terminal,retryAllowed,message:statusCd}; }
    };
    return result;
}

test('closing modal keeps server work active and does not pretend to cancel', async () => {
    const h = harness(); await h.start(); h.nodes.adaUploadCloseBtn.fire('click');
    assert.equal(h.transfers[0].aborts, 0); assert.equal(h.requests.length, 0);
    assert.equal(h.nodes.adaUploadSubmitBtn.disabled, true); assert.match(h.nodes.adaListResult.textContent, /취소가 아닙니다/);
});
test('abort and cancel acknowledgement remain pending until status confirms cleanup', async () => {
    const h = harness(); await h.start(); h.nodes.adaUploadCancelBtn.fire('click');
    assert.equal(h.transfers[0].aborts, 0);
    const action = h.requests.shift(); assert.match(action.url, /uploadCancel/); assert.equal(action.options.headers['X-ADA-CSRF'], 'csrf');
    action.reply(h.state('CANCEL_REQUESTED')); await flush();
    assert.equal(h.transfers[0].aborts, 1); assert.equal(h.nodes.adaUploadSubmitBtn.disabled, true);
    h.requests.shift().reply(h.state('CANCEL_REQUESTED')); await flush();
    assert.equal(h.nodes.adaUploadSubmitBtn.disabled, true);
    h.tick(); h.requests.shift().reply(h.state('CANCELLED', true, true)); await flush();
    assert.equal(h.nodes.adaUploadSubmitBtn.disabled, false); assert.equal(h.form.dumpFile.value, '');
    assert.equal(h.nodes.adaUploadResult.textContent, 'CANCELLED');
});
test('lost transport and authentication response never enable retry prematurely', async () => {
    const h = harness(); await h.start(); h.transfers[0].onerror(); h.tick();
    h.requests.shift().reply({success:false,message:'로그인이 필요합니다.'}, false); await flush();
    assert.equal(h.nodes.adaUploadSubmitBtn.disabled, true);
    assert.match(h.nodes.adaUploadResult.textContent, /완료 여부는 확인되지/);
});
test('late cancellation during finalization does not abort or report cancelled', async () => {
    const h = harness(); await h.start(); h.nodes.adaUploadCancelBtn.fire('click');
    h.requests.shift().reply(h.state('FINALIZING')); await flush();
    assert.equal(h.transfers[0].aborts, 0);
    h.requests.shift().reply(h.state('STORED', true)); await flush();
    assert.equal(h.nodes.adaUploadResult.textContent, 'STORED'); assert.equal(h.nodes.adaUploadSubmitBtn.disabled, false);
});
test('older polling response cannot overwrite newer terminal status', async () => {
    const h = harness(); await h.start(); h.tick(); const older = h.requests.shift();
    h.nodes.adaUploadCancelBtn.fire('click'); h.requests.shift().reply(h.state('CANCEL_REQUESTED')); await flush();
    h.requests.shift().reply(h.state('CANCELLED', true, true)); await flush();
    older.reply(h.state('UPLOADING')); await flush();
    assert.equal(h.nodes.adaUploadResult.textContent, 'CANCELLED'); assert.equal(h.nodes.adaUploadSubmitBtn.disabled, false);
});
test('5GB exclusive boundary agrees with the server', () => {
    const h = harness(); h.form.dumpFile.files[0].size = 4999999999; h.form.fire('submit'); assert.equal(h.requests.length, 1);
    const over = harness(); over.form.dumpFile.files[0].size = 5000000000; over.form.fire('submit'); assert.equal(over.requests.length, 0);
});
test('analysis buttons stay disabled until a stored, non-expired row is selected', () => {
    const h = harness({ rows: [{ id: 'd1', status: 'UPLOADING', analysisStatus: '' }] });
    h.rows[0].fire('click');
    assert.equal(h.nodes.adaAnalyzeBtn.disabled, true);
    assert.equal(h.nodes.adaAnalysisCancelBtn.disabled, true);
    assert.equal(h.nodes.adaAnalysisRetryBtn.disabled, true);
    assert.equal(h.nodes.adaAnalyzeResultBtn.disabled, true);
});
test('Analyze click on a stored row posts analyzeRequest.do and starts status polling', async () => {
    const h = harness({ rows: [{ id: 'd1', status: 'STORED', analysisStatus: '' }] });
    h.rows[0].fire('click');
    assert.equal(h.nodes.adaAnalyzeBtn.disabled, false);
    h.nodes.adaAnalyzeBtn.fire('click');
    const req = h.requests.shift();
    assert.match(req.url, /analyzeRequest\.do/);
    req.reply({ success: true, dumpId: 'd1', statusCd: 'QUEUED', message: 'QUEUED', terminal: false });
    await flush();
    assert.equal(h.rows[0].getAttribute('data-analysis-status'), 'QUEUED');
    assert.equal(h.nodes.adaAnalyzeBtn.disabled, true, 'cannot re-request while already queued');
    const poll = h.requests.shift();
    assert.match(poll.url, /analysisStatus\.do/, 'status polling starts right after a successful request');
    poll.reply({ success: true, dumpId: 'd1', statusCd: 'QUEUED', message: 'QUEUED', terminal: false });
    await flush();
    assert.equal(h.tickCount(), 1, 'next poll is scheduled after a non-terminal status read');
});
test('duplicate Analyze click while already queued/running is blocked client-side', async () => {
    const h = harness({ rows: [{ id: 'd1', status: 'STORED', analysisStatus: 'RUNNING' }] });
    h.rows[0].fire('click');
    assert.equal(h.nodes.adaAnalyzeBtn.disabled, true);
    assert.equal(h.nodes.adaAnalysisCancelBtn.disabled, false);
    h.nodes.adaAnalyzeBtn.fire('click');
    assert.equal(h.requests.length, 0);
});
test('analysis cancel posts analysisCancel.do and reflects the returned state on the row', async () => {
    const h = harness({ rows: [{ id: 'd1', status: 'STORED', analysisStatus: 'QUEUED' }] });
    h.rows[0].fire('click');
    h.nodes.adaAnalysisCancelBtn.fire('click');
    const req = h.requests.shift();
    assert.match(req.url, /analysisCancel\.do/);
    req.reply({ success: true, dumpId: 'd1', statusCd: 'CANCELLED', message: 'CANCELLED', terminal: true, retryAllowed: true });
    await flush();
    assert.equal(h.rows[0].getAttribute('data-analysis-status'), 'CANCELLED');
    assert.equal(h.nodes.adaAnalysisRetryBtn.disabled, false);
});
test('analysis result download navigates to the download endpoint only when SUCCEEDED', async () => {
    const notDone = harness({ rows: [{ id: 'd1', status: 'STORED', analysisStatus: 'RUNNING' }] });
    notDone.rows[0].fire('click');
    assert.equal(notDone.nodes.adaAnalyzeResultBtn.disabled, true);
    notDone.nodes.adaAnalyzeResultBtn.fire('click');
    assert.equal(notDone.location(), null, 'must not start a download while the analysis is still running');

    const h = harness({ rows: [{ id: 'd1', status: 'STORED', analysisStatus: 'SUCCEEDED' }] });
    h.rows[0].fire('click');
    assert.equal(h.nodes.adaAnalyzeResultBtn.disabled, false);
    h.nodes.adaAnalyzeResultBtn.fire('click');
    assert.equal(h.requests.length, 0, 'download is a navigation, not an ajax call');
    assert.match(h.location(), /\/dump\/analyzeResultDownload\.do\?dumpId=d1$/);
});
