// Region: ?c=GB in the URL (the app sends this), else a guess from the time zone, else the browser
// language. Used for example names, prices, price ranges and the Amazon store. Works worldwide;
// India gets Indian names and ₹.
const REGION = (() => {
  const q = new URLSearchParams(location.search).get("c");
  if (q && /^[A-Za-z]{2}$/.test(q)) return q.toUpperCase();
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "";
  const TZ = {
    "Asia/Kolkata": "IN", "Asia/Calcutta": "IN", "Europe/London": "GB", "Europe/Dublin": "IE", "Australia/Sydney": "AU",
    "Australia/Melbourne": "AU", "Australia/Brisbane": "AU", "Australia/Perth": "AU", "Australia/Adelaide": "AU",
    "Pacific/Auckland": "NZ", "Asia/Tokyo": "JP", "Asia/Singapore": "SG", "Asia/Dubai": "AE", "Asia/Riyadh": "SA",
    "America/Toronto": "CA", "America/Vancouver": "CA", "America/Mexico_City": "MX", "America/Sao_Paulo": "BR",
    "Europe/Berlin": "DE", "Europe/Paris": "FR", "Europe/Madrid": "ES", "Europe/Rome": "IT", "Europe/Amsterdam": "NL",
    "Europe/Brussels": "BE", "Europe/Vienna": "AT", "Europe/Stockholm": "SE", "Europe/Warsaw": "PL", "Europe/Istanbul": "TR",
    "Africa/Cairo": "EG",
  };
  if (TZ[tz]) return TZ[tz];
  const m = (navigator.language || "").match(/-([A-Za-z]{2})$/);
  if (m) return m[1].toUpperCase();
  return tz.startsWith("America/") ? "US" : "US";
})();
const EURO = ["DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "IE", "FI", "GR", "SK", "SI", "LU", "LV", "LT", "EE", "MT", "CY", "HR"];
const MONEY = REGION === "IN" ? "IN" : REGION === "GB" ? "GB" : EURO.includes(REGION) ? "EU" : "US";

// Indian visitors see Indian names and examples (data-india holds the alternative text).
if (REGION === "IN") {
  document.querySelectorAll("[data-india]").forEach((el) => { el.textContent = el.dataset.india; });
  document.querySelectorAll("[data-india-name]").forEach((el) => { el.dataset.name = el.dataset.indiaName; });
}

// British English: "Mum" in the UK, Ireland, Australia, New Zealand, South Africa.
const MUM = ["GB", "IE", "AU", "NZ", "ZA"].includes(REGION);
if (MUM) {
  const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let n = w.nextNode(); n; n = w.nextNode()) if (n.nodeValue.includes("Mom")) n.nodeValue = n.nodeValue.replace(/\bMom\b/g, "Mum");
}

// The hero: their phone (left) and yours (right), linked. What happens on theirs shows up on yours,
// the way the real apps behave: "I'm OK", "Ask my family to call me", hold SOS, tap a face.
(() => {
  const root = document.querySelector("#demo-senior .app-home");
  const fam = document.querySelector("#demo-family .app-family");
  if (!root) return;
  const N = REGION === "IN" ? { mom: "Maa", kid: "Ravi" } : { mom: MUM ? "Mum" : "Mom", kid: "James" };
  const q = (s, el = root) => el.querySelector(s);
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const dialog = q("[data-dialog]"), text = q("[data-dialog-text]"), av = q("[data-dialog-av]");
  const yes = q("[data-yes]"), no = q("[data-no]");

  const now = new Date();
  const loc = navigator.language || "en-US";
  const hm = now.toLocaleTimeString(loc, { hour: "numeric", minute: "2-digit" });
  // Big digits, small AM/PM (the app does the same so it never wraps).
  const parts = new Intl.DateTimeFormat(loc, { hour: "numeric", minute: "2-digit" }).formatToParts(now);
  const clock = q("[data-clock]");
  clock.textContent = parts.filter((x) => x.type !== "dayPeriod").map((x) => x.value).join("").trim();
  const period = parts.find((x) => x.type === "dayPeriod");
  if (period) { const sm = document.createElement("small"); sm.textContent = " " + period.value; clock.appendChild(sm); }
  q("[data-date]").textContent = now.toLocaleDateString(loc, { weekday: "long", day: "numeric", month: "long" });
  const hour = now.getHours();
  const title = q("[data-checkin] .app-card-title");
  const senior = title.querySelector("[data-senior]").textContent;
  title.textContent = (hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening") + ", " + senior;

  // ---- family phone helpers
  let pushTimer = 0;
  function push(tone, t, body) {
    if (!fam) return;
    const p = q("[data-push]", fam);
    p.className = `fam-push t-${tone}`;
    q("[data-push-title]", fam).textContent = t;
    q("[data-push-body]", fam).textContent = body;
    requestAnimationFrame(() => p.classList.add("show"));
    clearTimeout(pushTimer);
    pushTimer = setTimeout(() => p.classList.remove("show"), 4200);
  }
  function hero(tone, t, sub) {
    if (!fam) return;
    q("[data-hero]", fam).className = `fam-hero t-${tone}`;
    q("[data-hero-title]", fam).textContent = t;
    q("[data-hero-sub]", fam).textContent = sub;
  }
  const used = () => {
    if (!fam) return;
    q("[data-used]", fam).textContent = "In the last hour";
    if (q("[data-hero-title]", fam).textContent === "All good") q("[data-hero-sub]", fam).textContent = "Active in the last hour";
  };

  // ---- dialog on their phone
  function open({ message, avatar, tone, onYes }) {
    text.textContent = message;
    av.hidden = !avatar;
    if (avatar) { av.textContent = avatar; av.className = `av av-xl ${tone}`; }
    yes.hidden = !onYes;
    no.querySelector("span").textContent = onYes ? "No" : "OK";
    yes.onclick = onYes;
    dialog.hidden = false;
    (onYes ? yes : no).focus({ preventScroll: true });
  }
  const close = () => { dialog.hidden = true; };
  no.onclick = close;
  dialog.addEventListener("click", (e) => { if (e.target === dialog) close(); });
  root.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });

  root.querySelectorAll(".app-face").forEach((b) => b.addEventListener("click", () => {
    const a = b.querySelector(".av"), tone = [...a.classList].find((c) => c.startsWith("t-"));
    used();
    open({ message: `Call ${b.dataset.name}?`, avatar: a.textContent, tone,
      onYes: () => open({ message: `Calling ${b.dataset.name}…`, avatar: a.textContent, tone }) });
  }));
  q("[data-speak]").addEventListener("click", () => open({ message: REGION === "IN" ? 'Say "Call Ravi", "WhatsApp kholo" or "दवा"' : 'Say "Call James", "Open WhatsApp" or "Medicine"' }));

  // "I'm OK"
  q("[data-ok]").addEventListener("click", () => {
    q("[data-checkin]").classList.add("done");
    q("[data-ok]").hidden = true;
    q("[data-checkin-sub]").hidden = true;
    q("[data-ok-done]").hidden = false;
    used();
    if (fam) q("[data-checkin-val]", fam).textContent = `OK at ${hm}`;
    push("grn", "✅ All good today", `${N.mom} says she's OK today.`);
  });

  // "Ask my family to call me"
  const callme = q("[data-callme]");
  callme.addEventListener("click", () => {
    callme.className = "app-soft t-grn";
    callme.querySelector("span").textContent = "Sent. They'll call you soon";
    used();
    push("blu", "📞 Call request", `${N.mom} would like you to call her.`);
  });

  // Hold SOS for 3 seconds (keyboard: hold Space or Enter)
  const sos = q("[data-sos]"), fill = sos.querySelector(".fill"), label = sos.querySelector(".lbl");
  const HOLD = 3000;
  let start = 0, raf = 0;
  function tick() {
    const left = HOLD - (performance.now() - start);
    fill.style.transform = `scaleX(${Math.min(1, 1 - left / HOLD)})`;
    label.textContent = left > 0 ? `Hold… ${Math.ceil(left / 1000)}` : "Get help";
    if (left > 0) raf = requestAnimationFrame(tick);
    else {
      reset();
      open({ message: `Family alerted with your location. Calling ${N.kid} now.` });
      used();
      hero("red", `${N.mom} pressed SOS`, "Just now. Tap to see her location");
      push("red", "🆘 SOS", `${N.mom} pressed SOS and needs help. Tap for her location.`);
      setTimeout(() => hero("grn", "All good", "Active in the last hour"), 9000);
    }
  }
  function begin(e) {
    if (e.type === "keydown" && (e.repeat || ![" ", "Enter"].includes(e.key))) return;
    e.preventDefault();
    start = performance.now();
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(tick);
  }
  function reset() {
    cancelAnimationFrame(raf);
    fill.style.transition = reduce ? "none" : "transform .25s";
    fill.style.transform = "scaleX(0)";
    label.textContent = "Get help";
    setTimeout(() => (fill.style.transition = ""), 260);
  }
  sos.addEventListener("pointerdown", begin);
  ["pointerup", "pointerleave", "pointercancel"].forEach((ev) => sos.addEventListener(ev, () => { if (!dialog.hidden) return; reset(); }));
  sos.addEventListener("keydown", begin);
  sos.addEventListener("keyup", reset);
  sos.addEventListener("click", (e) => e.preventDefault());
})();

// Pricing: the visitor's currency where we set a local price (US$, £, €, ₹); yearly by default.
// Must match Play Console (docs/OPERATIONS.md, "Prices by country"). Other countries: shown in Play.
(() => {
  const box = document.getElementById("price-switch");
  if (!box) return;
  const P = {
    US: { sym: "$", m: "$4.99", y: "$39.99", save: 33 },
    GB: { sym: "£", m: "£4.49", y: "£34.99", save: 35 },
    EU: { sym: "€", m: "€4.99", y: "€39.99", save: 33 },
    IN: { sym: "₹", m: "₹149", y: "₹899", save: 50 },
  };
  const state = { region: MONEY, period: "yearly" };
  const price = document.querySelector("[data-price]"), per = document.querySelector("[data-per]"), note = document.querySelector("[data-note]");
  function render() {
    const p = P[state.region];
    const yearly = state.period === "yearly";
    price.textContent = yearly ? p.y : p.m;
    per.textContent = yearly ? " / year" : " / month";
    note.textContent = yearly
      ? `7 days free, then billed yearly through Google Play. Save ${p.save}% vs monthly.`
      : "7 days free, then billed monthly through Google Play. Cancel any time.";
    document.querySelector("[data-free]").textContent = p.sym + "0";
    box.querySelectorAll("[data-period]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.period === state.period)));
    box.querySelectorAll("[data-region]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.region === state.region)));
  }
  box.addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    if (b.dataset.period) state.period = b.dataset.period;
    if (b.dataset.region) state.region = b.dataset.region;
    render();
  });
  render();
})();

// /devices: the visitor's Amazon store and currency ranges; price filter.
(() => {
  const list = document.querySelector(".devices");
  if (!list) return;
  const STORE = {
    IN: "amazon.in", US: "amazon.com", GB: "amazon.co.uk", CA: "amazon.ca", AU: "amazon.com.au", DE: "amazon.de", AT: "amazon.de",
    FR: "amazon.fr", BE: "amazon.com.be", IT: "amazon.it", ES: "amazon.es", NL: "amazon.nl", SE: "amazon.se", PL: "amazon.pl",
    IE: "amazon.co.uk", JP: "amazon.co.jp", MX: "amazon.com.mx", BR: "amazon.com.br", AE: "amazon.ae", SA: "amazon.sa",
    SG: "amazon.sg", TR: "amazon.com.tr", EG: "amazon.eg",
  };
  const host = STORE[REGION] || "amazon.com";
  const tags = JSON.parse(list.dataset.tags || "{}"); // affiliate tag per store, from build.py
  list.querySelectorAll("a[data-search]").forEach((a) => {
    const tag = tags[host] ? `&tag=${encodeURIComponent(tags[host])}` : "";
    a.href = `https://www.${host}/s?k=${encodeURIComponent(a.dataset.search)}${tag}`;
  });
  const key = { IN: "inr", GB: "gbp", EU: "eur", US: "usd" }[MONEY];
  document.querySelectorAll("[data-usd]").forEach((el) => { el.textContent = el.dataset[key]; });
  const filter = document.querySelector(".tier-filter");
  filter.addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    filter.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    list.querySelectorAll(".device").forEach((d) => { d.hidden = b.dataset.tier !== "all" && d.dataset.tier !== b.dataset.tier; });
  });
})();
