// Sends email through your existing Zoho mailbox over SMTP.
// The "From" is an alias (support@ / billing@) of that mailbox, so no extra mailboxes are needed.
//
// Secrets (set once):
//   firebase functions:secrets:set SMTP_USER   -> your Zoho login, e.g. you@lusanetech.com
//   firebase functions:secrets:set SMTP_PASS   -> a Zoho *app-specific* password (not your login password)
// Param SMTP_HOST defaults to smtp.zoho.in (Indian data centre). Use smtp.zoho.com if your Zoho is .com.

const nodemailer = require("nodemailer");
const { defineSecret, defineString } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const cfg = require("./config");

const SMTP_USER = defineSecret("SMTP_USER");
const SMTP_PASS = defineSecret("SMTP_PASS");
const SMTP_HOST = defineString("SMTP_HOST", { default: "smtp.zoho.in" });

let transport;
function getTransport() {
  if (!transport) {
    transport = nodemailer.createTransport({
      host: SMTP_HOST.value(),
      port: 465,
      secure: true,
      auth: { user: SMTP_USER.value(), pass: SMTP_PASS.value() },
    });
  }
  return transport;
}

/**
 * @param {{to: string, from: "support"|"billing", subject: string, html: string, text: string, replyTo?: string, headers?: object}} m
 * Never throws: an email failure must not fail a purchase or a ticket.
 */
async function sendMail(m) {
  if (!m.to) return false;
  const sender = cfg.EMAIL[m.from] || cfg.EMAIL.support;
  try {
    await getTransport().sendMail({
      from: { name: sender.name, address: sender.address },
      to: m.to,
      replyTo: m.replyTo || sender.address,
      subject: m.subject,
      html: m.html,
      text: m.text,
      headers: m.headers,
    });
    return true;
  } catch (e) {
    logger.error("Email failed", { to: m.to, subject: m.subject, error: e.message });
    return false;
  }
}

module.exports = { sendMail, SMTP_SECRETS: [SMTP_USER, SMTP_PASS] };
