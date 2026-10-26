// Pure rules for family safety alerts. No Firebase here, so it is unit-tested (test/safety.test.js).
//
// Input: the senior's device document (status written by the phone every ~30 min and whenever the
// ringer changes; settings written by the family) and the server-only alert state.
// Output: alerts to send now, plus the updated state so each problem alerts once, not every 15 min.

const DEFAULTS = {
  checkIn: { enabled: true, by: "11:00" },
  sleep: { start: "22:00", end: "07:00" },
  inactiveHours: 12,
  alerts: { checkin: true, inactive: true, battery: true, silent: true, offline: true },
};

const OFFLINE_HOURS = 6;      // status reports every 30 min; 6 h of silence means off / no network
const SILENT_MINUTES = 30;    // ignore a quick "silent for a meeting"
const LOW_BATTERY = 15;
const BATTERY_RESET = 40;     // re-arm the battery alert once charged past this

const H = 3_600_000;

function settingsOf(device) {
  const s = device.settings || {};
  return {
    checkIn: { ...DEFAULTS.checkIn, ...(s.checkIn || {}) },
    sleep: { ...DEFAULTS.sleep, ...(s.sleep || {}) },
    inactiveHours: Number(s.inactiveHours) > 0 ? Number(s.inactiveHours) : DEFAULTS.inactiveHours,
    alerts: { ...DEFAULTS.alerts, ...(s.alerts || {}) },
  };
}

/** Local "HH:MM" and "YYYY-MM-DD" in the phone's time zone. */
function localClock(now, tz) {
  const zone = typeof tz === "string" && tz ? tz : "Asia/Kolkata";
  try {
    const hm = new Intl.DateTimeFormat("en-GB", { timeZone: zone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(now);
    const day = new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
    return { hm, day };
  } catch {
    return localClock(now, "Asia/Kolkata");
  }
}

const mins = (hm) => {
  const [h, m] = String(hm).split(":").map(Number);
  return (h || 0) * 60 + (m || 0);
};

/** True if hm is inside [start, end), handling windows that cross midnight (22:00–07:00). */
function inWindow(hm, start, end) {
  const t = mins(hm), a = mins(start), b = mins(end);
  return a <= b ? t >= a && t < b : t >= a || t < b;
}

const ms = (v) => (v == null ? null : typeof v === "number" ? v : typeof v.toMillis === "function" ? v.toMillis() : Date.parse(v) || null);

/**
 * @param device  devices/{id} data: name, status{...}, settings{...}, lastCheckInAt
 * @param state   alertState/{id} data: { sent: { key: value } }
 * @param now     Date
 * @returns { alerts: [{ type, message, message_hi }], state }
 */
function evaluate(device, state = {}, now = new Date()) {
  const s = settingsOf(device);
  const st = device.status || {};
  const sent = { ...(state.sent || {}) };
  const out = [];
  const name = (device.name || "").trim() || "Your parent";
  const nameHi = (device.name || "").trim() || "आपके परिजन";
  const t = now.getTime();
  const { hm, day } = localClock(now, st.tz);
  const asleep = inWindow(hm, s.sleep.start, s.sleep.end);

  const reportedAt = ms(st.reportedAt);
  const lastActive = ms(st.lastActiveAt);

  // 1. Phone stopped reporting: switched off, battery dead, or no internet.
  if (s.alerts.offline && reportedAt && t - reportedAt > OFFLINE_HOURS * H) {
    if (sent.offline !== reportedAt) {
      const dead = typeof st.battery === "number" && st.battery <= 10;
      out.push({
        type: "offline",
        message: dead
          ? `${name}'s phone hasn't been online for ${OFFLINE_HOURS} hours. Its battery was at ${st.battery}%, so it has probably run out.`
          : `${name}'s phone hasn't been online for ${OFFLINE_HOURS} hours. It may be switched off or without internet.`,
        message_hi: dead
          ? `${nameHi} का फ़ोन ${OFFLINE_HOURS} घंटे से ऑनलाइन नहीं है। बैटरी ${st.battery}% थी, शायद ख़त्म हो गई है।`
          : `${nameHi} का फ़ोन ${OFFLINE_HOURS} घंटे से ऑनलाइन नहीं है। शायद बंद है या इंटरनेट नहीं है।`,
      });
      sent.offline = reportedAt;
    }
    // Everything below depends on fresh status; don't pile on more alerts about a dead phone.
    return { alerts: out, state: { sent } };
  }

  // 2. Didn't say "I'm OK" by the agreed time today.
  if (s.alerts.checkin && s.checkIn.enabled && mins(hm) >= mins(s.checkIn.by)) {
    const last = ms(device.lastCheckInAt);
    const checkedToday = last && localClock(new Date(last), st.tz).day === day;
    if (!checkedToday && sent.checkin !== day) {
      out.push({
        type: "no_checkin",
        message: `${name} hasn't tapped "I'm OK" yet today (expected by ${s.checkIn.by}). Maybe give them a call.`,
        message_hi: `${nameHi} ने आज अभी तक "मैं ठीक हूँ" नहीं दबाया (${s.checkIn.by} तक)। एक बार फ़ोन कर लें।`,
      });
      sent.checkin = day;
    }
  }

  // 3. Phone not touched for N hours (not counted while they sleep).
  if (s.alerts.inactive && lastActive && !asleep && t - lastActive > s.inactiveHours * H) {
    if (sent.inactive !== lastActive) {
      out.push({
        type: "inactive",
        message: `${name}'s phone hasn't been used for over ${s.inactiveHours} hours. Please check on them.`,
        message_hi: `${nameHi} का फ़ोन ${s.inactiveHours} घंटे से ज़्यादा समय से इस्तेमाल नहीं हुआ। कृपया हालचाल लें।`,
      });
      sent.inactive = lastActive;
    }
  }

  // 4. Battery low and not charging. Re-arms after charging.
  if (typeof st.battery === "number") {
    if (st.charging || st.battery >= BATTERY_RESET) delete sent.battery;
    else if (s.alerts.battery && st.battery <= LOW_BATTERY && !sent.battery) {
      out.push({
        type: "low_battery",
        message: `${name}'s phone battery is at ${st.battery}% and not charging.`,
        message_hi: `${nameHi} के फ़ोन की बैटरी ${st.battery}% है और चार्ज नहीं हो रही।`,
      });
      sent.battery = t;
    }
  }

  // 5. Ringer silent / vibrate / volume 0 for a while, outside sleep hours: calls won't be heard.
  const quiet = st.ringer && (st.ringer !== "normal" || st.ringVolume === 0 || st.dnd === true);
  const silentSince = ms(st.silentSince);
  if (!quiet) delete sent.silent;
  else if (s.alerts.silent && !asleep && silentSince && t - silentSince > SILENT_MINUTES * 60_000 && sent.silent !== silentSince) {
    out.push({
      type: "silent",
      message: `${name}'s phone is on silent, so they won't hear your calls. Open WarmHearth to turn the ringer back on.`,
      message_hi: `${nameHi} का फ़ोन साइलेंट पर है, आपकी कॉल सुनाई नहीं देगी। रिंगर वापस चालू करने के लिए WarmHearth खोलें।`,
    });
    sent.silent = silentSince;
  }

  return { alerts: out, state: { sent } };
}

module.exports = { evaluate, inWindow, localClock, settingsOf, DEFAULTS };
