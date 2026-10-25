// Branded transactional emails.
//
// Decision: emails are branded WarmHearth (the product people know), with a Lusane Technologies
// footer (the legal seller). One shared layout means a future Lusane app only swaps config.js.
// Every email has a plain-text version too (better deliverability, better for screen readers).

const cfg = require("../config");

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const nl2br = (s) => esc(s).replace(/\n/g, "<br>");

function layout({ preheader, heading, bodyHtml, cta, footerNote }) {
  const c = cfg.COMPANY;
  const button = cta
    ? `<tr><td style="padding:8px 0 24px"><a href="${esc(cta.url)}" style="background:${cfg.BRAND_COLOR};color:#fff;text-decoration:none;font-weight:600;font-size:16px;padding:14px 24px;border-radius:12px;display:inline-block">${esc(cta.label)}</a></td></tr>`
    : "";
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><title>${esc(heading)}</title></head>
<body style="margin:0;background:#F6F7F2;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#1C1B1A">
<span style="display:none;max-height:0;overflow:hidden;opacity:0">${esc(preheader || "")}</span>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F6F7F2"><tr><td align="center" style="padding:24px 12px">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">
  <tr><td style="padding:8px 4px 16px;font-size:20px;font-weight:700;color:${cfg.BRAND_COLOR}">&#9832; ${esc(cfg.APP_NAME)}</td></tr>
  <tr><td style="background:#fff;border-radius:20px;padding:28px 24px;border:1px solid #DDE2D8">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
      <tr><td style="font-size:22px;font-weight:700;line-height:1.3;padding-bottom:12px">${esc(heading)}</td></tr>
      <tr><td style="font-size:16px;line-height:1.6;padding-bottom:16px">${bodyHtml}</td></tr>
      ${button}
      ${footerNote ? `<tr><td style="font-size:13px;line-height:1.5;color:#5B5550">${footerNote}</td></tr>` : ""}
    </table>
  </td></tr>
  <tr><td style="padding:20px 8px;font-size:12px;line-height:1.6;color:#7A726B;text-align:center">
    ${esc(cfg.APP_NAME)} is a product of ${esc(c.name)} &middot; ${esc(c.address)}<br>
    <a href="${cfg.SITE_URL}/privacy" style="color:#7A726B">Privacy</a> &middot;
    <a href="${cfg.SITE_URL}/terms" style="color:#7A726B">Terms</a> &middot;
    <a href="${cfg.SITE_URL}/refunds" style="color:#7A726B">Refunds</a> &middot;
    <a href="${cfg.SITE_URL}/support" style="color:#7A726B">Support</a>
  </td></tr>
</table></td></tr></table></body></html>`;
}

function textFooter() {
  const c = cfg.COMPANY;
  return `\n\n—\n${cfg.APP_NAME} is a product of ${c.name}, ${c.address}\nPrivacy: ${cfg.SITE_URL}/privacy · Terms: ${cfg.SITE_URL}/terms · Support: ${cfg.SITE_URL}/support`;
}

/** Each template returns {subject, html, text}. */
const templates = {
  subscriptionStarted({ name, plan, inTrial, nextDate, orderId }) {
    const subject = inTrial ? `Your ${cfg.APP_NAME} free trial has started` : `Welcome to ${cfg.APP_NAME} Premium`;
    const rows = [
      ["Plan", `${cfg.APP_NAME} Premium, ${plan.toLowerCase()}`],
      [inTrial ? "Free until" : "Renews on", nextDate],
      orderId ? ["Order ID", orderId] : null,
      ["Billed by", "Google Play"],
    ].filter(Boolean);
    const table = `<table role="presentation" cellpadding="0" cellspacing="0" style="width:100%;border-collapse:collapse;margin:8px 0 4px">${rows
      .map(([k, v]) => `<tr><td style="padding:8px 0;color:#4A5754;border-bottom:1px solid #E6EAE2">${esc(k)}</td><td style="padding:8px 0;text-align:right;font-weight:600;border-bottom:1px solid #E6EAE2">${esc(v)}</td></tr>`)
      .join("")}</table>`;
    const lead = inTrial
      ? `Your 7-day free trial is on. You won't be charged before <b>${esc(nextDate)}</b>, and we'll email you 2 days before. Cancel any time in Google Play.`
      : `Family remote setup, medicine reminders, fall detection, unlimited contacts and AI helpers are now on.`;
    return {
      subject,
      html: layout({
        preheader: inTrial ? `Free until ${nextDate}. Cancel any time.` : "Premium is on for your family.",
        heading: `Thank you${name ? ", " + name : ""}!`,
        bodyHtml: `${lead}${table}`,
        cta: { label: "Manage subscription", url: "https://play.google.com/store/account/subscriptions" },
        footerNote: `Payment receipts come from Google Play. Questions or refunds: reply to this email or see our <a href="${cfg.SITE_URL}/refunds" style="color:#4A5754">refund policy</a>.`,
      }),
      text: `Thank you${name ? ", " + name : ""}! ${inTrial ? `Your 7-day free trial is on. You won't be charged before ${nextDate}; we'll remind you 2 days before.` : "WarmHearth Premium is on."}\n\n${rows.map(([k, v]) => `${k}: ${v}`).join("\n")}\n\nManage or cancel: https://play.google.com/store/account/subscriptions${textFooter()}`,
    };
  },

  trialEnding({ plan, chargeDate }) {
    const subject = `Your ${cfg.APP_NAME} trial ends on ${chargeDate}`;
    return {
      subject,
      html: layout({
        preheader: `Your ${plan.toLowerCase()} plan starts on ${chargeDate} unless you cancel.`,
        heading: "Your free trial ends in 2 days",
        bodyHtml: `On <b>${esc(chargeDate)}</b> your ${esc(plan.toLowerCase())} plan starts and Google Play charges the price you saw when you started the trial. Nothing to do if you'd like to keep Premium.<br><br>Don't want it? Cancel before then in Google Play and you won't be charged. SOS and the big home screen stay free.`,
        cta: { label: "Manage subscription", url: "https://play.google.com/store/account/subscriptions" },
      }),
      text: `Your free trial ends on ${chargeDate}; your ${plan.toLowerCase()} plan then starts. To avoid the charge, cancel before then: https://play.google.com/store/account/subscriptions${textFooter()}`,
    };
  },

  subscriptionCanceled({ endDate }) {
    const subject = `Your ${cfg.APP_NAME} subscription was cancelled`;
    return {
      subject,
      html: layout({
        preheader: `Premium stays on until ${endDate}.`,
        heading: "Your subscription was cancelled",
        bodyHtml: `Premium keeps working until <b>${esc(endDate)}</b>. After that, the free features stay on: the big home screen, photo contacts and SOS with family alerts.<br><br>Changed your mind? You can resubscribe any time in Google Play.`,
        cta: { label: "Resubscribe", url: "https://play.google.com/store/account/subscriptions" },
        footerNote: "If something wasn't right, reply to this email. We read every one.",
      }),
      text: `Your subscription was cancelled. Premium keeps working until ${endDate}; the free features stay on after that.${textFooter()}`,
    };
  },

  paymentProblem({ onHold }) {
    const subject = onHold ? `${cfg.APP_NAME} Premium is paused: payment needed` : `There's a problem with your ${cfg.APP_NAME} payment`;
    return {
      subject,
      html: layout({
        preheader: "Update your payment method in Google Play to keep alerts working.",
        heading: onHold ? "Premium is paused" : "Your payment didn't go through",
        bodyHtml: `${onHold ? "Google Play couldn't renew your subscription, so Premium features are paused." : "Google Play couldn't take the renewal payment. Premium keeps working for a few days while it retries."}<br><br>Update your payment method to keep medicine reminders, missed-dose alerts and fall detection on. SOS keeps working either way.`,
        cta: { label: "Fix payment in Google Play", url: "https://play.google.com/store/account/subscriptions" },
      }),
      text: `${onHold ? "Premium is paused" : "Your renewal payment didn't go through"}. Update your payment method: https://play.google.com/store/account/subscriptions${textFooter()}`,
    };
  },

  refundProcessed({ name, orderId }) {
    const subject = `Your ${cfg.APP_NAME} refund`;
    return {
      subject,
      html: layout({
        preheader: "Your refund has been processed by Google Play.",
        heading: "Your refund has been processed",
        bodyHtml: `Hi${name ? " " + esc(name) : ""}, Google Play has refunded order <b>${esc(orderId)}</b>. It usually reaches your account in 3–10 working days, depending on your bank.<br><br>Premium has been switched off and the subscription won't renew. SOS, the big home screen and family alerts stay free.`,
        footerNote: "Something not right? Just reply to this email.",
      }),
      text: `Hi${name ? " " + name : ""}, Google Play has refunded order ${orderId}. It usually reaches your account in 3–10 working days.\nPremium features are now off; SOS and the home screen stay free.${textFooter()}`,
    };
  },

  ticketReceived({ name, number, subject: s, message }) {
    const subject = `[${number}] We got your message`;
    return {
      subject,
      html: layout({
        preheader: `Ticket ${number}: we usually reply within 24 hours.`,
        heading: `We got your message${name ? ", " + name : ""}`,
        bodyHtml: `Your ticket number is <b>${esc(number)}</b>. We usually reply within 24 hours (Monday–Saturday, IST).<br><br><div style="background:#F6F7F2;border-radius:12px;padding:12px 14px;color:#5B5550"><b>${esc(s)}</b><br>${nl2br(message)}</div>`,
        footerNote: "To add more details, simply reply to this email and keep the ticket number in the subject.",
      }),
      text: `Ticket ${number}: we got your message and usually reply within 24 hours.\n\n${s}\n${message}\n\nReply to this email to add details.${textFooter()}`,
    };
  },

  ticketReply({ name, number, reply }) {
    const subject = `[${number}] Reply from ${cfg.APP_NAME} Support`;
    return {
      subject,
      html: layout({
        preheader: reply.slice(0, 90),
        heading: `Hi${name ? " " + name : ""},`,
        bodyHtml: nl2br(reply),
        footerNote: `Ticket ${esc(number)}. Reply to this email if you need anything else.`,
      }),
      text: `Hi${name ? " " + name : ""},\n\n${reply}\n\nTicket ${number}. Reply to this email if you need anything else.${textFooter()}`,
    };
  },

  welcome({ name }) {
    const subject = `Welcome to ${cfg.APP_NAME}`;
    return {
      subject,
      html: layout({
        preheader: "Three steps to set up your parent's phone.",
        heading: `Welcome${name ? ", " + name : ""}!`,
        bodyHtml: `Thanks for looking after your family with ${cfg.APP_NAME}. Three steps:<ol style="padding-left:20px;margin:12px 0"><li>On your parent's phone, open <b>Settings → Link a family member's phone</b>.</li><li>Type the 8-letter code into your phone.</li><li>Add their important people and medicines from here.</li></ol>SOS alerts from their phone will now reach you instantly.`,
        cta: { label: "Setup guide", url: `${cfg.SITE_URL}/#how-it-works` },
      }),
      text: `Welcome${name ? ", " + name : ""}!\n1. On your parent's phone: Settings → Link a family member's phone.\n2. Type the 8-letter code into your phone.\n3. Add their people and medicines.\nSOS alerts will now reach you instantly.${textFooter()}`,
    };
  },

  accountDeleted({ name }) {
    const subject = `Your ${cfg.APP_NAME} account has been deleted`;
    return {
      subject,
      html: layout({
        preheader: "Your data has been deleted.",
        heading: "Your account has been deleted",
        bodyHtml: `Hi${name ? " " + esc(name) : ""}, as you asked, we deleted your ${cfg.APP_NAME} account and the data linked to it. If you had a subscription, please also cancel it in Google Play (Payments & subscriptions) so it doesn't renew.<br><br>We're sorry to see you go.`,
      }),
      text: `Hi${name ? " " + name : ""}, we deleted your ${cfg.APP_NAME} account and its data as requested.${textFooter()}`,
    };
  },

  adminNewTicket({ number, name, email, category, subject: s, message, source, appVersion }) {
    return {
      subject: `[${number}] New ${category} ticket: ${s}`,
      html: layout({
        heading: `New ticket ${number}`,
        bodyHtml: `<b>From:</b> ${esc(name)} &lt;${esc(email)}&gt;<br><b>Category:</b> ${esc(category)} · <b>Source:</b> ${esc(source)}${appVersion ? " · v" + esc(appVersion) : ""}<br><br><b>${esc(s)}</b><br>${nl2br(message)}`,
        cta: { label: "Open admin panel", url: `${cfg.SITE_URL}/admin/#tickets` },
      }),
      text: `New ticket ${number} from ${name} <${email}> (${category}, ${source})\n\n${s}\n${message}`,
    };
  },
};

module.exports = { templates, layout, esc };
