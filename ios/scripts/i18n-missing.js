const fs = require("fs");
const path = require("path");

const iosRoot = process.argv[2] || path.join(__dirname, "..");
const enPath = path.join(__dirname, "../../android/app/src/main/assets/i18n/en.json");
const en = JSON.parse(fs.readFileSync(enPath, "utf8"));
const keys = new Set();

const tRe = /\bt\(\s*"((?:[^"\\]|\\.)*)"/g;
const tInRe = /\btIn\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"/g;

function unescape(value) {
  try {
    return JSON.parse('"' + value + '"');
  } catch {
    return value;
  }
}

function walk(dir) {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full);
    } else if (entry.isFile() && full.endsWith(".swift")) {
      const source = fs.readFileSync(full, "utf8");
      for (const match of source.matchAll(tRe)) keys.add(unescape(match[1]));
      for (const match of source.matchAll(tInRe)) {
        const context = unescape(match[1]);
        const tr = unescape(match[2]);
        const key = context + ":" + tr;
        keys.add(key in en ? key : tr);
      }
    }
  }
}

for (const folder of ["Real", "Shared", "RealShare"]) {
  walk(path.join(iosRoot, folder));
}

const missing = [...keys].filter((key) => !(key in en));
if (process.argv[3] === "--json") {
  console.log(JSON.stringify(missing));
  process.exit(0);
}
console.log("json:", Object.keys(en).length, "t-keys:", keys.size, "missing:", missing.length);
console.log(missing.join("\n"));
