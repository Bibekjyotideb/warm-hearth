// Support and deletion forms → submitTicket Cloud Function.
import { initializeApp } from "firebase/app";
import { getFunctions, httpsCallable } from "firebase/functions";
import { firebaseConfig, functionsRegion } from "./firebase-config.js";

const form = document.getElementById("ticket-form");
const status = document.getElementById("form-status");
const submitTicket = httpsCallable(getFunctions(initializeApp(firebaseConfig), functionsRegion), "submitTicket");

function show(kind, text) {
  status.hidden = false;
  status.className = `form-status ${kind}`;
  status.textContent = text;
}

form?.addEventListener("submit", async (e) => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(form));
  if (form.dataset.category) data.category = form.dataset.category;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(data.email || "")) return show("err", "Enter a valid email address so we can reply.");
  if (!data.name || !(data.message || "").trim()) return show("err", "Fill in your name and message.");
  const button = form.querySelector("button[type=submit]");
  button.disabled = true;
  const label = button.textContent;
  button.textContent = "Sending…";
  try {
    const res = await submitTicket({ ...data, source: "web", locale: navigator.language });
    form.reset();
    show("ok", `Sent. Your ticket number is ${res.data.number}; we've emailed you a copy.`);
  } catch (err) {
    show("err", err?.message && !/internal/i.test(err.message) ? err.message : `Couldn't send right now. Email us at support@lusanetech.com instead.`);
  } finally {
    button.disabled = false;
    button.textContent = label;
  }
});
