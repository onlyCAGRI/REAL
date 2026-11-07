// Shorts and jeans built from Humation's tapered-pants and mini-skirt (MIT). Writes into ../parts/bottom.
// node android/avatar/drafts/make-bottoms.mjs
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const { humation1 } = await import(pathToFileURL(join(here, '..', 'vendor', 'humation-assets-humation-1-1.0.3.mjs')));
const inner = (name) => humation1.parts.find((p) => p.selectionSlot === 'bottom' && p.name === name).layers[0].svg
  .replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '')
  // keep each source's ids apart
  .replace(/id="([^"]+)"/g, `id="${name}-$1"`).replace(/url\(#([^)]+)\)/g, `url(#${name}-$1)`);
const fillOf = (name) => humation1.parts.find((p) => p.selectionSlot === 'bottom' && p.name === name).layers[0].svg
  .match(/<path[^>]*fill="var\(--hm-bottom[^"]*"[^>]*\/>/)[0].replace(/ fill="[^"]*"/, '').replace(/ stroke[a-z-]*="[^"]*"/g, '');
const S = 'var(--hm-stroke, #000000)';
const head = '<svg width="80" height="107" viewBox="0 0 80 107" fill="none" xmlns="http://www.w3.org/2000/svg">';

// Shorts: pants above the cut, the skirt's bare legs, socks and shoes below it; a turned-up hem at the cut.
const K = 64;
const shorts = `${head}<defs>
<clipPath id="real-sort-up"><rect x="0" y="0" width="80" height="${K}"/></clipPath>
<clipPath id="real-sort-down"><rect x="0" y="${K}" width="80" height="${107 - K}"/></clipPath>
<clipPath id="real-sort-cloth">${fillOf('tapered-pants')}</clipPath></defs>
<g clip-path="url(#real-sort-down)">${inner('mini-skirt')}</g>
<g clip-path="url(#real-sort-up)">${inner('tapered-pants')}</g>
<g clip-path="url(#real-sort-cloth)">
<rect x="0" y="${K - 3.2}" width="80" height="3.2" fill="#FFFFFF" fill-opacity="0.16"/>
<rect x="0" y="${K - 3.6}" width="80" height="0.9" fill="${S}"/>
<rect x="0" y="${K - 0.9}" width="80" height="1.6" fill="${S}"/>
</g></svg>
`;
writeFileSync(join(here, '..', 'parts', 'bottom', 'sort.svg'), shorts);

// Jeans: waistband, fly, front pockets, golden topstitching and rolled cuffs, all clipped to the trousers.
const gold = '#E5B84D';
const jeans = `${head}<defs><clipPath id="real-kot-cloth">${fillOf('tapered-pants')}</clipPath></defs>
${inner('tapered-pants')}
<g clip-path="url(#real-kot-cloth)">
<rect x="0" y="11.2" width="80" height="0.9" fill="${S}"/>
<rect x="0" y="12.9" width="80" height="0.45" fill="${gold}"/>
<path d="M41.4,12 C41.6,17 41.4,21 39.6,24.5" stroke="${S}" stroke-width="0.9" stroke-linecap="round"/>
<path d="M40.3,12.4 C40.4,16.5 40.2,20 38.8,22.8" stroke="${gold}" stroke-width="0.45" stroke-dasharray="1 0.8"/>
<path d="M24.5,12.2 C27,13.4 29.4,15.6 30.6,18.6" stroke="${S}" stroke-width="0.9" stroke-linecap="round"/>
<path d="M49.5,12.2 C51.5,13.6 53.6,15.6 54.6,18.4" stroke="${S}" stroke-width="0.9" stroke-linecap="round"/>
<path d="M25.4,13.6 C27.4,14.7 29.1,16.5 30,18.8" stroke="${gold}" stroke-width="0.45" stroke-dasharray="1 0.8"/>
<path d="M50.3,13.6 C52,14.8 53.6,16.6 54.3,18.8" stroke="${gold}" stroke-width="0.45" stroke-dasharray="1 0.8"/>
<rect x="0" y="80.6" width="80" height="6" fill="#FFFFFF" fill-opacity="0.18"/>
<rect x="0" y="80.2" width="80" height="0.9" fill="${S}"/>
<rect x="0" y="82.4" width="80" height="0.45" fill="${gold}"/>
</g></svg>
`;
writeFileSync(join(here, '..', 'parts', 'bottom', 'kot.svg'), jeans);
console.log('sort.svg, kot.svg written');
