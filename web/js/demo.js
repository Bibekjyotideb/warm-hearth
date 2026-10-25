// The live phone in the hero: tap a face, hold SOS, tap Speak. Mirrors what the app does.
(() => {
  const root = document.querySelector("#demo .app-home");
  if (!root) return;
  const q = (s) => root.querySelector(s);
  const dialog = q("[data-dialog]"), text = q("[data-dialog-text]"), av = q("[data-dialog-av]");
  const yes = q("[data-yes]"), no = q("[data-no]");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

  const now = new Date();
  q("[data-clock]").textContent = now.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });
  q("[data-date]").textContent = now.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });

  function open({ message, avatar, color, onYes }) {
    text.textContent = message;
    av.hidden = !avatar;
    if (avatar) { av.textContent = avatar; av.style.setProperty("--c", color); }
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
    const a = b.querySelector(".av");
    open({ message: `Call ${b.dataset.name}?`, avatar: a.textContent, color: a.style.getPropertyValue("--c"),
      onYes: () => open({ message: `Calling ${b.dataset.name}…`, avatar: a.textContent, color: a.style.getPropertyValue("--c") }) });
  }));
  q("[data-speak]").addEventListener("click", () => open({ message: 'Say "Call Asha", "WhatsApp kholo" or "दवा"' }));

  // Hold SOS for 3 seconds (keyboard: hold Space or Enter)
  const sos = q("[data-sos]"), fill = sos.querySelector(".fill"), label = sos.querySelector(".lbl");
  const HOLD = 3000;
  let start = 0, raf = 0;
  function tick() {
    const left = HOLD - (performance.now() - start);
    fill.style.transform = `scaleX(${Math.min(1, 1 - left / HOLD)})`;
    label.textContent = left > 0 ? `Hold… ${Math.ceil(left / 1000)}` : "Get help";
    if (left > 0) raf = requestAnimationFrame(tick);
    else { reset(); open({ message: "Family alerted with your location. Calling Ravi now." }); }
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
