package dev.shugyo.scan

/**
 * Runs inside every frame of a page in the in-app browser. On request it reads the page into
 * blocks: headings, paragraphs (bold key terms marked **like this**), list items, tables and
 * pictures. Textbook readers usually keep the book itself in an iframe, so this goes into all frames
 * and answers through the ShugyoBridge object.
 */
object ScanScript {
    val JS = """
(function () {
  if (window.__shugyoReady) return;
  window.__shugyoReady = true;

  var SKIP_TAGS = {SCRIPT:1, STYLE:1, NOSCRIPT:1, TEMPLATE:1, NAV:1, FOOTER:1, BUTTON:1, SELECT:1, OPTION:1,
    TEXTAREA:1, INPUT:1, IFRAME:1, VIDEO:1, AUDIO:1, svg:1, SVG:1, HEAD:1, LINK:1, META:1, FORM:1, OBJECT:1};
  var SKIP_ROLES = {navigation:1, menu:1, menubar:1, toolbar:1, banner:1, contentinfo:1, search:1, tablist:1};
  var BLOCK_TAGS = {P:1, DIV:1, SECTION:1, ARTICLE:1, MAIN:1, ASIDE:1, HEADER:1, UL:1, OL:1, LI:1, DL:1, DT:1,
    DD:1, BLOCKQUOTE:1, FIGURE:1, FIGCAPTION:1, TABLE:1, H1:1, H2:1, H3:1, H4:1, H5:1, H6:1, IMG:1, CANVAS:1,
    PRE:1, HR:1, BODY:1, CENTER:1, DETAILS:1, SUMMARY:1, PICTURE:1};
  var MAX_TEXT = 350000, MAX_IMG = 30;
  var out, imgs, textLen, imgCount, seenImg;

  function clean(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
  function cls(el) { var c = el.className; return typeof c === 'string' ? c : ((c && c.baseVal) || ''); }
  function hidden(el) {
    if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') return true;
    var st = window.getComputedStyle(el);
    return !st || st.display === 'none' || st.visibility === 'hidden';
  }
  function skip(el) {
    if (SKIP_TAGS[el.tagName]) return true;
    var role = el.getAttribute && el.getAttribute('role');
    if (role && SKIP_ROLES[role]) return true;
    return hidden(el);
  }
  function isKey(el) {
    var t = el.tagName;
    if (t === 'B' || t === 'STRONG' || t === 'DFN') return true;
    return /key-?term|glossterm|gloss-term|keyword|boldterm|term-def/i.test(cls(el));
  }
  function inline(el) {
    var s = '';
    var kids = el.childNodes;
    for (var i = 0; i < kids.length; i++) {
      var n = kids[i];
      if (n.nodeType === 3) { s += n.nodeValue; continue; }
      if (n.nodeType !== 1) continue;
      if (n.tagName === 'BR') { s += ' '; continue; }
      if (n.tagName === 'IMG' || skip(n)) continue;
      var t = inline(n);
      if (isKey(n)) {
        var c = clean(t);
        if (c.length > 1 && c.length < 90) { s += ' **' + c + '** '; continue; }
      }
      s += t;
    }
    return s;
  }
  function hasBlock(el) {
    var k = el.children;
    if (!k) return false;
    for (var i = 0; i < k.length; i++) if (BLOCK_TAGS[k[i].tagName] || k[i].shadowRoot) return true;
    return false;
  }
  function push(kind, text, extra) {
    var t = clean(text);
    if (!t) return;
    textLen += t.length;
    var b = {k: kind, t: t};
    if (extra) for (var key in extra) b[key] = extra[key];
    out.push(b);
  }
  function caption(el) {
    var fig = el.closest ? el.closest('figure') : null;
    var cap = '';
    if (fig) { var fc = fig.querySelector('figcaption'); if (fc) cap = clean(inline(fc)); }
    if (!cap && el.getAttribute) cap = clean(el.getAttribute('alt') || el.getAttribute('aria-label') || el.getAttribute('title') || '');
    var p = el.parentElement, guard = 0;
    while (p && guard++ < 3 && cap.length < 12) {
      var sib = p.nextElementSibling;
      if (sib) {
        var s = clean(sib.innerText || '');
        if (/^(fig(ure)?|table|photo|diagram)\s*\d/i.test(s)) cap = s.slice(0, 500);
      }
      p = p.parentElement;
    }
    return cap;
  }
  function encode(el, w, h) {
    try {
      var max = 1280, scale = Math.min(1, max / Math.max(w, h));
      var c = document.createElement('canvas');
      c.width = Math.max(1, Math.round(w * scale));
      c.height = Math.max(1, Math.round(h * scale));
      var g = c.getContext('2d');
      g.fillStyle = '#ffffff';
      g.fillRect(0, 0, c.width, c.height);
      g.drawImage(el, 0, 0, c.width, c.height);
      return c.toDataURL('image/jpeg', 0.85);
    } catch (e) { return ''; }
  }
  function image(el) {
    if (imgCount >= MAX_IMG) return;
    var isCanvas = el.tagName === 'CANVAS';
    var w = isCanvas ? el.width : (el.naturalWidth || el.width || 0);
    var h = isCanvas ? el.height : (el.naturalHeight || el.height || 0);
    var src = isCanvas ? '' : (el.currentSrc || el.src || el.getAttribute('data-src') || '');
    if (w && h && (w < 140 || h < 100)) return;
    if (isCanvas && (w < 300 || h < 200)) return;
    if (!isCanvas && !src) return;
    if (src && seenImg[src]) return;
    if (src) seenImg[src] = 1;
    var data = '';
    if (isCanvas || (el.complete && w && h)) data = encode(el, w, h);
    if (isCanvas && !data) return;
    imgCount++;
    var id = 'i' + imgCount;
    var cap = caption(el);
    imgs.push({id: id, src: data ? '' : src, data: data, cap: cap, w: w, h: h});
    out.push({k: 'img', id: id, t: cap});
  }
  function table(el) {
    var rows = [];
    for (var r = 0; r < el.rows.length && rows.length < 80; r++) {
      var cells = [];
      var cs = el.rows[r].cells;
      for (var c = 0; c < cs.length; c++) cells.push(clean(inline(cs[c])));
      if (cells.join('').length) rows.push(cells);
    }
    if (!rows.length) return;
    var cap = el.caption ? clean(inline(el.caption)) : '';
    if (!cap) {
      var prev = el.previousElementSibling;
      if (prev) { var s = clean(prev.innerText || ''); if (/^table\s*\d/i.test(s)) cap = s.slice(0, 300); }
    }
    textLen += JSON.stringify(rows).length;
    out.push({k: 'table', t: cap, rows: rows});
  }
  function walk(el) {
    if (textLen > MAX_TEXT) return;
    var tag = el.tagName;
    if (skip(el)) return;
    if (/^H[1-6]$/.test(tag)) { push('h', inline(el), {l: +tag.charAt(1)}); return; }
    if (tag === 'TABLE') { table(el); return; }
    if (tag === 'IMG' || tag === 'CANVAS') { image(el); return; }
    if (tag === 'DT') { var d = clean(inline(el)); if (d) push('p', '**' + d + '**'); return; }
    if (tag === 'LI' && !hasBlock(el)) { push('li', inline(el)); return; }
    if (el.shadowRoot) walkChildren(el.shadowRoot);
    if (!hasBlock(el) && tag !== 'BODY') { push('p', inline(el)); return; }
    walkChildren(el);
  }
  function walkChildren(parent) {
    var kids = parent.childNodes;
    var buf = '';
    for (var i = 0; i < kids.length; i++) {
      var n = kids[i];
      if (n.nodeType === 3) { buf += n.nodeValue; continue; }
      if (n.nodeType !== 1) continue;
      if (!BLOCK_TAGS[n.tagName] && !n.shadowRoot && !hasBlock(n)) {
        if (n.tagName === 'BR') { buf += ' '; continue; }
        if (!skip(n)) {
          var t = inline(n);
          if (isKey(n)) { var c = clean(t); if (c.length > 1 && c.length < 90) t = ' **' + c + '** '; }
          buf += t;
        }
        continue;
      }
      if (buf.trim()) push('p', buf);
      buf = '';
      walk(n);
    }
    if (buf.trim()) push('p', buf);
  }
  function textOf(el) { try { return (el.innerText || '').length; } catch (e) { return 0; } }
  function pickRoot() {
    var body = document.body;
    if (!body) return document.documentElement;
    var all = textOf(body), best = null, bestLen = 0;
    var cands = document.querySelectorAll('main, [role=main], article');
    for (var i = 0; i < cands.length; i++) {
      var n = textOf(cands[i]);
      if (n > bestLen) { best = cands[i]; bestLen = n; }
    }
    return (best && bestLen > all * 0.5) ? best : body;
  }

  window.__shugyoScan = function () {
    out = []; imgs = []; textLen = 0; imgCount = 0; seenImg = {};
    walk(pickRoot());
    var words = 0;
    for (var i = 0; i < out.length; i++) if (out[i].k !== 'img') words += out[i].t.split(' ').length;
    return {url: location.href, title: document.title, top: window === window.top,
            blocks: out, images: imgs, words: words};
  };

  window.__shugyoScroll = function () {
    var best = null, bestH = 0;
    var se = document.scrollingElement || document.documentElement;
    if (se && se.scrollHeight > se.clientHeight + 40) { best = se; bestH = window.innerHeight || se.clientHeight; }
    var all = document.querySelectorAll('div, main, section, article');
    for (var i = 0; i < all.length && i < 6000; i++) {
      var e = all[i];
      if (e.clientHeight > bestH && e.scrollHeight > e.clientHeight + 40) {
        var oy = window.getComputedStyle(e).overflowY;
        if (oy === 'auto' || oy === 'scroll' || oy === 'overlay') { best = e; bestH = e.clientHeight; }
      }
    }
    if (!best) return false;
    var step = Math.max(200, Math.round(bestH * 0.85));
    if (best === se) {
      var y = window.scrollY;
      window.scrollBy(0, step);
      return window.scrollY > y + 5;
    }
    var before = best.scrollTop;
    best.scrollTop = before + step;
    return best.scrollTop > before + 5;
  };

  function hook() {
    var b = window.ShugyoBridge;
    if (!b) return false;
    if (window.__shugyoHooked) return true;
    window.__shugyoHooked = true;
    b.onmessage = function (e) {
      var cmd = e.data, reply;
      try {
        if (cmd === 'scan') reply = {kind: 'scan', data: window.__shugyoScan()};
        else if (cmd === 'scroll') reply = {kind: 'scroll', moved: window.__shugyoScroll()};
        else return;
      } catch (err) { reply = {kind: cmd, error: String(err)}; }
      b.postMessage(JSON.stringify(reply));
    };
    b.postMessage(JSON.stringify({kind: 'hello', top: window === window.top}));
    return true;
  }
  if (!hook()) document.addEventListener('DOMContentLoaded', hook);
})();
"""
}
