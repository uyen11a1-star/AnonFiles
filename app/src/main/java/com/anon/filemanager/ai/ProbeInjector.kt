package com.anon.filemanager.ai

object ProbeInjector {

    // Script inject sau khi load xong — đếm ảnh lỗi + bắt JS error + đo chiều cao
    val SCRIPT = """
        (function() {
            try {
                var imgs = document.querySelectorAll('img');
                var failedSrcs = [];
                var failedCount = 0;
                for (var i = 0; i < imgs.length; i++) {
                    var img = imgs[i];
                    if (!img.complete || img.naturalWidth === 0) {
                        failedCount++;
                        var s = img.getAttribute('src') || '(no-src)';
                        if (failedSrcs.length < 6) failedSrcs.push(s);
                    }
                }
                var jsErrs = window.__anonJSErrors || [];
                var bodyH = document.body ? document.body.scrollHeight : 0;
                var isEmpty = bodyH < 20 && (!document.body || (document.body.innerText || '').trim().length < 5);
                var payload = {
                    imagesTotal: imgs.length,
                    imagesFailed: failedCount,
                    failedSources: failedSrcs,
                    jsErrors: jsErrs,
                    bodyHeight: bodyH,
                    isEmpty: isEmpty
                };
                if (window.AnonProbe && window.AnonProbe.report) {
                    window.AnonProbe.report(JSON.stringify(payload));
                }
            } catch (e) { }
        })();
    """.trimIndent()

    // Script chặn và gom JS error ngay từ đầu — inject trước khi load page
    val ERROR_HOOK = """
        (function(){
            window.__anonJSErrors = [];
            window.addEventListener('error', function(e) {
                try {
                    var msg = (e.message || 'error') + ' @ ' + (e.filename || '?') + ':' + (e.lineno || 0);
                    window.__anonJSErrors.push(msg);
                } catch(_) {}
            }, true);
            window.addEventListener('unhandledrejection', function(e) {
                try { window.__anonJSErrors.push('Promise: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason))); } catch(_) {}
            });
        })();
    """.trimIndent()
}
