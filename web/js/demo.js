// The hero: their phone (left) and yours (right), linked. What happens on theirs shows up on yours,
// the way the real apps behave: "I'm OK", "Ask my family to call me", hold SOS, tap a face.
(() => {
  const root = document.querySelector("#demo-senior .app-home");
  const fam = document.querySelector("#demo-family .app-family");
  if (!root) return;
  const q = (s, el = root) => el.querySelector(s);
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const dialog = q("[data-dialog]"), text = q("[data-dialog-text]"), av = q("[data-dialog-av]");
  const yes = q("[data-yes]"), no = q("[data-no]");

  const now = new Date();
  const hm = now.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });
  q("[data-clock]").textContent = hm;
  q("[data-date]").textContent = now.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });
  const hour = now.getHours();
  q("[data-checkin] .app-card-title").textContent = (hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening") + ", Kamala";

  // ---- family phone helpers
  let pushTimer = 0;
  function push(tone, title, body) {
    if (!fam) return;
    const p = q("[data-push]", fam);
    p.className = `fam-push t-${tone}`;
    q("[data-push-title]", fam).textContent = title;
    q("[data-push-body]", fam).textContent = body;
    requestAnimationFrame(() => p.classList.add("show"));
    clearTimeout(pushTimer);
    pushTimer = setTimeout(() => p.classList.remove("show"), 4200);
  }
  function hero(tone, title, sub) {
    if (!fam) return;
    q("[data-hero]", fam).className = `fam-hero t-${tone}`;
    q("[data-hero-title]", fam).textContent = title;
    q("[data-hero-sub]", fam).textContent = sub;
  }
  const used = () => {
    if (!fam) return;
    q("[data-used]", fam).textContent = "just now";
    if (q("[data-hero-title]", fam).textContent === "All good") q("[data-hero-sub]", fam).textContent = "Used the phone just now";
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
  q("[data-speak]").addEventListener("click", () => open({ message: 'Say "Call Ravi", "WhatsApp kholo" or "दवा"' }));

  // "I'm OK"
  q("[data-ok]").addEventListener("click", () => {
    const card = q("[data-checkin]");
    card.classList.add("done");
    q("[data-ok]").hidden = true;
    q("[data-checkin-sub]").hidden = true;
    q("[data-ok-done]").hidden = false;
    used();
    if (fam) q("[data-checkin-val]", fam).textContent = `OK at ${hm}`;
    push("grn", "✅ All good today", "Maa says she's OK today.");
  });

  // "Ask my family to call me"
  const callme = q("[data-callme]");
  callme.addEventListener("click", () => {
    callme.className = "app-soft t-grn";
    callme.querySelector("span").textContent = "Sent. They'll call you soon";
    used();
    push("blu", "📞 Call request", "Maa would like you to call her.");
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
      open({ message: "Family alerted with your location. Calling Ravi now." });
      used();
      hero("red", "Maa pressed SOS", "Just now. Tap to see her location");
      push("red", "🆘 SOS", "Maa pressed SOS and needs help. Tap for her location.");
      setTimeout(() => hero("grn", "All good", "Used the phone just now"), 9000);
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

// Pricing: ₹ for visitors in India, $ elsewhere; yearly by default. Prices match Play Console.
(() => {
  const box = document.getElementById("price-switch");
  if (!box) return;
  const PRICES = {
    IN: { yearly: ["₹899", " / year", "7 days free, then billed yearly through Google Play. Save 50% vs monthly (₹149 × 12)."],
          monthly: ["₹149", " / month", "7 days free, then billed monthly through Google Play. Cancel any time."] },
    INTL: { yearly: ["$39.99", " / year", "7 days free, then billed yearly through Google Play. Save 33% vs monthly ($4.99 × 12)."],
            monthly: ["$4.99", " / month", "7 days free, then billed monthly through Google Play. Cancel any time."] },
  };
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "";
  const inIndia = /Asia\/(Kolkata|Calcutta)/.test(tz) || /-IN$|^hi\b/.test(navigator.language || "");
  const state = { region: inIndia ? "IN" : "INTL", period: "yearly" };
  const price = document.querySelector("[data-price]"), per = document.querySelector("[data-per]"), note = document.querySelector("[data-note]");
  function render() {
    const [p, u, n] = PRICES[state.region][state.period];
    price.textContent = p; per.textContent = u; note.textContent = n;
    document.querySelector("[data-free]").textContent = state.region === "IN" ? "₹0" : "$0";
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

// /devices: Amazon.in and ₹ ranges for visitors in India, Amazon.com and $ elsewhere; price filter.
(() => {
  const list = document.querySelector(".devices");
  if (!list) return;
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "";
  const india = /Asia\/(Kolkata|Calcutta)/.test(tz) || /-IN$|^hi\b/.test(navigator.language || "");
  const key = india ? "in" : "intl";
  document.querySelectorAll(".devices [data-in], .tier-filter [data-in]").forEach((el) => {
    const v = el.dataset[key];
    if (el.tagName === "A") el.href = v; else el.textContent = v;
  });
  const filter = document.querySelector(".tier-filter");
  filter.addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    filter.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    list.querySelectorAll(".device").forEach((d) => { d.hidden = b.dataset.tier !== "all" && d.dataset.tier !== b.dataset.tier; });
  });
})();
