// Play Store keyword scorer
// Usage:  node score.mjs            (all ideas, country from config)
//         node score.mjs seniors    (one idea)
// Output: out/<idea>.csv  and  out/summary.csv

import gplay from 'google-play-scraper';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const cfg = JSON.parse(fs.readFileSync(new URL('./seeds.json', import.meta.url)));
const { country = 'us', lang = 'en', expandSuggest = true, maxKeywordsPerIdea = 40 } = cfg.settings || {};
const only = process.argv[2];
const OUT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'out');
fs.mkdirSync(OUT, { recursive: true });

const sleep = ms => new Promise(r => setTimeout(r, ms));
const cache = new Map();

async function retry(fn, tries = 3) {
  for (let i = 0; i < tries; i++) {
    try { return await fn(); }
    catch (e) {
      if (i === tries - 1) throw e;
      await sleep(2000 * (i + 1)); // back off if Google throttles
    }
  }
}

async function suggest(term) {
  const k = 's:' + term;
  if (cache.has(k)) return cache.get(k);
  await sleep(350);
  const r = await retry(() => gplay.suggest({ term, country, lang })).catch(() => []);
  cache.set(k, r);
  return r;
}

async function topApps(term) {
  await sleep(600);
  const list = await retry(() => gplay.search({ term, num: 10, country, lang, fullDetail: true })).catch(() => []);
  return list;
}

// Expand a seed into long-tail keywords using Play autocomplete (seed + a..z)
async function expand(seed) {
  const set = new Set([seed.toLowerCase()]);
  (await suggest(seed)).forEach(s => set.add(s.toLowerCase()));
  if (expandSuggest) {
    for (const ch of 'abcdefghijklmnopqrstuvwxyz') {
      (await suggest(`${seed} ${ch}`)).forEach(s => set.add(s.toLowerCase()));
    }
  }
  return [...set];
}

// Traffic proxy (0-100): how few letters it takes for Play to suggest this keyword,
// and how high it appears. Popular searches surface early.
async function traffic(kw) {
  for (let len = Math.min(3, kw.length); len <= kw.length; len++) {
    const s = await suggest(kw.slice(0, len));
    const idx = s.indexOf(kw);
    if (idx !== -1) {
      const lenScore = Math.max(0, 1 - (len - 3) / Math.max(1, kw.length - 2));
      const posScore = 1 - idx / Math.max(1, s.length);
      return Math.round((lenScore * 0.7 + posScore * 0.3) * 100);
    }
    if (len - 3 > 12) break; // stop probing very long keywords
  }
  return 0; // never suggested = very low search volume
}

const log10 = n => (n > 0 ? Math.log10(n) : 0);
const avg = a => (a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0);

// Difficulty (0-100): how strong the top 10 apps are for this exact search.
function difficulty(kw, apps) {
  if (!apps.length) return { diff: 0, notes: 'no results' };
  const words = kw.split(/\s+/).filter(w => w.length > 2);
  const titleHits = apps.filter(a => words.every(w => (a.title || '').toLowerCase().includes(w))).length;
  const installs = apps.map(a => a.minInstalls || 0);
  const ratings = apps.map(a => a.ratings || 0);
  const scores = apps.map(a => a.score || 0);
  const stale = apps.filter(a => a.updated && Date.now() - a.updated > 365 * 864e5).length;

  const diff =
    (titleHits / 10) * 30 +                       // apps targeting the keyword in title
    Math.min(1, avg(installs.map(log10)) / 8) * 35 + // installs (1e8 = max)
    Math.min(1, avg(ratings.map(log10)) / 6) * 20 +  // review counts (1e6 = max)
    (avg(scores) / 5) * 15;                       // star quality

  return {
    diff: Math.round(diff),
    titleHits,
    medianInstalls: installs.sort((a, b) => a - b)[Math.floor(installs.length / 2)],
    avgRating: avg(scores).toFixed(2),
    staleTop10: stale,
    weakApps: apps.filter(a => (a.score || 0) < 4.0).length,
    leader: `${apps[0].title} (${apps[0].installs || '?'})`,
  };
}

const csv = rows => {
  const head = Object.keys(rows[0] || {});
  const esc = v => `"${String(v ?? '').replace(/"/g, '""')}"`;
  return [head.join(','), ...rows.map(r => head.map(h => esc(r[h])).join(','))].join('\n');
};

const summary = [];
for (const [idea, seeds] of Object.entries(cfg.ideas)) {
  if (only && only !== idea) continue;
  console.log(`\n== ${idea} ==`);
  let kws = new Set();
  for (const seed of seeds) {
    process.stdout.write(`  expanding "${seed}"... `);
    const ex = await expand(seed);
    ex.forEach(k => kws.add(k));
    console.log(ex.length);
  }
  kws = [...kws].slice(0, maxKeywordsPerIdea);

  const rows = [];
  for (const kw of kws) {
    const t = await traffic(kw);
    const apps = await topApps(kw);
    const d = difficulty(kw, apps);
    const opp = Math.round((t * (100 - d.diff)) / 100);
    rows.push({ keyword: kw, traffic: t, difficulty: d.diff, opportunity: opp, ...d, diff: undefined });
    console.log(`  ${kw.padEnd(40)} T${String(t).padStart(3)}  D${String(d.diff).padStart(3)}  OPP ${opp}`);
  }
  rows.forEach(r => delete r.diff);
  rows.sort((a, b) => b.opportunity - a.opportunity);
  fs.writeFileSync(path.join(OUT, `${idea}.csv`), csv(rows));

  const top5 = rows.slice(0, 5);
  summary.push({
    idea,
    keywordsScored: rows.length,
    avgOpportunityTop5: Math.round(avg(top5.map(r => r.opportunity))),
    avgTrafficTop5: Math.round(avg(top5.map(r => r.traffic))),
    avgDifficultyTop5: Math.round(avg(top5.map(r => r.difficulty))),
    weakAppsInTop10: Math.round(avg(top5.map(r => r.weakApps))),
    bestKeywords: top5.map(r => r.keyword).join(' | '),
  });
}

summary.sort((a, b) => b.avgOpportunityTop5 - a.avgOpportunityTop5);
if (summary.length) {
  const file = path.join(OUT, only ? `summary-${only}.csv` : 'summary.csv');
  fs.writeFileSync(file, csv(summary));
  console.log('\nRANKING');
  summary.forEach((s, i) => console.log(`${i + 1}. ${s.idea.padEnd(18)} opp ${s.avgOpportunityTop5}  traffic ${s.avgTrafficTop5}  diff ${s.avgDifficultyTop5}`));
  console.log(`\nSaved: ${OUT}`);
}
