const fs = require("fs"), path = require("path");
const root = process.argv[2];
const en = JSON.parse(fs.readFileSync(path.join(root, "assets/i18n/en.json"), "utf8"));
const keys = new Set();
const re = /\bt\(\s*"((?:[^"\\]|\\.)*)"/g;
(function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) walk(p);
    else if (p.endsWith(".kt")) {
      const s = fs.readFileSync(p, "utf8");
      for (const m of s.matchAll(re)) {
        try { keys.add(JSON.parse('"' + m[1] + '"')); } catch { keys.add(m[1]); }
      }
    }
  }
})(path.join(root, "java"));
const miss = [...keys].filter(k => !(k in en));
if (process.argv[3] === "--json") { console.log(JSON.stringify(miss)); process.exit(0); }
console.log("json:", Object.keys(en).length, "t-keys:", keys.size, "missing:", miss.length);
console.log(miss.join("\n"));
