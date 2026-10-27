// Everything brand- and company-specific lives here, so a second Lusane app can reuse the backend.
module.exports = {
  APP_NAME: "WarmHearth",
  PACKAGE_NAME: "com.lusanetech.warmhearth",
  // Google Play subscription with base plans "monthly" and "yearly", 7-day trial offer "free-trial-7d".
  SUBSCRIPTION_ID: "warmhearth_premium",
  // Display-only reference copy of the Play prices (Play Console is the source of truth and shows
  // the local price to each buyer). Keep in step with docs/OPERATIONS.md → "Prices by country".
  PRICES: {
    US: { monthly: "US$4.99", yearly: "US$39.99" },
    GB: { monthly: "£4.49", yearly: "£34.99" },
    EU: { monthly: "€4.99", yearly: "€39.99" },
    IN: { monthly: "₹149", yearly: "₹899" },
    CA: { monthly: "C$6.49", yearly: "C$49.99" },
    AU: { monthly: "A$7.49", yearly: "A$59.99" },
    default: { monthly: "US$4.99", yearly: "US$39.99" },
  },
  SITE_URL: "https://warmhearth.lusanetech.com",
  PLAY_URL: "https://play.google.com/store/apps/details?id=com.lusanetech.warmhearth",
  BRAND_COLOR: "#0F4C4A",

  COMPANY: {
    name: "Lusane Technologies",
    // Sole proprietorship. Fill in exactly as on your registration / GST certificate.
    owner: "TODO: proprietor's full legal name",
    address: "TODO: registered business address, Tripura, India",
    website: "https://lusanetech.com",
  },

  // Zoho aliases on your existing mailbox (no new mailboxes). See docs/OPERATIONS.md.
  EMAIL: {
    support: { address: "support@lusanetech.com", name: "WarmHearth Support" },
    billing: { address: "billing@lusanetech.com", name: "WarmHearth Billing" },
    // Where "new ticket" alerts go. Usually the same Zoho inbox the aliases deliver to.
    adminInbox: "support@lusanetech.com",
  },

  REGION: "asia-south1",
  TIMEZONE: "Asia/Kolkata",
};
