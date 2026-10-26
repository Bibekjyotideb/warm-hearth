const test = require("node:test");
const assert = require("node:assert");
const { evaluate, inWindow } = require("../src/safetyRules");

// 2026-10-26 14:00 IST
const NOW = new Date("2026-10-26T08:30:00Z");
const H = 3_600_000;
const base = (over = {}) => ({
  name: "Maa",
  family: ["u1"],
  lastCheckInAt: NOW.getTime() - 2 * H,
  status: { tz: "Asia/Kolkata", reportedAt: NOW.getTime() - 10 * 60_000, lastActiveAt: NOW.getTime() - H, battery: 80, charging: false, ringer: "normal", ringVolume: 70 },
  ...over,
});
const types = (r) => r.alerts.map((a) => a.type).sort();

test("all fine: no alerts", () => {
  assert.deepEqual(types(evaluate(base(), {}, NOW)), []);
});

test("sleep window crossing midnight", () => {
  assert.ok(inWindow("23:30", "22:00", "07:00"));
  assert.ok(inWindow("03:00", "22:00", "07:00"));
  assert.ok(!inWindow("14:00", "22:00", "07:00"));
  assert.ok(inWindow("14:00", "13:00", "15:00"));
});

test("missed check-in alerts once per day", () => {
  const d = base({ lastCheckInAt: NOW.getTime() - 30 * H });
  const r1 = evaluate(d, {}, NOW);
  assert.deepEqual(types(r1), ["no_checkin"]);
  assert.match(r1.alerts[0].message, /Maa/);
  assert.ok(r1.alerts[0].message_hi.length > 0);
  assert.deepEqual(types(evaluate(d, r1.state, NOW)), []);
});

test("check-in not due before the agreed time", () => {
  const morning = new Date("2026-10-26T03:00:00Z"); // 08:30 IST
  const d = base({ lastCheckInAt: null });
  d.status.reportedAt = morning.getTime(); d.status.lastActiveAt = morning.getTime();
  assert.deepEqual(types(evaluate(d, {}, morning)), []);
});

test("family can turn check-in off", () => {
  const d = base({ lastCheckInAt: null, settings: { checkIn: { enabled: false } } });
  assert.deepEqual(types(evaluate(d, {}, NOW)), []);
});

test("inactivity uses the family's threshold and re-arms on new activity", () => {
  const d = base({ settings: { inactiveHours: 6 } });
  d.status.lastActiveAt = NOW.getTime() - 7 * H;
  const r = evaluate(d, {}, NOW);
  assert.deepEqual(types(r), ["inactive"]);
  assert.deepEqual(types(evaluate(d, r.state, NOW)), []);
  d.status.lastActiveAt = NOW.getTime() - 6.5 * H - 1; // different activity timestamp → new episode
  assert.deepEqual(types(evaluate(d, r.state, NOW)), ["inactive"]);
});

test("no inactivity alert during sleep hours", () => {
  const night = new Date("2026-10-26T20:30:00Z"); // 02:00 IST
  const d = base({ lastCheckInAt: night.getTime() - H });
  d.status.reportedAt = night.getTime(); d.status.lastActiveAt = night.getTime() - 13 * H;
  assert.deepEqual(types(evaluate(d, {}, night)), []);
});

test("low battery alerts once, re-arms after charging", () => {
  const d = base();
  d.status.battery = 12;
  const r1 = evaluate(d, {}, NOW);
  assert.deepEqual(types(r1), ["low_battery"]);
  assert.deepEqual(types(evaluate(d, r1.state, NOW)), []);
  d.status.charging = true;
  const r2 = evaluate(d, r1.state, NOW);
  assert.equal(r2.state.sent.battery, undefined);
});

test("silent for 30+ minutes alerts once per episode", () => {
  const d = base();
  d.status.ringer = "silent"; d.status.silentSince = NOW.getTime() - 45 * 60_000;
  const r = evaluate(d, {}, NOW);
  assert.deepEqual(types(r), ["silent"]);
  assert.deepEqual(types(evaluate(d, r.state, NOW)), []);
  d.status.silentSince = NOW.getTime() - 5 * 60_000;
  assert.deepEqual(types(evaluate(d, r.state, NOW)), []); // too recent
});

test("volume 0 counts as silent", () => {
  const d = base();
  d.status.ringVolume = 0; d.status.silentSince = NOW.getTime() - H;
  assert.deepEqual(types(evaluate(d, {}, NOW)), ["silent"]);
});

test("offline phone: one alert and nothing else piled on", () => {
  const d = base({ lastCheckInAt: null });
  d.status.reportedAt = NOW.getTime() - 7 * H; d.status.battery = 5; d.status.lastActiveAt = NOW.getTime() - 20 * H;
  const r = evaluate(d, {}, NOW);
  assert.deepEqual(types(r), ["offline"]);
  assert.match(r.alerts[0].message, /run out/);
  assert.deepEqual(types(evaluate(d, r.state, NOW)), []);
});

test("bad time zone falls back safely", () => {
  const d = base();
  d.status.tz = "Not/AZone";
  assert.doesNotThrow(() => evaluate(d, {}, NOW));
});
