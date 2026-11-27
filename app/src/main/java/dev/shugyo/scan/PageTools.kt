package dev.shugyo.scan

/** Small scripts the browser runs on the page you're looking at. */
object PageTools {
    /**
     * Every link on the page, including the ones inside menus that are closed or won't open
     * (a site's hamburger menu keeps its links in the page even while it's hidden).
     */
    val LINKS = """
(function () {
  var out = [], seen = {};
  var as = document.querySelectorAll('a[href]');
  for (var i = 0; i < as.length && out.length < 500; i++) {
    var a = as[i], h = a.href;
    if (!h || h.indexOf('javascript:') === 0 || h.indexOf('mailto:') === 0) continue;
    if (h.split('#')[0] === location.href.split('#')[0] && h.indexOf('#') >= 0) continue;
    var t = (a.textContent || '').replace(/\s+/g, ' ').trim();
    if (!t) t = (a.getAttribute('aria-label') || a.getAttribute('title') || '').trim();
    if (!t) { var img = a.querySelector('img[alt]'); if (img) t = (img.getAttribute('alt') || '').trim(); }
    if (!t || seen[h]) continue;
    seen[h] = 1;
    var menu = !!(a.closest && a.closest('nav, [role=navigation], [role=menu], .drawer, .navbar, .primary-navigation, .courseindex, .block, aside, [data-region=drawer], [data-region=fixed-drawer]'));
    out.push({t: t.slice(0, 140), u: h, m: menu});
  }
  return JSON.stringify(out);
})()
"""

    /** Facts about the page that show why it looks or behaves wrong. */
    val CHECK = """
(function () {
  var r = {};
  try {
    var d = document.createElement('div');
    d.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:100vh;visibility:hidden;pointer-events:none';
    document.body.appendChild(d);
    r.vh100 = d.offsetHeight;
    d.parentNode.removeChild(d);
  } catch (e) { r.vh100 = 'err ' + e; }
  r.window = window.innerWidth + 'x' + window.innerHeight;
  r.screen = screen.width + 'x' + screen.height + ' @' + window.devicePixelRatio;
  r.pageHeight = document.documentElement.scrollHeight;
  r.ready = document.readyState;
  r.darkScheme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  r.requirejs = typeof window.require;
  r.jquery = typeof window.jQuery;
  if (window.M && M.cfg) {
    r.moodleTheme = M.cfg.theme;
    r.moodleJsPending = (M.util && M.util.pending_js) ? M.util.pending_js.length + ': ' + M.util.pending_js.slice(0, 8).join(', ') : 'n/a';
  }
  var b = document.body;
  if (b) r.bodyClass = (b.className || '').toString().slice(0, 200);
  var page = document.getElementById('page');
  if (page) {
    var ps = getComputedStyle(page);
    r.page = 'h=' + page.offsetHeight + ' pos=' + ps.position + ' overflowY=' + ps.overflowY + ' top=' + Math.round(page.getBoundingClientRect().top);
  }
  var ds = document.querySelectorAll('.drawer, [data-region="fixed-drawer"], [data-region="drawer"]');
  r.drawers = [];
  for (var i = 0; i < ds.length && i < 6; i++) {
    var el = ds[i], s = getComputedStyle(el), rc = el.getBoundingClientRect();
    r.drawers.push((el.id || String(el.className)).slice(0, 40) + ': ' + s.visibility + '/' + s.display +
      ' x=' + Math.round(rc.left) + ' w=' + Math.round(rc.width) + ' h=' + Math.round(rc.height) + ' z=' + s.zIndex);
  }
  var btn = document.querySelector('[data-toggler="drawers"], [data-action="openDrawer"], .drawer-toggler button, button.navbar-toggler, [data-toggle="drawer"]');
  if (btn) r.menuButton = btn.outerHTML.slice(0, 220);
  return JSON.stringify(r);
})()
"""

    /** The page's embedded frames (like a textbook shown inside Moodle), with their size on screen. */
    val FRAMES = """
(function () {
  var out = [];
  var fs = document.querySelectorAll('iframe, frame, object[data], embed[src]');
  for (var i = 0; i < fs.length; i++) {
    var f = fs[i], r = f.getBoundingClientRect();
    out.push({u: f.src || f.getAttribute('data') || '', w: Math.round(r.width), h: Math.round(r.height)});
  }
  return JSON.stringify({vw: window.innerWidth, vh: window.innerHeight, frames: out});
})()
"""

    /** Back to the phone layout. */
    val MOBILE = """
(function () {
  var m = document.querySelector('meta[name=viewport]');
  if (m) m.setAttribute('content', 'width=device-width, initial-scale=1');
})()
"""

    /** Lays the page out at a desktop width, like Chrome's Desktop site. */
    val DESKTOP = """
(function () {
  var m = document.querySelector('meta[name=viewport]');
  if (!m) { m = document.createElement('meta'); m.setAttribute('name', 'viewport'); (document.head || document.documentElement).appendChild(m); }
  m.setAttribute('content', 'width=1280');
})()
"""
}
