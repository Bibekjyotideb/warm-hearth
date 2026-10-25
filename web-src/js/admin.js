// WarmHearth admin panel. Reads Firestore directly; access is enforced by the rules
// (custom claim admin=true, granted with `npm run set-admin -- you@lusanetech.com`).
import { initializeApp } from "firebase/app";
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut } from "firebase/auth";
import {
  addDoc, average, collection, doc, getAggregateFromServer, getCountFromServer, getDocs, limit, onSnapshot,
  orderBy, query, serverTimestamp, startAfter, Timestamp, updateDoc, where,
} from "firebase/firestore";
import { getFirestore } from "firebase/firestore";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const $ = (id) => document.getElementById(id);
const DAY = 86_400_000;
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmtDate = (t) => (t?.toDate ? t.toDate() : t ? new Date(t) : null)?.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "2-digit" }) ?? "–";
const fmtAgo = (t) => {
  const d = t?.toDate ? t.toDate() : null;
  if (!d) return "–";
  const days = Math.floor((Date.now() - d) / DAY);
  return days <= 0 ? "today" : days === 1 ? "yesterday" : `${days} days ago`;
};
const num = (n) => Number(n || 0).toLocaleString("en-IN");
const pct = (a, b) => (b ? Math.round((a / b) * 1000) / 10 : 0);
const count = async (q) => (await getCountFromServer(q)).data().count;
const since = (days) => Timestamp.fromMillis(Date.now() - days * DAY);

/* ---------------- auth ---------------- */
$("signin-btn").onclick = () => signInWithPopup(auth, new GoogleAuthProvider()).catch((e) => ($("signin-msg").textContent = e.message));
$("signout").onclick = () => signOut(auth);

onAuthStateChanged(auth, async (user) => {
  if (!user) { $("app").hidden = true; $("signin").hidden = false; return; }
  const token = await user.getIdTokenResult(true);
  if (!token.claims.admin) {
    $("signin").hidden = false; $("app").hidden = true;
    $("signin-msg").innerHTML = `<b>${esc(user.email)}</b> is not an admin yet.<br>Run <code>npm run set-admin -- ${esc(user.email)}</code> in firebase/functions, then sign in again.`;
    $("signin-btn").textContent = "Sign in again";
    $("signin-btn").onclick = () => signOut(auth);
    return;
  }
  $("signin").hidden = true; $("app").hidden = false;
  $("who").textContent = user.email;
  route();
  watchOpenTickets();
});

/* ---------------- routing ---------------- */
const loaded = new Set();
function route() {
  const tab = (location.hash || "#overview").slice(1);
  document.querySelectorAll(".tab").forEach((s) => (s.hidden = s.id !== `tab-${tab}`));
  document.querySelectorAll(".side nav a").forEach((a) => a.classList.toggle("on", a.dataset.tab === tab));
  if (loaded.has(tab)) return;
  loaded.add(tab);
  ({ overview: loadOverview, funnel: loadFunnel, users: loadUsers, purchases: loadPurchases, tickets: loadTickets }[tab] || loadOverview)();
}
addEventListener("hashchange", () => auth.currentUser && route());

/* ---------------- overview ---------------- */
async function loadOverview() {
  const devices = collection(db, "devices"), users = collection(db, "users"), purchases = collection(db, "subscriptions");
  const [phones, dau, wau, mau, premiumPhones, fam, famMau, sales30, refunds30, open, activeSubs] = await Promise.all([
    count(devices), count(query(devices, where("lastActiveAt", ">=", since(1)))),
    count(query(devices, where("lastActiveAt", ">=", since(7)))), count(query(devices, where("lastActiveAt", ">=", since(30)))),
    count(query(devices, where("premium", "==", true))), count(users), count(query(users, where("lastActiveAt", ">=", since(30)))),
    count(query(purchases, where("createdAt", ">=", since(30)))), count(query(purchases, where("refundedAt", ">=", since(30)))),
    count(query(collection(db, "tickets"), where("status", "==", "open"))),
    getDocs(query(purchases, where("entitled", "==", true))),
  ]);
  // Revenue estimate from active subscriptions (list prices; Play's 15% fee and taxes not deducted).
  const PRICE = { IN: { monthly: 149, yearly: 899 / 12, cur: "₹" }, INTL: { monthly: 4.99, yearly: 39.99 / 12, cur: "$" } };
  const mrr = { IN: 0, INTL: 0 };
  let trials = 0, paying = 0, yearly = 0;
  activeSubs.docs.forEach((d) => {
    const s = d.data();
    if (s.basePlanId === "yearly") yearly++;
    if (s.inTrial) { trials++; return; }
    paying++;
    const r = s.regionCode === "IN" ? "IN" : "INTL";
    mrr[r] += PRICE[r][s.basePlanId === "yearly" ? "yearly" : "monthly"];
  });
  const kpi = (v, l, s = "") => `<div class="kpi"><div class="v">${v}</div><div class="l">${l}</div>${s ? `<div class="s">${s}</div>` : ""}</div>`;
  $("kpis").innerHTML = [
    kpi(num(phones), "Senior phones", "all time"),
    kpi(num(dau), "Active today", `${pct(dau, mau)}% of monthly (stickiness)`),
    kpi(num(wau), "Active this week"),
    kpi(num(mau), "Active this month", `${pct(mau, phones)}% of all phones`),
    kpi(num(paying), "Paying subscribers", `${num(trials)} in free trial · ${pct(yearly, activeSubs.size)}% yearly`),
    kpi(`₹${num(Math.round(mrr.IN))} + $${num(Math.round(mrr.INTL))}`, "Monthly recurring revenue", "estimate, before Google's 15%"),
    kpi(num(premiumPhones), "Premium phones", `${pct(premiumPhones, phones)}% of all phones`),
    kpi(num(fam), "Family accounts", `${num(famMau)} active this month`),
    kpi(num(sales30), "New subscriptions (30 days)", `${num(refunds30)} refunded`),
    kpi(num(open), "Open tickets"),
  ].join("");
  $("updated").textContent = "Live · " + new Date().toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });

  const stats = (await getDocs(query(collection(db, "stats"), orderBy("date", "desc"), limit(30)))).docs.map((d) => d.data()).reverse();
  lineChart($("chart-dau"), stats.map((s) => ({ x: s.date, y: s.seniorPhones?.dau || 0 })), "#0F4C4A");
  barChart($("chart-sales"), stats.map((s) => ({ x: s.date, a: s.purchases?.new || 0, b: s.purchases?.refunds || 0 })));
}

function lineChart(el, pts, color) {
  if (pts.length < 2) { el.innerHTML = `<div class="empty">History appears after a couple of nights (the dailyStats job runs at 00:30 IST).</div>`; return; }
  const W = 700, H = 180, P = 24, max = Math.max(1, ...pts.map((p) => p.y));
  const x = (i) => P + (i * (W - 2 * P)) / (pts.length - 1), y = (v) => H - P - (v / max) * (H - 2 * P);
  const d = pts.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p.y).toFixed(1)}`).join("");
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Line chart">
    <line x1="${P}" x2="${W - P}" y1="${H - P}" y2="${H - P}" stroke="#D3D9D0"/>
    <path d="${d}" fill="none" stroke="${color}" stroke-width="2.5"/>
    ${pts.map((p, i) => `<circle cx="${x(i)}" cy="${y(p.y)}" r="3" fill="${color}"><title>${p.x}: ${p.y}</title></circle>`).join("")}
    <text x="${P}" y="14" font-size="12" fill="#4A5754">max ${max}</text>
    <text x="${P}" y="${H - 6}" font-size="11" fill="#4A5754">${pts[0].x}</text>
    <text x="${W - P}" y="${H - 6}" font-size="11" fill="#4A5754" text-anchor="end">${pts.at(-1).x}</text></svg>`;
}

function barChart(el, pts) {
  if (!pts.length) { el.innerHTML = `<div class="empty">No sales history yet.</div>`; return; }
  const W = 700, H = 180, P = 24, max = Math.max(1, ...pts.map((p) => p.a + p.b));
  const bw = (W - 2 * P) / pts.length;
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Purchases and refunds per day">
    ${pts.map((p, i) => {
      const ha = (p.a / max) * (H - 2 * P), hb = (p.b / max) * (H - 2 * P), x = P + i * bw + 2;
      return `<rect x="${x}" y="${H - P - ha}" width="${bw - 4}" height="${ha}" fill="#0F4C4A" rx="2"><title>${p.x}: ${p.a} purchases</title></rect>
              <rect x="${x}" y="${H - P - ha - hb}" width="${bw - 4}" height="${hb}" fill="#C2342B" rx="2"><title>${p.x}: ${p.b} refunds</title></rect>`;
    }).join("")}
    <text x="${P}" y="14" font-size="12" fill="#4A5754">max ${max}/day · teal = purchases, red = refunds</text></svg>`;
}

/* ---------------- funnel & usage ---------------- */
async function loadFunnel() {
  const devices = collection(db, "devices");
  const steps = [
    ["Installed", null],
    ["Made home screen", "funnel.setHome"],
    ["Added a contact", "funnel.addedContact"],
    ["Linked family", "funnel.linkedFamily"],
    ["Added a medicine", "funnel.addedMedicine"],
    ["Tested SOS", "funnel.testedSos"],
    ["Premium (trial or paid)", "premium"],
  ];
  const counts = await Promise.all(steps.map(([, f]) => count(f ? query(devices, where(f, "==", true)) : devices)));
  let worst = -1, worstDrop = 0;
  counts.forEach((c, i) => { if (i && counts[i - 1]) { const drop = 1 - c / counts[i - 1]; if (drop > worstDrop) { worstDrop = drop; worst = i; } } });
  $("funnel").innerHTML = steps.map(([label], i) => {
    const c = counts[i], w = pct(c, counts[0]);
    const drop = i && counts[i - 1] ? Math.round((1 - c / counts[i - 1]) * 100) : null;
    return `<div class="funnel-row ${i === worst ? "worst" : ""}"><div>${label}</div>
      <div class="funnel-bar"><i style="width:${w}%"></i></div>
      <div class="pct">${num(c)} · ${w}%${drop !== null ? `<span class="drop">−${drop}% from previous</span>` : ""}</div></div>`;
  }).join("");

  const buckets = [[1, "1+ days"], [3, "3+ days"], [7, "7+ days"], [30, "30+ days"], [90, "90+ days"]];
  const [total, avg, ...bc] = await Promise.all([
    count(devices),
    getAggregateFromServer(devices, { a: average("activeDays") }).then((r) => r.data().a || 0),
    ...buckets.map(([d]) => count(query(devices, where("activeDays", ">=", d)))),
  ]);
  $("usage").innerHTML = `<p>Average: <b>${avg.toFixed(1)} active days</b> per phone.</p>` + buckets.map(([, label], i) => `
    <div class="funnel-row"><div>${label}</div><div class="funnel-bar"><i style="width:${pct(bc[i], total)}%;background:#B35A0F"></i></div>
    <div class="pct">${num(bc[i])} · ${pct(bc[i], total)}%</div></div>`).join("");
}

/* ---------------- users ---------------- */
const pages = {};
async function pageInto(key, q, render, tableId, headers) {
  const p = pages[key] || (pages[key] = { last: null, rows: [] });
  const snap = await getDocs(p.last ? query(q, startAfter(p.last)) : q);
  if (snap.docs.length) p.last = snap.docs.at(-1);
  p.rows.push(...snap.docs.map((d) => render(d.id, d.data())));
  $(tableId).innerHTML = `<thead><tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${p.rows.join("") || `<tr><td colspan="${headers.length}" class="muted">Nothing yet.</td></tr>`}</tbody>`;
}
const plan = (premium) => `<span class="pill ${premium ? "premium" : "free"}">${premium ? "Unlocked" : "Free"}</span>`;
const flags = (f = {}) => `<span class="flags" title="home · contact · family · medicine · SOS">${["setHome", "addedContact", "linkedFamily", "addedMedicine", "testedSos"].map((k) => `<span class="${f[k] ? "on" : ""}"></span>`).join("")}</span>`;

function phoneRow(id, d) {
  return `<tr><td>${esc(d.name || "–")}</td><td>${plan(d.premium)}</td><td>${fmtAgo(d.lastActiveAt)}</td><td>${num(d.activeDays)}</td>
    <td>${fmtDate(d.createdAt)}</td><td>${(d.family || []).length}</td><td>${flags(d.funnel)}</td><td>${esc(d.appVersion || "")}</td>
    <td>${esc(d.model || "")}</td><td>${esc(d.locale || "")}</td><td class="muted">${id.slice(0, 8)}</td></tr>`;
}
function familyRow(id, d) {
  return `<tr><td>${esc(d.email || "–")}</td><td>${esc(d.displayName || "")}</td><td>${plan(d.premium)}</td><td>${fmtAgo(d.lastActiveAt)}</td>
    <td>${fmtDate(d.createdAt)}</td><td>${esc(d.appVersion || "")}</td><td class="muted">${id.slice(0, 8)}</td></tr>`;
}
const phoneHeaders = ["Name", "Plan", "Last active", "Active days", "Installed", "Family", "Setup", "Version", "Phone", "Lang", "ID"];
const familyHeaders = ["Email", "Name", "Plan", "Last active", "Joined", "Version", "UID"];

function loadUsers() {
  const phonesQ = query(collection(db, "devices"), orderBy("lastActiveAt", "desc"), limit(50));
  const familyQ = query(collection(db, "users"), orderBy("createdAt", "desc"), limit(50));
  pageInto("phones", phonesQ, phoneRow, "phones", phoneHeaders);
  pageInto("family", familyQ, familyRow, "family", familyHeaders);
  $("phones-more").onclick = () => pageInto("phones", phonesQ, phoneRow, "phones", phoneHeaders);
  $("family-more").onclick = () => pageInto("family", familyQ, familyRow, "family", familyHeaders);
  $("user-search").onsubmit = async (e) => {
    e.preventDefault();
    const q = new FormData(e.target).get("q").trim().toLowerCase();
    if (!q) return;
    const snap = await getDocs(query(collection(db, "users"), where("email", "==", q)));
    pages.family = { last: null, rows: snap.docs.map((d) => familyRow(d.id, d.data())) };
    $("family").innerHTML = `<thead><tr>${familyHeaders.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${pages.family.rows.join("") || `<tr><td colspan="7" class="muted">No family account with that email.</td></tr>`}</tbody>`;
  };
}

/* ---------------- purchases ---------------- */
function loadPurchases() {
  const q = query(collection(db, "subscriptions"), orderBy("createdAt", "desc"), limit(50));
  const headers = ["Started", "Email", "Plan", "Status", "Renews / ends", "Auto-renew", "Country", "Phones", "Last order"];
  const status = (d) => {
    if (d.state === "refunded") return ["refunded", "Refunded"];
    if (!d.entitled) return ["free", d.state === "expired" ? "Expired" : esc(d.state || "Ended")];
    if (d.inTrial) return ["premium", "Free trial"];
    if (d.state === "in_grace_period") return ["refunded", "Payment issue"];
    if (d.state === "canceled") return ["free", "Cancelled (still active)"];
    return ["active", "Active"];
  };
  const row = (id, d) => {
    const [cls, label] = status(d);
    return `<tr><td>${fmtDate(d.createdAt)}</td><td>${esc(d.email || "–")}</td><td>${esc(d.basePlanId || "")}</td>
    <td><span class="pill ${cls}">${label}</span></td><td>${fmtDate(d.expiryTime)}</td><td>${d.autoRenew ? "On" : "Off"}</td>
    <td>${esc(d.regionCode || "")}</td><td>${(d.deviceIds || []).filter((x) => x !== "-").length}</td><td class="muted">${esc(d.latestOrderId || "")}</td></tr>`;
  };
  pageInto("purchases", q, row, "purchases", headers);
  $("purchases-more").onclick = () => pageInto("purchases", q, row, "purchases", headers);
}

/* ---------------- tickets ---------------- */
let ticketStatus = "open", unsubList = null, unsubThread = null, currentTicket = null;

function watchOpenTickets() {
  onSnapshot(query(collection(db, "tickets"), where("status", "==", "open")), (s) => {
    $("open-badge").hidden = !s.size;
    $("open-badge").textContent = s.size;
  });
}

function loadTickets() {
  document.querySelectorAll("#ticket-filter button").forEach((b) => (b.onclick = () => {
    document.querySelectorAll("#ticket-filter button").forEach((x) => x.classList.toggle("on", x === b));
    ticketStatus = b.dataset.status;
    listTickets();
  }));
  listTickets();
}

function listTickets() {
  unsubList?.();
  unsubList = onSnapshot(query(collection(db, "tickets"), where("status", "==", ticketStatus), orderBy("updatedAt", "desc"), limit(100)), (snap) => {
    $("ticket-list").innerHTML = snap.docs.map((d) => {
      const t = d.data();
      return `<li data-id="${d.id}" class="${currentTicket === d.id ? "on" : ""}"><div class="t">${esc(t.subject)}</div>
        <div class="m">${esc(t.number)} · ${esc(t.name)} · ${esc(t.category)} · ${fmtAgo(t.updatedAt)}</div></li>`;
    }).join("") || `<li class="muted">No ${ticketStatus.replaceAll("_", " ")} tickets.</li>`;
    $("ticket-list").querySelectorAll("li[data-id]").forEach((li) => (li.onclick = () => openTicket(li.dataset.id, snap.docs.find((d) => d.id === li.dataset.id).data())));
  });
}

const CANNED = {
  "Alarm fix": "On your parent's phone, open WarmHearth Settings (PIN) and tap \"Allow on-time medicine alarms\" and \"Allow full-screen alerts\". On Xiaomi/Redmi/Realme/Oppo/Vivo phones also set Battery → No restrictions for WarmHearth.",
  "Restore purchase": "Please open the unlock screen and tap \"Already bought? Restore\" while signed in with the Google account you paid with. If it still doesn't show, reply with your order ID (starts with GPA.).",
  "Refund done": "We've refunded your order through Google Play. It usually reaches your account in 3–10 working days. The free features, including SOS, keep working.",
  "Deletion done": "We've deleted your WarmHearth account and the data linked to it, as requested.",
};

function openTicket(id, t) {
  currentTicket = id;
  document.querySelectorAll("#ticket-list li").forEach((li) => li.classList.toggle("on", li.dataset.id === id));
  unsubThread?.();
  $("ticket-view").innerHTML = `<header class="tab-head" style="margin-bottom:.5rem"><div><h2 style="margin:0">${esc(t.subject)}</h2>
      <div class="muted">${esc(t.number)} · ${esc(t.name)} &lt;<a href="mailto:${esc(t.email)}">${esc(t.email)}</a>&gt; · ${esc(t.category)} · ${esc(t.source)}${t.appVersion ? " · v" + esc(t.appVersion) : ""}</div></div>
      <div class="row"><button class="btn sm ghost" id="t-close" style="margin:0">${t.status === "closed" ? "Reopen" : "Close ticket"}</button></div></header>
    <div id="thread"></div>
    <form class="reply" id="reply"><textarea name="text" placeholder="Write a reply. It's emailed to the user${t.uid ? " and shown in their app" : ""}." required></textarea>
      <div class="row"><button class="btn">Send reply</button><label class="muted"><input type="checkbox" name="close"> Close after sending</label></div>
      <div class="canned">${Object.keys(CANNED).map((k) => `<button type="button" data-k="${esc(k)}">${esc(k)}</button>`).join("")}</div></form>`;

  unsubThread = onSnapshot(query(collection(db, "tickets", id, "messages"), orderBy("createdAt")), (snap) => {
    $("thread").innerHTML = snap.docs.map((d) => {
      const m = d.data();
      return `<div class="msg ${m.from}">${esc(m.text)}<small>${m.from === "admin" ? esc(m.authorEmail || "Support") : esc(t.name)} · ${m.createdAt?.toDate?.().toLocaleString("en-IN") || "sending…"}</small></div>`;
    }).join("");
  });

  $("t-close").onclick = () => updateDoc(doc(db, "tickets", id), { status: t.status === "closed" ? "open" : "closed", updatedAt: serverTimestamp() });
  $("reply").querySelectorAll(".canned button").forEach((b) => (b.onclick = () => {
    const ta = $("reply").elements.text;
    ta.value = (ta.value ? ta.value + "\n\n" : "") + CANNED[b.dataset.k];
    ta.focus();
  }));
  $("reply").onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    const text = String(f.get("text")).trim();
    if (!text) return;
    e.target.querySelector("button.btn").disabled = true;
    // "close" is applied by the onTicketMessage function after the email goes out.
    await addDoc(collection(db, "tickets", id, "messages"), { from: "admin", text, close: !!f.get("close"), createdAt: serverTimestamp(), authorEmail: auth.currentUser.email });
    e.target.reset();
    e.target.querySelector("button.btn").disabled = false;
  };
}
