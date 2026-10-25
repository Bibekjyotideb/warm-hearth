// Renders every email template to out/*.html so you can open them in a browser.
const fs = require("fs");
const path = require("path");
const { templates } = require("../src/emails/templates");
const sample = {
  subscriptionStarted: { name: "Priya", plan: "Yearly", inTrial: true, nextDate: "1 November 2026", orderId: "GPA.3345-1122-6789-01234" },
  trialEnding: { plan: "Yearly", chargeDate: "1 November 2026" },
  subscriptionCanceled: { endDate: "1 November 2027" },
  paymentProblem: { onHold: false },
  refundProcessed: { name: "Priya", orderId: "GPA.3345-1122-6789-01234" },
  ticketReceived: { name: "Priya", number: "WH-7K3QP9", subject: "Medicine alarm didn't ring", message: "My father's 8am alarm didn't ring today.\nPhone: Redmi Note 12" },
  ticketReply: { name: "Priya", number: "WH-7K3QP9", reply: "Thanks for the details! Please open Settings → Allow on-time medicine alarms on your father's phone.\n\nThat fixes it on Redmi phones." },
  welcome: { name: "Priya" },
  accountDeleted: { name: "Priya" },
  adminNewTicket: { number: "WH-7K3QP9", name: "Priya", email: "priya@example.com", category: "problem", subject: "Alarm", message: "Didn't ring", source: "app", appVersion: "1.0.0" },
};
const out = path.join(__dirname, "..", "out");
fs.mkdirSync(out, { recursive: true });
for (const [k, fn] of Object.entries(templates)) {
  const t = fn(sample[k]);
  fs.writeFileSync(path.join(out, `${k}.html`), t.html);
  console.log(`${k.padEnd(18)} ${t.subject}`);
}
console.log(`\nOpen ${out}`);
