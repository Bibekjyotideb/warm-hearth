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
    "updated": "26 October 2026",
    # Affiliate tracking IDs (Amazon Associates), currently the partner account (owner's brother).
    # That account must list warmhearth.lusanetech.com under Account settings → Website and mobile app list.
    # Leave empty and the links still work, just without commission.
    "amazon_tag_in": "",
    "amazon_tag_us": "",
    # Illustration used as the "photo from family" on screens (no real people's photos on the site).
    "sunrise": '<svg viewBox="0 0 400 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F6C27A"/><stop offset=".55" stop-color="#F3A066"/><stop offset="1" stop-color="#E9895E"/></linearGradient></defs><rect width="400" height="300" fill="url(#sky)"/><circle cx="250" cy="175" r="46" fill="#FFF1C9"/><path d="M0 200 Q90 150 180 190 T400 170 V300 H0Z" fill="#B76E57"/><path d="M0 235 Q120 195 230 228 T400 215 V300 H0Z" fill="#7F4E45"/><path d="M0 262 H400 V300 H0Z" fill="#3E3431"/><g stroke="#3E3431" stroke-width="7"><path d="M20 262 V214M80 262 V214M140 262 V214M200 262 V214M260 262 V214M320 262 V214M380 262 V214"/><path d="M0 214 H400" stroke-width="9"/></g><path d="M300 120 q8 -6 16 0 q8 -6 16 0" fill="none" stroke="#7F4E45" stroke-width="3" stroke-linecap="round"/></svg>',
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
        <li><a href="/devices">Watches that work with WarmHearth</a></li>
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
    ("index.html", "/", "Elderly Launcher for Seniors – WarmHearth | Big icons, SOS, family safety alerts",
     "WarmHearth turns your parent's Android phone into a simple elderly launcher (big buttons, SOS, medicine alarms, scam call shield) and gives you a family app with daily check-ins and safety alerts. Free, with a 7-day trial of Premium.",
     '<script src="/js/demo.js" defer></script>'),
    ("privacy.html", "/privacy", "Privacy policy – WarmHearth", "How WarmHearth collects, uses and protects personal data.", ""),
    ("terms.html", "/terms", "Terms of service – WarmHearth", "The terms for using WarmHearth.", ""),
    ("refunds.html", "/refunds", "Refunds & cancellation – WarmHearth", "How to cancel WarmHearth Premium and get a refund.", ""),
    ("support.html", "/support", "Help & support – WarmHearth", "Get help with WarmHearth or contact support.",
     '<script type="module" src="/js/support.js"></script>'),
    ("delete-account.html", "/delete-account", "Delete your account – WarmHearth", "How to delete your WarmHearth account and data.",
     '<script type="module" src="/js/support.js"></script>'),
    ("devices.html", "/devices", "Best smartwatch for elderly parents – watches that work with WarmHearth",
     "Smartwatches and fitness bands that share heart rate, steps, sleep and more with family through WarmHearth and Health Connect. Budget to fall-detection picks.",
     '<script src="/js/demo.js" defer></script>'),
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
    s = s.replace("{{devices}}", devices_html()) if "{{devices}}" in s else s
    return re.sub(r"\{\{(\w+)\}\}", lambda m: SITE[m.group(1)], s)


def devices_html() -> str:
    """The /devices list, from data/devices.json. Links carry the affiliate tag; region picked in JS."""
    from urllib.parse import quote_plus
    items = json.loads((ROOT / "data" / "devices.json").read_text())
    tiers = {"budget": ("Under ₹5,000", "Under $60"), "mid": ("₹5,000 – ₹15,000", "$60 – $200"), "premium": ("Above ₹15,000", "Above $200")}
    out = []
    for it in items:
        q = quote_plus(it["search"])
        tin = f"&tag={SITE['amazon_tag_in']}" if SITE["amazon_tag_in"] else ""
        tus = f"&tag={SITE['amazon_tag_us']}" if SITE["amazon_tag_us"] else ""
        t_in, t_us = tiers[it["tier"]]
        feats = "".join(f'<li class="{"warn" if f in ("Fall detection", "SOS call") else ""}">{f}</li>' for f in it["features"])
        out.append(
            f'<article class="device" id="{it["id"]}" data-tier="{it["tier"]}">'
            f'<div class="device-head"><h3>{it["name"]}</h3><span class="tier" data-in="{t_in}" data-intl="{t_us}">{t_us}</span></div>'
            f'<ul class="feats">{feats}</ul><p>{it["note"]}</p><p class="muted small">Syncs through the {it["app"]} app.</p>'
            f'<a class="btn btn-ghost" rel="sponsored noopener" target="_blank" '
            f'data-in="https://www.amazon.in/s?k={q}{tin}" data-intl="https://www.amazon.com/s?k={q}{tus}" '
            f'href="https://www.amazon.com/s?k={q}{tus}">See prices on Amazon</a><span class="aff">Affiliate link</span></article>'
        )
    return "".join(out)


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
    shots = [("home", "Home screen"), ("note", "Photo from family"), ("call", "Tap a face"), ("sos", "SOS"), ("meds", "Medicine alarm"), ("scam", "Scam check"),
             ("shield", "Scam call shield"), ("family", "Family dashboard"), ("famalert", "Family alerts"), ("famsafety", "Safety settings"), ("famvitals", "Health readings"), ("famphoto", "Send a photo")]
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
