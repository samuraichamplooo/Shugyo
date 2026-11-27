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
  var MAX_TEXT = 350000, MAX_IMG = 30, MAX_GLOSS = 60;
  var GLOSS_RE = /gloss|key-?term|keyword|defin|vocab|term-?link|termref|popover|tooltip/i;
  var out, imgs, textLen, imgCount, seenImg, gloss, glossEls, glossSeen;

  function clean(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
  function cls(el) { var c = el.className; return typeof c === 'string' ? c : ((c && c.baseVal) || ''); }
  function hidden(el) {
    if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') return true;
    var st = window.getComputedStyle(el);
    return !st || st.display === 'none' || st.visibility === 'hidden';
  }
  function skip(el) {
    // A linked key term can be a button; everything else that's a button is page chrome.
    if (el.tagName === 'BUTTON' && isGloss(el)) return hidden(el);
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
  // A linked key term whose definition pops up when tapped.
  function isGloss(el) {
    var tag = el.tagName, role = el.getAttribute('role');
    var clickable = tag === 'A' || tag === 'BUTTON' || role === 'button' || role === 'link' ||
      el.hasAttribute('tabindex') || el.hasAttribute('onclick');
    if (!clickable) return false;
    var txt = clean(el.textContent);
    if (txt.length < 2 || txt.length > 60 || txt.split(' ').length > 6) return false;
    if (/^(fig(ure)?|table|section|chapter|page|see)\b/i.test(txt)) return false;
    var attrs = cls(el) + ' ' + (el.id || '') + ' ' + ['href', 'data-type', 'data-term', 'data-glossary',
      'aria-describedby', 'aria-controls', 'epub:type', 'data-toggle', 'data-bs-toggle'].map(function (a) {
      return el.getAttribute(a) || '';
    }).join(' ');
    if (GLOSS_RE.test(attrs) || el.hasAttribute('data-term') || el.hasAttribute('data-glossary') ||
      el.hasAttribute('aria-haspopup')) return true;
    var fw = parseInt(window.getComputedStyle(el).fontWeight, 10) || 400;
    var p = el.parentElement;
    return fw >= 600 && !!p && clean(p.textContent).length > txt.length + 40;
  }
  // A definition the page already holds: in an attribute, or in an element the term points to.
  function staticDef(el, term) {
    var c = [el.getAttribute('title'), el.getAttribute('data-definition'), el.getAttribute('data-content'),
      el.getAttribute('data-original-title'), el.getAttribute('data-tooltip'), el.getAttribute('data-tippy-content'),
      el.getAttribute('aria-description'), el.getAttribute('aria-label')];
    var ids = ((el.getAttribute('aria-describedby') || '') + ' ' + (el.getAttribute('aria-controls') || '')).split(/\s+/);
    var href = el.getAttribute('href') || '';
    if (href.charAt(0) === '#' && href.length > 1 && href.charAt(1) !== '/') ids.push(href.slice(1));
    for (var i = 0; i < ids.length; i++) {
      if (!ids[i]) continue;
      var d = null;
      try { d = document.getElementById(ids[i]); } catch (e) {}
      if (d && !d.contains(el)) c.push(d.textContent);
    }
    for (var j = 0; j < c.length; j++) {
      var v = clean(c[j]);
      if (v && v.length > term.length + 8 && !/opens? in|new window|click|tap to/i.test(v)) return v.slice(0, 900);
    }
    return '';
  }
  function noteGloss(el) {
    var term = clean(el.textContent);
    var key = term.toLowerCase();
    if (glossSeen[key] || gloss.length >= MAX_GLOSS) return;
    glossSeen[key] = 1;
    var def = staticDef(el, term);
    gloss.push({t: term, d: def});
    if (!def) glossEls.push({el: el, t: term});
  }
  function mark(n, t) {
    var c = clean(t);
    if (isGloss(n)) {
      noteGloss(n);
      if (c.length > 1) return ' **' + c + '** ';
    }
    if (isKey(n) && c.length > 1 && c.length < 90) return ' **' + c + '** ';
    return t;
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
      s += mark(n, inline(n));
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
        if (!skip(n)) buf += mark(n, inline(n));
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
    gloss = []; glossEls = []; glossSeen = {};
    walk(pickRoot());
    window.__shugyoGlossEls = glossEls;
    var words = 0;
    for (var i = 0; i < out.length; i++) if (out[i].k !== 'img') words += out[i].t.split(' ').length;
    return {url: location.href, title: document.title, top: window === window.top,
            blocks: out, images: imgs, words: words, gloss: gloss, glossPending: glossEls.length};
  };

  // ---- Pop-up definitions: tap each linked term, read what pops up, close it.
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  var POPUP = '[role=dialog],[role=tooltip],[role=alertdialog],[aria-modal=true],.popover,.tooltip,.modal,.tippy-box,' +
    '[class*=popover],[class*=Popover],[class*=tooltip],[class*=Tooltip],[class*=glossary],[class*=Glossary],' +
    '[class*=definition],[class*=Definition],[class*=flyout],[class*=Flyout],[class*=popup],[class*=Popup],[class*=modal],[class*=Modal]';
  function shown(el) {
    if (!el || !el.isConnected) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var s = window.getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none' && parseFloat(s.opacity || '1') > 0.05;
  }
  function textIn(el) { return clean(el.innerText || el.textContent || ''); }
  function popupsShown() {
    var list = [], all = document.querySelectorAll(POPUP);
    for (var i = 0; i < all.length; i++) if (shown(all[i])) list.push(all[i]);
    return list;
  }
  function pick(before, added, trigger, term) {
    var cands = popupsShown().filter(function (p) { return before.indexOf(p) < 0; });
    for (var i = 0; i < added.length; i++) {
      var a = added[i];
      if (a.nodeType !== 1) continue;
      cands.push(a);
      var up = a.closest ? a.closest(POPUP) : null;
      if (up) cands.push(up);
    }
    var best = null, bestScore = -1, low = term.toLowerCase();
    for (var j = 0; j < cands.length; j++) {
      var c = cands[j];
      if (c === trigger || c.contains(trigger) || trigger.contains(c) || !shown(c)) continue;
      var tx = textIn(c);
      if (tx.length < term.length + 10 || tx.length > 2500) continue;
      var score = (tx.toLowerCase().indexOf(low) >= 0 ? 50 : 0) + (c.matches && c.matches(POPUP) ? 30 : 0) +
        Math.min(tx.length, 400) / 40;
      if (score > bestScore) { best = c; bestScore = score; }
    }
    return best;
  }
  function tidy(text, term) {
    var t = text;
    var low = term.toLowerCase();
    for (var k = 0; k < 4; k++) {
      t = t.replace(/^(glossary|definition|key term|close|done|ok|\u00d7|x)\b[\s:.-]*/i, '');
      if (t.toLowerCase().indexOf(low) === 0) t = t.slice(term.length).replace(/^[\s:.,\u2013\u2014-]+/, '');
    }
    t = t.replace(/[\s]*(close|\u00d7|done|ok)$/i, '');
    return t.trim().slice(0, 900);
  }
  async function closePopup(p, trigger) {
    var btn = p.querySelector('button[aria-label*="lose" i], button[title*="lose" i], [data-dismiss], [data-bs-dismiss], ' +
      '.close, .btn-close, button[class*=close i], [role=button][aria-label*="lose" i]');
    if (btn) { btn.click(); await sleep(250); if (!shown(p)) return; }
    var esc = {key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true};
    try {
      (document.activeElement || document.body).dispatchEvent(new KeyboardEvent('keydown', esc));
      document.dispatchEvent(new KeyboardEvent('keydown', esc));
    } catch (e) {}
    await sleep(250);
    if (!shown(p)) return;
    trigger.click();
    await sleep(250);
    if (!shown(p)) return;
    try {
      document.body.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
      document.body.dispatchEvent(new MouseEvent('click', {bubbles: true}));
    } catch (e) {}
    await sleep(200);
  }
  async function readOne(item) {
    var el = item.el, term = item.t;
    if (!el.isConnected) return '';
    var before = popupsShown(), added = [];
    var mo = new MutationObserver(function (ms) {
      ms.forEach(function (m) {
        if (m.type === 'attributes') added.push(m.target);
        else m.addedNodes.forEach(function (n) { if (n.nodeType === 1) added.push(n); });
      });
    });
    mo.observe(document.body, {childList: true, subtree: true, attributes: true,
      attributeFilter: ['class', 'style', 'hidden', 'aria-hidden', 'open']});
    try { el.scrollIntoView({block: 'center'}); } catch (e) {}
    el.click();
    var found = null;
    for (var w = 0; w < 15 && !found; w++) {
      await sleep(100);
      found = pick(before, added, el, term);
    }
    if (found) await sleep(150);   // let it finish filling in
    mo.disconnect();
    var text = found ? tidy(textIn(found), term) : '';
    if (found) await closePopup(found, el);
    return text;
  }
  window.__shugyoDefs = async function (report) {
    var items = window.__shugyoGlossEls || [];
    var out = [];
    var y = window.scrollY;
    var block = function (e) {
      var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
      if (a) e.preventDefault();   // read the pop-up, don't leave the page
    };
    document.addEventListener('click', block, true);
    try {
      for (var i = 0; i < items.length; i++) {
        if (report) report(i, items.length);
        var d = '';
        try { d = await readOne(items[i]); } catch (e) {}
        if (d && d.length > 6) out.push({t: items[i].t, d: d});
      }
    } finally {
      document.removeEventListener('click', block, true);
      try { window.scrollTo(0, y); } catch (e) {}
    }
    return out;
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
        if (cmd === 'defs') {
          window.__shugyoDefs(function (done, total) {
            b.postMessage(JSON.stringify({kind: 'progress', done: done, total: total}));
          }).then(function (list) {
            b.postMessage(JSON.stringify({kind: 'defs', data: list}));
          }, function (err) {
            b.postMessage(JSON.stringify({kind: 'defs', data: [], error: String(err)}));
          });
          return;
        }
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
