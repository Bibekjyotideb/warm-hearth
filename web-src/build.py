#!/usr/bin/env python3
"""
Builds the static site into ../web from the page bodies in pages/.
Header, footer, meta tags and company details live here once.

    python3 web-src/build.py
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).parent
OUT = ROOT.parent / "web"

SITE = {
    "url": "https://warmhearth.lusanetech.com",
    "app": "WarmHearth",
    "company": "Lusane Technologies",
    # Fill these in (same as firebase/functions/src/config.js)
    "owner": "[Proprietor's full legal name]",
    "address": "[Registered business address], Tripura, India",
    "support": "support@lusanetech.com",
    "billing": "billing@lusanetech.com",
    "privacy_contact": "support@lusanetech.com",
    "play": "https://play.google.com/store/apps/details?id=com.lusanetech.warmhearth",
    "updated": "25 October 2026",
}

LOGO = """<svg viewBox="0 0 40 40" aria-hidden="true"><rect width="40" height="40" rx="11" fill="#0F4C4A"/>
<path d="M20 9 8 19h3.5v12h7.5v-7h2v7h7.5V19H32Z" fill="#F6F7F2"/><circle cx="20" cy="22.5" r="3.2" fill="#F2A54A"/></svg>"""

HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<meta name="description" content="{description}">
<link rel="canonical" href="{url}{path}">
<meta property="og:title" content="{title}">
<meta property="og:description" content="{description}">
<meta property="og:url" content="{url}{path}">
<meta property="og:type" content="website">
<meta property="og:image" content="{url}/img/og.png">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0F4C4A">
<link rel="icon" href="/img/favicon.svg" type="image/svg+xml">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible+Next:wght@400;600;700;800&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/css/site.css">
{extra_head}
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header class="site-head">
  <div class="wrap">
    <a class="brand" href="/">{logo}<span>WarmHearth</span></a>
    <nav class="site-nav" aria-label="Main">
      <a href="/#features">Features</a>
      <a href="/#how-it-works">How it works</a>
      <a href="/#pricing">Pricing</a>
      <a href="/support">Help</a>
    </nav>
  </div>
</header>
<main id="main">
"""

FOOT = """</main>
<footer class="site-foot">
  <div class="wrap foot-grid">
    <div>
      <a class="brand" href="/" style="color:#fff">{logo}<span>WarmHearth</span></a>
      <p style="margin-top:1rem">The elderly launcher that keeps families close. A product of {company}, {address}.</p>
      <p>Support: <a href="mailto:{support}">{support}</a><br>Billing: <a href="mailto:{billing}">{billing}</a></p>
    </div>
    <div>
      <h4>Legal</h4>
      <ul>
        <li><a href="/privacy">Privacy policy</a></li>
        <li><a href="/terms">Terms of service</a></li>
        <li><a href="/refunds">Refund policy</a></li>
        <li><a href="/delete-account">Delete your account</a></li>
      </ul>
    </div>
    <div>
      <h4>Help</h4>
      <ul>
        <li><a href="/support">Contact support</a></li>
        <li><a href="/#faq">Questions</a></li>
        <li><a href="{play}">Google Play</a></li>
      </ul>
    </div>
  </div>
  <div class="wrap" style="margin-top:2rem;opacity:.8">© 2026 {company}. WarmHearth is not an emergency service. In an emergency, call your local emergency number.</div>
</footer>
{scripts}
</body>
</html>
"""

PAGES = [
    # file, path, title, description, scripts
    ("index.html", "/", "Elderly Launcher for Seniors – WarmHearth | Big icons, SOS, medicine alarms",
     "WarmHearth is a simple elderly launcher for Android: big icons, photo contacts, SOS with family alerts, medicine reminders and AI scam checks. Free, with a 7-day free trial of Premium.",
     '<script src="/js/demo.js" defer></script>'),
    ("privacy.html", "/privacy", "Privacy policy – WarmHearth", "How WarmHearth collects, uses and protects personal data.", ""),
    ("terms.html", "/terms", "Terms of service – WarmHearth", "The terms for using WarmHearth.", ""),
    ("refunds.html", "/refunds", "Refunds & cancellation – WarmHearth", "How to cancel WarmHearth Premium and get a refund.", ""),
    ("support.html", "/support", "Help & support – WarmHearth", "Get help with WarmHearth or contact support.",
     '<script type="module" src="/js/support.js"></script>'),
    ("delete-account.html", "/delete-account", "Delete your account – WarmHearth", "How to delete your WarmHearth account and data.",
     '<script type="module" src="/js/support.js"></script>'),
    ("404.html", "/404", "Page not found – WarmHearth", "This page does not exist.", ""),
]


ICONS = ROOT / "node_modules" / "@material-symbols" / "svg-500" / "rounded"


def icon(name: str) -> str:
    """Inline Material Symbol (same icon family as the Android app), coloured by currentColor."""
    f = ICONS / f"{name}-fill.svg"
    if not f.exists():
        f = ICONS / f"{name}.svg"
    svg = f.read_text()
    path = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    return f'<svg class="ico" viewBox="0 -960 960 960" aria-hidden="true" fill="currentColor">{path}</svg>'


def screen(name: str) -> str:
    return fill((ROOT / "screens" / f"{name}.html").read_text())


def phone(name: str, label: str = "") -> str:
    aria = f' role="img" aria-label="{label}"' if label else ""
    return f'<div class="phone"{aria}><div class="screen">{screen(name)}</div></div>'


def fill(s: str) -> str:
    s = re.sub(r"\{\{phone:(\w+)(?:\|([^}]*))?\}\}", lambda m: phone(m.group(1), m.group(2) or ""), s)
    s = re.sub(r"\{\{screen:(\w+)\}\}", lambda m: screen(m.group(1)), s)
    s = re.sub(r"\{\{icon:(\w+)\}\}", lambda m: icon(m.group(1)), s)
    return re.sub(r"\{\{(\w+)\}\}", lambda m: SITE[m.group(1)], s)


def main():
    OUT.mkdir(exist_ok=True)
    for file, path, title, desc, scripts in PAGES:
        body = fill((ROOT / "pages" / file).read_text())
        extra = ""
        if file == "index.html":
            extra = '<script type="application/ld+json">' + json.dumps({
                "@context": "https://schema.org", "@type": "SoftwareApplication", "name": "WarmHearth",
                "operatingSystem": "Android", "applicationCategory": "LifestyleApplication",
                "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD", "description": "Free; Premium subscription with 7-day free trial"},
                "publisher": {"@type": "Organization", "name": SITE["company"]},
            }) + "</script>"
        html = HEAD.format(title=title, description=desc, url=SITE["url"], path="" if path == "/" else path,
                           extra_head=extra, logo=LOGO) + body + FOOT.format(logo=LOGO, scripts=scripts, **SITE)
        (OUT / file).write_text(html)
        print("built", file)

    # docs/app-preview.html: every app screen side by side, for reviewing the design without a phone.
    shots = [("home", "Home screen"), ("call", "Tap a face"), ("sos", "SOS"), ("meds", "Medicine alarm"), ("scam", "Scam check"), ("family", "Family phone")]
    grid = "".join(f'<figure class="preview">{phone(n, t)}<figcaption>{t}</figcaption></figure>' for n, t in shots)
    preview = HEAD.format(title="WarmHearth app preview", description="App screens", url=SITE["url"], path="/preview", extra_head="", logo=LOGO)
    (ROOT.parent / "docs").mkdir(exist_ok=True)
    (ROOT.parent / "docs" / "app-preview.html").write_text(
        preview.replace('href="/css/site.css"', 'href="../web/css/site.css"').replace('href="/img/favicon.svg"', 'href="../web/img/favicon.svg"')
        + f'<section><div class="wrap preview-grid">{grid}</div></section></main></body></html>')

    urls = "".join(f"<url><loc>{SITE['url']}{'' if p == '/' else p}</loc></url>" for f, p, *_ in PAGES if f != "404.html")
    (OUT / "sitemap.xml").write_text(f'<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">{urls}</urlset>\n')
    (OUT / "robots.txt").write_text(f"User-agent: *\nDisallow: /admin/\nSitemap: {SITE['url']}/sitemap.xml\n")


if __name__ == "__main__":
    main()
