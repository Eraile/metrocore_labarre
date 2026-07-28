/*
 * Genere les VectorDrawable des icones a partir de metrocore/src/controls/icons.ts.
 *
 *     node tools/gen-icons.mjs [chemin/vers/metrocore]
 *
 * android:pathData accepte la meme grammaire que SVG (arcs "A" compris), donc les
 * chaines de metrocore sont recopiees telles quelles : une seule source de verite.
 *
 * Les sorties (app/src/main/res/drawable/ic_mc_*.xml) sont versionnees ; on ne
 * regenere que si metrocore bouge.
 */

import { readFileSync, writeFileSync, readdirSync, unlinkSync } from "node:fs";
import { join, resolve } from "node:path";

const metrocore = resolve(process.argv[2] ?? "../metrocore");
const outDir = resolve("app/src/main/res/drawable");

const source = readFileSync(join(metrocore, "src/controls/icons.ts"), "utf8");

// Corps de `export const icons = { ... } as const;`
const body = source.slice(
  source.indexOf("export const icons"),
  source.indexOf("} as const;"),
);

// Retire les commentaires pour ne pas capturer un ":" qui trainerait dedans.
const clean = body.replace(/\/\/[^\n]*/g, "").replace(/\/\*[\s\S]*?\*\//g, "");

const entries = [...clean.matchAll(/(\w+)\s*:\s*"((?:[^"\\]|\\.)*)"/g)].map(
  ([, name, path]) => ({ name, path }),
);

if (entries.length === 0) {
  console.error("Aucune icone trouvee — le format de icons.ts a change ?");
  process.exit(1);
}

/** Les seules glyphes pleines ; tout le reste est un trait d'epaisseur constante. */
const FILLED = new Set(["metro"]);

/** Icones propres a ce projet, absentes de metrocore. */
const EXTRA = [
  // Le drapeau Windows : quatre quadrilateres en perspective. Absent de metrocore,
  // qui l'a retire volontairement (marque deposee) au profit de la glyphe "metro".
  {
    name: "windows",
    filled: true,
    path:
      "M2.2 5.6 L11.1 4.35 L11.1 11.5 L2.2 11.5 Z " +
      "M12.2 4.2 L21.8 2.85 L21.8 11.5 L12.2 11.5 Z " +
      "M2.2 12.5 L11.1 12.5 L11.1 19.65 L2.2 18.4 Z " +
      "M12.2 12.5 L21.8 12.5 L21.8 21.15 L12.2 19.8 Z",
  },
  // La touche retour capacitive est une fleche complete, pas le chevron applicatif :
  // c'est ce que montrent les photos d'appareils WP.
  { name: "backarrow", filled: false, path: "M21 12 L3 12 M10.5 4.5 L3 12 L10.5 19.5" },
];

/*
 * Glyphes ecrites a la main, pas generees : elles ont plusieurs teintes et ne passent
 * donc pas par le gabarit monochrome ci-dessous. On les declare pour qu'elles
 * apparaissent dans le selecteur — et pour que la purge les epargne.
 */
const HANDWRITTEN = [{ name: "metrocolor", tintable: false }];

const STROKE_WIDTH = 1.8; // navkeys.ts : icon(..., { weight: 1.8 })

const xml = ({ name, path, filled }) => `<?xml version="1.0" encoding="utf-8"?>
<!-- Genere par tools/gen-icons.mjs — ne pas editer a la main.
     Source : metrocore/src/controls/icons.ts (${name}) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Toujours blanc ici : la couleur reelle est appliquee au runtime via
         ImageView.imageTintList, pour qu'une seule icone serve a tous les themes. -->
    <path
        android:pathData="${path}"
${
  filled
    ? `        android:fillColor="#FFFFFFFF" />`
    : `        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="${STROKE_WIDTH}"
        android:strokeLineCap="square"
        android:strokeLineJoin="miter" />`
}
</vector>
`;

// Purge les generations precedentes pour que les icones retirees en amont disparaissent.
// Les glyphes ecrites a la main portent le meme prefixe : il faut les epargner.
const spared = new Set(HANDWRITTEN.map((i) => `ic_mc_${i.name}.xml`));
for (const file of readdirSync(outDir)) {
  if (file.startsWith("ic_mc_") && file.endsWith(".xml") && !spared.has(file)) {
    unlinkSync(join(outDir, file));
  }
}

const all = [
  ...entries.map((e) => ({ ...e, filled: FILLED.has(e.name) })),
  ...EXTRA,
];

for (const item of all) {
  writeFileSync(join(outDir, `ic_mc_${item.name}.xml`), xml(item), "utf8");
}

// Les glyphes des touches capacitives d'abord : ce sont celles qu'on cherche dans le
// selecteur, le reste est de la garniture.
const FIRST = ["backarrow", "windows", "search", "back", "metro", "metrocolor"];
const catalogue = [...all, ...HANDWRITTEN];
const ordered = [
  ...FIRST.map((n) => catalogue.find((i) => i.name === n)).filter(Boolean),
  ...catalogue
    .filter((i) => !FIRST.includes(i.name))
    .sort((a, b) => a.name.localeCompare(b.name)),
];

const kt = `/*
 * Genere par tools/gen-icons.mjs — ne pas editer a la main.
 * Source : metrocore/src/controls/icons.ts
 */

package dev.metrocore.navbar

import androidx.annotation.DrawableRes

/**
 * Une glyphe disponible pour un bouton de la barre.
 *
 * [tintable] est faux pour les glyphes qui portent leurs propres couleurs : les teinter
 * les aplatirait en une seule teinte.
 */
data class NavIcon(val key: String, @DrawableRes val res: Int, val tintable: Boolean = true)

/** Toutes les glyphes, dans l'ordre d'affichage du selecteur. */
val NAV_ICONS: List<NavIcon> = listOf(
${ordered
  .map(
    (i) =>
      `    NavIcon("${i.name}", R.drawable.ic_mc_${i.name}${
        i.tintable === false ? ", tintable = false" : ""
      }),`,
  )
  .join("\n")}
)

fun navIcon(key: String): NavIcon = NAV_ICONS.firstOrNull { it.key == key } ?: NAV_ICONS[0]
`;

writeFileSync(resolve("app/src/main/java/dev/metrocore/navbar/NavIcons.kt"), kt, "utf8");

console.log(`${all.length} icones generees dans ${outDir}`);
console.log(`+ app/src/main/java/dev/metrocore/navbar/NavIcons.kt`);
