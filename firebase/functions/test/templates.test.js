const test = require("node:test");
const assert = require("node:assert");
const { templates, esc } = require("../src/emails/templates");

test("every template returns subject, html and text", () => {
  for (const [name, fn] of Object.entries(templates)) {
    const t = fn({ name: "A", orderId: "O1", plan: "Yearly", inTrial: true, nextDate: "today", chargeDate: "today", endDate: "today", onHold: true, number: "WH-1", subject: "S", message: "M", reply: "R", email: "a@b.co", category: "question", source: "web" });
    assert.ok(t.subject && t.html.includes("<html") && t.text.length > 10, name);
  }
});

test("user input is escaped in emails", () => {
  const t = templates.ticketReceived({ name: "<script>", number: "WH-1", subject: "<b>x</b>", message: "<img src=x onerror=alert(1)>" });
  assert.ok(!t.html.includes("<script>") && !t.html.includes("<img src=x"));
  assert.equal(esc(`"'<>&`), "&quot;&#39;&lt;&gt;&amp;");
});
