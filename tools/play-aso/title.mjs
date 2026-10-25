// Scores candidate title keywords on live Play data (traffic = autocomplete strength, difficulty = top-10 strength).
// Behind a proxy? set GLOBAL_AGENT_HTTPS_PROXY. Harmless otherwise.
import 'global-agent/bootstrap.js';
import gplay from 'google-play-scraper';
const country = process.argv[2] || 'us', lang = process.argv[3] || 'en';
const kws = process.argv.slice(4);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const cache = new Map();
async function sug(t){ if(cache.has(t)) return cache.get(t); await sleep(300); const r = await gplay.suggest({term:t,country,lang}).catch(()=>[]); const l=r.map(x=>x.toLowerCase()); cache.set(t,l); return l; }
async function traffic(kw){ for(let n=Math.min(3,kw.length);n<=kw.length;n++){ const s=await sug(kw.slice(0,n)); const i=s.indexOf(kw); if(i>=0){ const ls=Math.max(0,1-(n-3)/Math.max(1,kw.length-2)); return {t:Math.round((ls*0.7+(1-i/Math.max(1,s.length))*0.3)*100), chars:n}; } } return {t:0,chars:null}; }
const lg=n=>n>0?Math.log10(n):0, avg=a=>a.length?a.reduce((x,y)=>x+y,0)/a.length:0;
const out=[];
for (const kw of kws){
  const tr=await traffic(kw); await sleep(500);
  const apps=await gplay.search({term:kw,num:10,country,lang,fullDetail:true}).catch(e=>{console.error(kw,e.message);return [];});
  const words=kw.split(/\s+/).filter(w=>w.length>1);
  const hits=apps.filter(a=>words.every(w=>(a.title||'').toLowerCase().includes(w))).length;
  const d=Math.round((hits/10)*30+Math.min(1,avg(apps.map(a=>lg(a.minInstalls||0)))/8)*35+Math.min(1,avg(apps.map(a=>lg(a.ratings||0)))/6)*20+(avg(apps.map(a=>a.score||0))/5)*15);
  const row={kw,traffic:tr.t,suggestedAfterChars:tr.chars,difficulty:d,opportunity:Math.round(tr.t*(100-d)/100),titleMatchesTop10:hits,weakTop10:apps.filter(a=>(a.score||0)<4).length,leader:apps[0]?`${apps[0].title} (${apps[0].installs})`:'-'};
  out.push(row); console.log(JSON.stringify(row));
}
out.sort((a,b)=>b.opportunity-a.opportunity); console.table(out.map(({leader,...r})=>r));
