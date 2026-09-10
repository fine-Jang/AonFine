/* Transport abort never proves server cancellation. Only a fresh status response ends the workflow. */
(function () {
    "use strict";
    var context = window.ADA_CONTEXT_PATH || "";
    var byId = function (id) { return document.getElementById(id); };
    var modal = byId("adaUploadModal"), form = byId("adaUploadForm");
    var submit = byId("adaUploadSubmitBtn"), cancel = byId("adaUploadCancelBtn");
    var result = byId("adaUploadResult"), listResult = byId("adaListResult");
    var progress = byId("adaProgressWrap"), bar = byId("adaProgressBar"), progressText = byId("adaProgressText");
    var activeId = null, xhr = null, busy = false, pollTimer = null, generation = 0, selected = null;
    var cancelPending = false;
    var pollSequence = 0;
    var cancellable = function (s) { return ["UPLOAD_READY", "UPLOADING", "VALIDATING"].indexOf(s) >= 0; };
    var analyzeBtn = byId("adaAnalyzeBtn"), analysisCancelBtn = byId("adaAnalysisCancelBtn");
    var analysisRetryBtn = byId("adaAnalysisRetryBtn"), analysisResultBtn = byId("adaAnalyzeResultBtn");
    var analysisActive = function (s) { return ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].indexOf(s) >= 0; };
    var analysisId = null, analysisGeneration = 0, analysisPollTimer = null, analysisPollSequence = 0;

    function show(box, message, good) {
        box.textContent = message;
        box.classList.remove("success", "error");
        box.classList.add("show", good ? "success" : "error");
    }
    function api(path, data, post) {
        var params = new URLSearchParams(data || {});
        var options = { method: post ? "POST" : "GET", credentials: "same-origin", cache: "no-store",
            headers: { "X-Requested-With": "XMLHttpRequest" } };
        if (post) {
            options.headers["X-ADA-CSRF"] = window.ADA_CSRF;
            options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
            options.body = params.toString();
        }
        return fetch(context + path + (post ? "" : "?" + params.toString()), options).then(function (response) {
            return response.json().then(function (json) {
                if (!response.ok || !json.success) throw new Error(json.message || "서버 상태를 확인하지 못했습니다.");
                return json;
            });
        });
    }
    function rowState(json) {
        document.querySelectorAll(".ada-row").forEach(function (row) {
            if (row.getAttribute("data-dump-id") !== json.dumpId) return;
            row.setAttribute("data-status", json.statusCd);
            row.className = "ada-row status-" + json.statusCd + (row === selected ? " selected" : "");
            row.children[1].textContent = json.message;
        });
        selectionButtons();
    }
    function selectionButtons() {
        var state = selected && selected.getAttribute("data-status");
        var analysisState = selected && selected.getAttribute("data-analysis-status");
        var storedAndCurrent = state === "STORED" && selected.getAttribute("data-expired") !== "true";
        byId("adaDownloadBtn").disabled = !storedAndCurrent;
        byId("adaCancelSelectedBtn").disabled = !cancellable(state);
        byId("adaReuploadBtn").disabled = ["FAILED", "CANCELLED"].indexOf(state) < 0 || busy;
        analyzeBtn.disabled = !storedAndCurrent || analysisActive(analysisState) || analysisState === "RECOVERY_REQUIRED";
        analysisRetryBtn.disabled = !storedAndCurrent || ["FAILED", "CANCELLED"].indexOf(analysisState) < 0;
        analysisCancelBtn.disabled = !storedAndCurrent || !analysisActive(analysisState);
        analysisResultBtn.disabled = !storedAndCurrent || analysisState !== "SUCCEEDED";
    }
    function analysisRowState(id, result) {
        document.querySelectorAll(".ada-row").forEach(function (row) {
            if (row.getAttribute("data-dump-id") !== id) return;
            row.setAttribute("data-analysis-status", result.statusCd || "");
            var cssKey = result.statusCd && result.statusCd !== "IDLE" ? result.statusCd : row.getAttribute("data-status");
            row.className = "ada-row status-" + cssKey + (row === selected ? " selected" : "");
        });
        if (selected && selected.getAttribute("data-dump-id") === id) selectionButtons();
    }
    function scheduleAnalysisPoll(id, gen) {
        if (gen !== analysisGeneration || id !== analysisId) return;
        clearTimeout(analysisPollTimer);
        analysisPollTimer = setTimeout(function () { pollAnalysis(id, gen); }, 2000);
    }
    function pollAnalysis(id, gen) {
        if (gen !== analysisGeneration || id !== analysisId) return;
        var sequence = ++analysisPollSequence;
        api("/dump/analysisStatus.do", { dumpId: id }).then(function (json) {
            if (gen !== analysisGeneration || id !== analysisId || sequence !== analysisPollSequence) return;
            analysisRowState(id, json);
            show(listResult, json.message, json.statusCd === "SUCCEEDED");
            if (!json.terminal) scheduleAnalysisPoll(id, gen);
        }).catch(function (e) {
            if (gen !== analysisGeneration || id !== analysisId || sequence !== analysisPollSequence) return;
            show(listResult, e.message + " 분석 완료 여부는 확인되지 않았습니다. 목록에서 다시 확인해 주세요.", false);
            scheduleAnalysisPoll(id, gen);
        });
    }
    function startAnalysisPoll(id) {
        analysisGeneration++; clearTimeout(analysisPollTimer); analysisId = id;
        pollAnalysis(id, analysisGeneration);
    }
    function analysisAction(path, id) {
        analyzeBtn.disabled = analysisRetryBtn.disabled = analysisCancelBtn.disabled = true;
        api(path, { dumpId: id }, true).then(function (json) {
            analysisRowState(id, json);
            show(listResult, json.message, json.statusCd === "SUCCEEDED");
            startAnalysisPoll(id);
        }).catch(function (e) {
            show(listResult, e.message, false);
            selectionButtons();
        });
    }
    function schedulePoll(id, gen) {
        if (gen !== generation || id !== activeId) return;
        clearTimeout(pollTimer);
        pollTimer = setTimeout(function () { poll(id, gen); }, 1500);
    }
    function poll(id, gen) {
        if (gen !== generation || id !== activeId) return;
        var sequence = ++pollSequence;
        api("/dump/uploadStatus.do", { dumpId: id }).then(function (json) {
            if (gen !== generation || id !== activeId || sequence !== pollSequence) return;
            rowState(json);
            show(result, json.message, json.statusCd === "STORED");
            if (!modal.classList.contains("open")) show(listResult, json.message, json.statusCd === "STORED");
            cancel.disabled = cancelPending || !cancellable(json.statusCd);
            if (json.terminal) {
                busy = false; cancelPending = false; submit.disabled = false; cancel.disabled = true;
                xhr = null; selectionButtons();
                if (json.retryAllowed) form.dumpFile.value = "";
                if (json.statusCd === "STORED") { bar.style.width = "100%"; progressText.textContent = "업로드와 검증 완료"; }
                show(listResult, json.message + " 최신 목록은 업로드 내역을 눌러 확인해 주세요.", json.statusCd === "STORED");
            } else {
                if (json.statusCd === "CLEANUP_FAILED") show(result, json.message + " 재업로드 전에 관리자 확인이 필요합니다.", false);
                schedulePoll(id, gen);
            }
        }).catch(function (e) {
            if (gen !== generation || id !== activeId || sequence !== pollSequence) return;
            show(result, e.message + " 완료 여부는 확인되지 않았습니다. 다시 로그인하거나 목록에서 확인해 주세요.", false);
            schedulePoll(id, gen);
        });
    }
    function openModal() {
        modal.classList.add("open");
        if (busy) return;
        generation++; clearTimeout(pollTimer); activeId = null; cancelPending = false;
        form.reset(); result.classList.remove("show"); progress.classList.remove("show");
        submit.disabled = false; cancel.disabled = true;
    }
    byId("adaUploadOpenBtn").addEventListener("click", function (e) { e.preventDefault(); openModal(); });
    byId("adaUploadCloseBtn").addEventListener("click", function () {
        modal.classList.remove("open");
        if (busy) show(listResult, "업로드 처리가 진행 중입니다. 팝업 닫기는 취소가 아닙니다. 덤프 업로드를 눌러 상태 확인·취소할 수 있습니다.", false);
    });
    function requestCancel(id, ownTransfer) {
        if (!id) return;
        var gen = generation;
        cancelPending = true; cancel.disabled = true;
        show(result, "서버에 취소를 요청하고 있습니다. 아직 취소 완료가 아닙니다.", false);
        api("/dump/uploadCancel.do", { dumpId: id }, true).then(function (json) {
            if (gen !== generation || id !== activeId) return;
            // Abort reduces network traffic only after the server accepted cancellation.
            if (ownTransfer && xhr && ["CANCEL_REQUESTED", "CLEANUP_PENDING", "CANCELLED"].indexOf(json.statusCd) >= 0) xhr.abort();
            cancelPending = false;
            // Ignore action snapshots: a status GET may already have observed a newer terminal state.
            poll(id, gen);
        }).catch(function (e) {
            if (gen !== generation || id !== activeId) return;
            cancelPending = false; cancel.disabled = false;
            show(result, e.message + " 취소 접수 여부를 다시 확인합니다.", false);
            poll(id, gen);
        });
    }
    cancel.addEventListener("click", function () { requestCancel(activeId, true); });
    form.addEventListener("submit", function (event) {
        event.preventDefault(); if (busy) return;
        var file = form.dumpFile.files && form.dumpFile.files[0];
        if (!file || !/\.tar\.gz$/i.test(file.name) || file.size <= 0 || file.size >= 5000000000) {
            show(result, "비어 있지 않은 5GB 미만 tar.gz 파일을 선택해 주세요.", false); return;
        }
        busy = true; submit.disabled = true; cancel.disabled = true; cancelPending = false;
        generation++; var gen = generation;
        progress.classList.add("show"); bar.style.width = "0%"; progressText.textContent = "업로드 준비 중";
        api("/dump/uploadPrepare.do", { dumpType: form.dumpType.value, deptNm: form.deptNm.value.trim(),
            taskNm: form.taskNm.value.trim(), hostNm: form.hostNm.value.trim(), fileName: file.name, fileSize: file.size }, true)
            .then(function (json) {
                if (gen !== generation) return;
                activeId = json.dumpId; cancel.disabled = false;
                var body = new FormData(); body.append("dumpFile", file, file.name);
                xhr = new XMLHttpRequest();
                xhr.open("POST", context + "/dump/upload.do", true);
                xhr.setRequestHeader("X-ADA-CSRF", window.ADA_CSRF);
                xhr.setRequestHeader("X-ADA-Dump-ID", activeId);
                xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
                xhr.upload.onprogress = function (e) {
                    if (gen !== generation || !busy || cancelPending) return;
                    if (e.lengthComputable) {
                        var pct = Math.min(99, Math.round(e.loaded / e.total * 100));
                        bar.style.width = pct + "%";
                        progressText.textContent = "전송 " + pct + "% · " + Math.round(e.loaded / 1048576) + " / " + Math.round(e.total / 1048576) + " MB";
                    }
                };
                xhr.upload.onload = function () {
                    if (gen === generation && busy) progressText.textContent = "전송 종료 · 서버 검증/저장 상태 확인 중";
                };
                xhr.onload = xhr.onerror = xhr.onabort = function () { schedulePoll(activeId, gen); };
                xhr.send(body); schedulePoll(activeId, gen);
            }).catch(function (e) {
                if (gen !== generation) return;
                busy = false; submit.disabled = false;
                show(result, e.message + " 목록에 업로드 대기 건이 있으면 해당 건을 취소한 뒤 재업로드해 주세요.", false);
            });
    });
    document.querySelectorAll(".ada-row").forEach(function (row) {
        row.addEventListener("click", function () {
            document.querySelectorAll(".ada-row").forEach(function (r) { r.classList.remove("selected"); });
            selected = row; row.classList.add("selected"); row.querySelector("input[type=radio]").checked = true;
            selectionButtons();
        });
    });
    byId("adaCancelSelectedBtn").addEventListener("click", function () {
        if (!selected || this.disabled) return;
        var id = selected.getAttribute("data-dump-id");
        if (busy && id !== activeId) { show(listResult, "현재 업로드 처리를 확인한 뒤 다른 항목을 취소해 주세요.", false); return; }
        if (id !== activeId) { generation++; clearTimeout(pollTimer); activeId = id; xhr = null; }
        busy = true; submit.disabled = true; modal.classList.add("open"); requestCancel(id, !!xhr);
    });
    byId("adaReuploadBtn").addEventListener("click", function () { if (!this.disabled) openModal(); });
    byId("adaDownloadBtn").addEventListener("click", function () {
        if (selected && !this.disabled) window.location = context + "/dump/download.do?dumpId=" + encodeURIComponent(selected.getAttribute("data-dump-id"));
    });
    analyzeBtn.addEventListener("click", function () {
        if (this.disabled || !selected) return;
        analysisAction("/dump/analyzeRequest.do", selected.getAttribute("data-dump-id"));
    });
    analysisRetryBtn.addEventListener("click", function () {
        if (this.disabled || !selected) return;
        analysisAction("/dump/analysisRetry.do", selected.getAttribute("data-dump-id"));
    });
    analysisCancelBtn.addEventListener("click", function () {
        if (this.disabled || !selected) return;
        analysisAction("/dump/analysisCancel.do", selected.getAttribute("data-dump-id"));
    });
    analysisResultBtn.addEventListener("click", function () {
        if (this.disabled || !selected) return;
        window.location = context + "/dump/analyzeResultDownload.do?dumpId="
            + encodeURIComponent(selected.getAttribute("data-dump-id"));
    });
    window.addEventListener("beforeunload", function (e) { if (busy) { e.preventDefault(); e.returnValue = ""; } });
})();
