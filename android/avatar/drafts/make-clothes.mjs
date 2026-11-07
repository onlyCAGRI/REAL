// Trial clothes from Humation parts: a pattern clipped to the garment's own fill shapes.
import { writeFileSync, mkdirSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
const [vendor, out] = process.argv.slice(2);
const { humation1 } = await import(pathToFileURL(vendor + '/humation-assets-humation-1-1.0.3.mjs'));
mkdirSync(out, { recursive: true });
const svgOf = (slot, name) => humation1.parts.find((p) => p.selectionSlot === slot && p.name === name).layers[0].svg;

function patterned(svg, colourVar, id, pattern) {
  // Every path filled with the garment colour becomes part of the clip; the pattern is drawn on top, under the outlines.
  const fills = [...svg.matchAll(new RegExp(`<path[^>]*fill="var\\(--${colourVar}[^"]*"[^>]*/>`, 'g'))].map((m) => m[0]);
  const clip = `<clipPath id="${id}">${fills.map((f) => f.replace(/ fill="[^"]*"/, '').replace(/ stroke[a-z-]*="[^"]*"/g, '')).join('')}</clipPath>`;
  const layer = `<g clip-path="url(#${id})">${pattern}</g>`;
  // Insert the pattern right after the last garment fill so stroke paths that follow stay on top.
  const last = fills[fills.length - 1];
  const i = svg.lastIndexOf(last) + last.length;
  return svg.replace(/(<svg[^>]*>)/, `$1<defs>${clip}</defs>`).slice(0, i + `<defs>${clip}</defs>`.length) + layer + svg.replace(/(<svg[^>]*>)/, `$1<defs>${clip}</defs>`).slice(i + `<defs>${clip}</defs>`.length);
}

const S = 'var(--hm-stroke, #000000)';
let stripes = '';
for (let y = 0; y < 80; y += 4.2) stripes += `<rect x="0" y="${y.toFixed(1)}" width="80" height="1.7" fill="${S}" fill-opacity="0.85"/>`;
const tee = patterned(svgOf('body', 'tee'), 'hm-clothes', 'real-cizgili-clip', stripes);
writeFileSync(out + '/cizgili-tisort.svg', tee);

let plaid = '';
for (let x = -2; x < 80; x += 5) plaid += `<rect x="${x}" y="0" width="1.6" height="107" fill="${S}" fill-opacity="0.55"/>`;
for (let y = 0; y < 107; y += 5) plaid += `<rect x="0" y="${y}" width="80" height="1.6" fill="${S}" fill-opacity="0.55"/>`;
for (let x = 0.5; x < 80; x += 10) plaid += `<rect x="${x}" y="0" width="0.6" height="107" fill="#FFFFFF" fill-opacity="0.5"/>`;
const skirt = patterned(svgOf('bottom', 'midi-skirt'), 'hm-bottom', 'real-kareli-clip', plaid);
writeFileSync(out + '/kareli-etek.svg', skirt);
console.log('tee fills', (svgOf('body', 'tee').match(/hm-clothes/g) || []).length, 'skirt fills', (svgOf('bottom', 'midi-skirt').match(/hm-bottom/g) || []).length);
