// ===== CctvFix：央视频电视页注入脚本 =====
// 原则：不依赖任何 class / id 名，全部按“频道原名 + 几何位置/大小”判断。
(function () {
  if (window.__CctvFixLoaded) return;
  window.__CctvFixLoaded = true;

  var VW = function () { return window.innerWidth || document.documentElement.clientWidth || 1280; };
  var VH = function () { return window.innerHeight || document.documentElement.clientHeight || 720; };
  var CHANNELS = [];
  var state = { playerReady: false, lastTime: -1, checkCount: 0, sentFor: '' };

  // ---------- 工具 ----------
  function elRect(e) {
    try {
      var r = e.getBoundingClientRect();
      return { left: r.left, top: r.top, right: r.right, bottom: r.bottom, w: r.width, h: r.height };
    } catch (err) { return null; }
  }

  function isFixed(e) {
    try { var p = getComputedStyle(e); return p.position === 'fixed' || p.position === 'sticky'; }
    catch (err) { return false; }
  }

  function visibleArea(rect) {
    if (!rect) return 0;
    return rect.w * rect.h;
  }

  // ---------- 播放器识别 ----------
  function candidateNodes() {
    var list = [];
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) list.push(vids[i]);
    var ifrs = document.querySelectorAll('iframe');
    for (var j = 0; j < ifrs.length; j++) list.push(ifrs[j]);
    return list;
  }

  function findPlayer() {
    var best = null, bestArea = 0;
    var nodes = candidateNodes();
    for (var i = 0; i < nodes.length; i++) {
      var r = elRect(nodes[i]);
      if (!r) continue;
      var a = visibleArea(r);
      // 只认占据屏幕较大部分的播控元素，偏小的一律忽略（常是广告/二维码）
      if (a > bestArea && r.w > VW() * 0.4 && r.h > VH() * 0.3) {
        bestArea = a;
        best = nodes[i];
      }
    }
    return best;
  }

  function pinPlayer() {
    var p = findPlayer();
    if (!p) return false;
    try {
      p.style.position = 'fixed';
      p.style.top = '0px';
      p.style.left = '0px';
      p.style.width = '100vw';
      p.style.height = '100vh';
      p.style.zIndex = '2147483646';
      p.style.objectFit = 'contain';
      p.style.background = '#000';
    } catch (e) { return false; }
    return true;
  }

  // ---------- 浮层清理（按位置与大小） ----------
  var HIDDEN = [];
  function hideNode(e) {
    if (e == null || e.nodeType !== 1) return;
    try {
      if (HIDDEN.indexOf(e) >= 0) return;
      e.style.setProperty('display', 'none', 'important');
      HIDDEN.push(e);
    } catch (err) {}
  }

  function cleanup() {
    try { pinPlayer(); } catch (e) {}
    var all = document.querySelectorAll('body *');
    var vw = VW(), vh = VH();
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (!isFixed(el)) continue;
      // 播放器本身就是 fixed，且覆盖全屏，保留
      if (el.tagName === 'VIDEO' || el.tagName === 'IFRAME' || el.tagName === 'CANVAS') continue;
      if (el.style && el.style.zIndex === '2147483646') continue;
      var r = elRect(el);
      if (!r) continue;
      var area = visibleArea(r);
      var isBig = r.w > vw * 0.9 && r.h > vh * 0.9;
      var isFull = r.w >= vw - 2 && r.h >= vh - 2;
      // 全屏遮罩层保留，其余按几何规则隐藏
      if (isBig || isFull) continue;
      var isLeftSidebar = r.w < vw * 0.4 && r.h > vh * 0.5 && r.left < vw * 0.05;
      var isRightSidebar = r.w < vw * 0.4 && r.h > vh * 0.5 && r.left > vw * 0.6;
      var isSmallPopup = area < vw * vh * 0.35;
      var isBanner = r.h < vh * 0.14 && (r.top < vh * 0.1 || r.top > vh * 0.85);
      var isPlayerBar = r.h < vh * 0.2 && r.w < vw * 0.5 && area < vw * vh * 0.2;
      if (isSmallPopup || isRightSidebar || isBanner) hideNode(el);
      else if (isLeftSidebar && !isPlayerBar) hideNode(el);
    }
  }

  // ---------- 频道枚举 ----------
  function elText(el) {
    try { return (el.textContent || '').replace(/\s+/g, ' ').trim(); }
    catch (e) { return ''; }
  }

  function norm(s) {
    return (s || '').toLowerCase().replace(/[\s\-_·:：|｜,，。.()（）]+/g, '');
  }

  // 每个频道生成候选别名：全名 / CCTV 编号前缀 / 分类词
  function aliases(ch) {
    var list = [];
    list.push({ t: norm(ch.name), loose: false });
    var m = (ch.key || '').toLowerCase().match(/cctv(\d{1,2})(plus)?/)
            || norm(ch.name).match(/cctv\s*-?\s*(\d{1,2})(\+)?/);
    if (m) {
      list.push({ t: 'cctv' + m[1] + (m[2] ? '+' : ''), loose: true });
    }
    var parts = (ch.name || '').split(' ');
    if (parts.length > 1 && parts[parts.length - 1]) {
      list.push({ t: norm(parts[parts.length - 1]), loose: false });
    }
    return list;
  }

  function entryMatch(txt, ch) {
    var t = norm(txt);
    var list = aliases(ch);
    for (var i = 0; i < list.length; i++) {
      var a = list[i].t;
      if (!a) continue;
      if (t === a) return true;
      if (t.indexOf(a) === 0) {
        if (list[i].loose) return true;
        var rest = t.substring(a.length);
        if (rest && !/^[a-z0-9\u4e00-\u9fa5]/.test(rest.charAt(0))) return true;
      }
    }
    return false;
  }

  function findChannelEntries() {
    var out = {};
    var all = document.querySelectorAll('body *');
    for (var i = all.length - 1; i >= 0; i--) {
      var el = all[i];
      var txt = elText(el);
      if (!txt) continue;
      for (var c = 0; c < CHANNELS.length; c++) {
        var ch = CHANNELS[c];
        if (!entryMatch(txt, ch)) continue;
        var nm = ch.name;
        if (!out[nm]) {
          out[nm] = { el: el, name: nm, program: txt.replace(nm, '').trim() };
        } else if ((elRect(out[nm].el) || { h: 1e9 }).h > (elRect(el) || { h: 0 }).h) {
          out[nm] = { el: el, name: nm, program: txt.replace(nm, '').trim() };
        }
      }
    }
    return out;
  }

  function reportChannels() {
    var entries = findChannelEntries();
    var arr = [];
    for (var c = 0; c < CHANNELS.length; c++) {
      var name = CHANNELS[c].name;
      var e = entries[name];
      arr.push({ name: name, program: e ? e.program : '' });
    }
    try { window.CctvBridge && window.CctvBridge.onChannels(JSON.stringify(arr)); } catch (err) {}
  }

  // ---------- 当前节目 ----------
  function nextSiblingText(el) {
    try {
      var p = el.parentNode, acc = '';
      var kids = p ? p.children : null;
      for (var i = 0; kids && i < kids.length; i++) {
        if (kids[i] === el) continue;
        var t = elText(kids[i]);
        if (t && t !== elText(el)) acc += ' ' + t;
      }
      return acc.trim();
    } catch (e) { return ''; }
  }

  function currentProgram() {
    try {
      var cur = window.__currentChannelName || '';
      if (!cur) return '';
      var entries = findChannelEntries();
      var e = entries[cur];
      if (e && e.program) return e.program;
      if (e && e.el) return nextSiblingText(e.el);
      return '';
    } catch (err) { return ''; }
  }

  function reportProgram() {
    var p = currentProgram();
    try { window.CctvBridge && window.CctvBridge.onProgram(window.__currentChannelName || '', p); } catch (err) {}
  }

  // ---------- 播放状态 ----------
  function captureState() {
    try {
      var vids = document.querySelectorAll('video');
      var any = false;
      state.checkCount++;
      for (var i = 0; i < vids.length; i++) {
        any = true;
        var v = vids[i];
        var t = v.currentTime || 0;
        if (v.readyState >= 2 && (state.lastTime < 0 || Math.abs(t - state.lastTime) > 0.05)) {
          state.lastTime = t;
          state.playerReady = true;
          return 'playing';
        }
      }
      if (!any) { state.playerReady = false; return 'loading'; }
      if (state.playerReady && state.checkCount > 4) {
        state.checkCount = 0;
        state.playerReady = false;
        return 'stall';
      }
      return state.playerReady ? 'playing' : 'loading';
    } catch (e) { return 'loading'; }
  }

  function reportState() {
    var s = captureState();
    var name = window.__currentChannelName || '';
    var res = '';
    if (s === 'playing') {
      try {
        var vids = document.querySelectorAll('video');
        for (var i = 0; i < vids.length; i++) {
          var v = vids[i];
          if (v.videoWidth > 0 && v.videoHeight > 0) {
            if (state.sentFor !== name) {
              state.sentFor = name;
              res = v.videoWidth + 'x' + v.videoHeight;
            }
            break;
          }
        }
      } catch (e) {}
    }
    try { window.CctvBridge && window.CctvBridge.onPlayerState(s + ':' + name + ':' + res); } catch (err) {}
    return s;
  }

  // ---------- 对外接口 ----------
  window.CctvFix = {
    init: function (channelsJson) {
      try {
        var raw = JSON.parse(channelsJson || '[]');
        CHANNELS = [];
        for (var i = 0; i < raw.length; i++) {
          CHANNELS.push({ name: raw[i].name || '', key: raw[i].key || '' });
        }
      } catch (e) { CHANNELS = []; }
      cleanup();
      try { window.CctvBridge && window.CctvBridge.onChannels('ping'); } catch (e) {}
    },
    cleanup: function () { cleanup(); },
    listChannels: function () { reportChannels(); },
    program: function () { reportProgram(); },
    state: function () { return reportState(); },
    playChannel: function (name) {
      window.__currentChannelName = name;
      state.sentFor = '';
      var entries = findChannelEntries();
      var e = entries[name];
      if (!e || !e.el) { reportState(); return false; }
      var r = elRect(e.el);
      try { e.el.click(); } catch (err) {}
      try {
        window.CctvBridge && window.CctvBridge.requestTap(r.left + r.w / 2, r.top + r.h / 2);
      } catch (err) {}
      reportState();
      return true;
    }
  };

  // ---------- 周期任务 ----------
  window.setInterval(cleanup, 1500);
  window.setInterval(reportState, 1500);
  try {
    window.setInterval(reportChannels, 30000);
    window.setInterval(reportProgram, 5000);
  } catch (e) {}
})();