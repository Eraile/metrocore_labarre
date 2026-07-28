<p align="center">
  <img src="README/metrocore.png" width="104" alt="metrocore">
</p>

<p align="center">
  <img src="README/labarre.png" width="290" alt="La barre en bas de l'écran d'accueil">
</p>

<h1 align="center">La Barre</h1>

<p align="center">
  <b>La barre de navigation Windows Phone, reconstruite sur Android.</b><br>
  Un projet <a href="https://metrocore.dev">metro<b>core</b></a>.
</p>

<p align="center">
  <a href="https://metrocore.dev"> >> metrocore.dev << </a>
</p>

<p align="center">
  <img src="README/bar.png" width="290" alt="La barre en bas de l'écran d'accueil">
</p>

---

Trois boutons, en overlay tout en bas de l'écran. Réglages par défaut :

| Bouton | Glyphe | Appui court | Appui long |
|---|---|---|---|
| Gauche | ← flèche | Précédent | Applis récentes |
| Centre | logo metrocore (couleur) | Accueil | — |
| Droite | 🔍 loupe | Recherche système | — |

Tout est modifiable depuis l'écran de réglages : couleur du fond et des glyphes
(les 20 accents WP8.1, ou **Material You** pour suivre le fond d'écran), hauteur de la
barre, taille des glyphes, vibration, masquage automatique en plein écran et sur l'écran
de verrouillage, et pour chacun des trois boutons une glyphe parmi 45, sa taille propre,
et une action par appui court et par appui long.

<p align="center">
  <img src="README/overview.png" width="245" alt="vue d'ensemble">
  <img src="README/buttons.png" width="245" alt="réglages d'un bouton">
  <img src="README/about.png" width="245" alt="page metrocore">
</p>

<p align="center"><i>
  L'écran de réglages est un panorama : une seule toile plus large que l'écran,<br>
  parcourue horizontalement, avec trois couches à trois vitesses.
</i></p>

Les actions disponibles : précédent, accueil, applis récentes, notifications, réglages
rapides, menu marche/arrêt, écran partagé, verrouiller, capture d'écran, recherche
système / assistant / vocale / web, **ouvrir une application**, et **action rapide**.

### Sur la recherche

Windows Phone indexait localement contacts, messages, musique et applications, et posait
le web par-dessus, le tout derrière une seule touche. **Android n'a pas cet
équivalent** : l'index local appartient au lanceur et n'est pas exposé. « recherche
système » est ce qui s'en approche le plus — elle tente la Quick Search Box (l'ancienne
recherche appareil + web, encore présente sur beaucoup d'appareils), puis l'assistant,
puis le web seul, et prend la première qui répond.

De même, les raccourcis que le système affiche sous l'icône d'une application ne sont
lisibles que par le lanceur par défaut (`LauncherApps` exige ce rôle). « action
rapide » passe donc par `ACTION_CREATE_SHORTCUT` : l'application ouvre son propre
sélecteur et rend un intent, qu'on rejoue ensuite. Toutes les applications ne le
proposent pas.

## Installation

Les APK signés sont publiés dans
[Releases](https://github.com/Eraile/metrocore_labarre/releases). Android demandera
d'autoriser l'installation depuis la source utilisée (navigateur ou gestionnaire de
fichiers) — c'est normal, et ça ne se demande qu'une fois.

Pour publier une version : [RELEASING.md](RELEASING.md).

### Se mettre à jour

Hors du Play Store, personne ne pousse les mises à jour à votre place : une correction
n'atteint que ceux qui repassent d'eux-mêmes sur la page du dépôt. La section *metrocore*
de l'écran de réglages porte donc une ligne **mise à jour**, qui interroge les Releases du
dépôt, télécharge l'APK et le passe à l'installeur d'Android.

Rien n'est automatique : **ni vérification au démarrage, ni téléchargement en fond**. On
ne va sur le réseau que sur appui, et l'installation reste celle du système, avec son
écran de confirmation. Une application qui s'installe toute seule est exactement ce dont
il faut se méfier. Voir
[Updates.kt](app/src/main/java/dev/metrocore/navbar/Updates.kt).

L'APK téléchargé atterrit dans le cache de l'application, et le `FileProvider` n'expose
que ce sous-dossier. Comme il est signé de la même clé que celui installé, Android accepte
la mise à jour par-dessus — c'est tout l'intérêt de garder la même clé à vie
([RELEASING.md](RELEASING.md)).

## Setup (une seule fois, sur le téléphone)

1. Installer l'APK.
2. Ouvrir **metrocore — La Barre** → section *vue d'ensemble* → **ouvrir les réglages
   système**.
3. Dans les réglages d'accessibilité, ouvrir **metrocore — La Barre** (souvent sous
   *Applications installées* / *Services téléchargés*), activer l'interrupteur, confirmer.
4. La barre apparaît immédiatement. Pour l'enlever : désactiver le même interrupteur.

## Fidélité : d'où viennent les constantes

Rien n'est réglé à l'œil. Métriques, couleurs, courbes et durées viennent de
[metrocore](../metrocore) — `spec/tokens.json` et `src/` — et sont reportées dans
[MetroTokens.kt](app/src/main/java/dev/metrocore/navbar/MetroTokens.kt) :

- **hauteur de barre** — WP posait 60 px de bande sur un écran large de 480 px, soit
  un huitième de la largeur. On ancre sur la **largeur** et non sur la hauteur : les
  téléphones actuels sont bien plus allongés que le 5:3 du WVGA, et un ratio pris sur
  la hauteur donnerait une bande énorme. Sur un écran de 411 dp ça fait 51 dp. C'est
  aujourd'hui la valeur de repli : la hauteur automatique se cale d'abord sur la bande
  que réserve la barre système, qui en fait 48 — voir *La place en bas de l'écran*.
- **taille des glyphes** — le tiers de la bande (20 sur 60), d'où le « auto (33 %) ».
- **retour visuel** — l'opacité tombe à 45 % en 100 ms et remonte en 200 ms, avec un
  enfoncement à 0.985. C'est `.mc-navkey.is-down` et les tokens `tilt`. Pas
  d'ondulation Material : c'est exactement le détail qui trahirait la reconstruction.
- **appui long** — 550 ms (`hold-ms`), et il *annule* l'appui court.
- **accents** — les vingt teintes stock de WP8.1, ni plus ni moins.
- **glyphes** — générées depuis `metrocore/src/controls/icons.ts` par
  [tools/gen-icons.mjs](tools/gen-icons.mjs) ; `android:pathData` accepte la même
  grammaire que SVG, donc les chaînes sont recopiées telles quelles.

L'écran de réglages est un **panorama** (`metrocore/src/nav/panorama.ts`) : une seule
toile plus large que l'écran, parcourue horizontalement, avec trois couches à trois
vitesses — fond 0.30×, contenu 1.00×, et le titre entre les deux. Les sections font 0.8
écran pour que la suivante dépasse toujours à droite, et le titre surdimensionné est
coupé par les deux bords, volontairement. Les contrôles (interrupteur, pastilles,
radios, ListPicker plein écran) suivent `metrocore/src/styles/controls.css`.

Sept sections : vue d'ensemble, apparence, retour, un par bouton, et metrocore.

L'écran est en **thème clair** — dégradé crème → or, texte noir — pris sur le thème
clair de metrocore (`spec/tokens.json` → `themes.light`), qui était un réglage système à
part entière sur WP et pas une arrière-pensée. Les deux palettes sont définies dans
[MetroTokens.kt](app/src/main/java/dev/metrocore/navbar/MetroTokens.kt) ; `MetroUi` en
prend une et tout en découle. La barre elle-même garde sa palette propre, réglée dans
l'app.

Le même dégradé est posé deux fois : sur la couche de fond mobile, et sur le panorama
lui-même. Tirer au-delà des extrémités décale la couche mobile et découvrirait sinon le
fond nu — là on retombe sur la même teinte, crème à gauche, or à droite. Le débordement
est en plus amorti (`scroll-rubber-limit`, 90 px).

Une nuance sur le titre : metrocore le fait défiler à 0.60× fixe. Ici sa vitesse est
calculée pour qu'il **finisse sa course en même temps que le contenu** (plafonnée à
0.60×). Avec un titre court comme « La Barre » et sept sections, un taux fixe l'aurait
emmené hors de l'écran dès la troisième — alors qu'un panorama garde son titre visible
jusqu'au bout.

### Sur le logo Windows

metrocore a volontairement retiré le drapeau Windows au profit d'une glyphe « metro »
maison : le glyphe d'origine était exact, mais une marque déposée est la seule chose
qu'une reconstruction ne peut pas reproduire. Les trois sont fournies : `metrocolor`
(le logo metrocore en couleur) est le défaut de la touche centrale, `metro` en est la
version monochrome, et `windows` reste dans le sélecteur pour qui veut le drapeau
d'origine.

## Langues

L'app suit la langue du téléphone. L'anglais est la langue par défaut
(`values/strings.xml`) et sert de repli pour toute langue non couverte.

| | | | |
|---|---|---|---|
| `fr` français | `de` allemand | `ru` russe | `ko` coréen |
| `es` espagnol | `pl` polonais | `zh` chinois simplifié | `ar` arabe |
| `pt` portugais | `it` italien | `ja` japonais | `eu` euskara |

82 chaînes traduisibles par langue, toutes présentes dans les douze fichiers. Les 7
restantes sont les noms de produit — « metrocore — La Barre », « La Barre »,
« metrocore », « metrocore.dev » — marqués `translatable="false"` : elles n'existent que
dans le fichier par défaut et ne bougent dans aucune langue.

Trois remarques :

- **L'euskara n'a pas été relu par un locuteur natif.** Il est marqué comme tel en tête
  de [values-eu/strings.xml](app/src/main/res/values-eu/strings.xml) ; à faire corriger
  avant publication — c'est la langue du pied de page, autant qu'elle soit juste.
- **L'allemand est en minuscules, substantifs compris.** C'est la règle de style Metro,
  pas une coquille : Windows Phone composait son interface allemande ainsi.
- **L'arabe s'affiche en RTL mais la mise en page reste LTR.** `supportsRtl` est à
  `false` : le texte arabe se rend correctement et s'aligne à droite dans chaque bloc,
  mais le panorama défile de gauche à droite par construction. Activer le RTL
  retournerait marges et alignements sans retourner le panorama — un miroir à moitié se
  voit plus qu'un sens assumé. Le miroir complet est un chantier à part.

Pour tester une langue sans changer celle du téléphone (Android 13+) :

```bash
adb shell cmd locale set-app-locales dev.metrocore.navbar --locales ja
adb shell cmd locale set-app-locales dev.metrocore.navbar --locales ""   # retour au système
```

## Régénérer les glyphes

```bash
node tools/gen-icons.mjs [chemin/vers/metrocore]   # défaut : ../metrocore
```

Écrit `app/src/main/res/drawable/ic_mc_*.xml` et
`app/src/main/java/dev/metrocore/navbar/NavIcons.kt`. Les deux sont versionnés : on ne
régénère que si metrocore bouge.

Pas de « Affichage par-dessus les autres applications » : l'overlay utilise
`TYPE_ACCESSIBILITY_OVERLAY`, fourni par le service d'accessibilité lui-même. Les trois
seules permissions déclarées sont `VIBRATE` pour l'haptique, et `INTERNET` +
`REQUEST_INSTALL_PACKAGES` pour la mise à jour intégrée — cette dernière n'agit que si
l'utilisateur l'autorise en plus, écran système à l'appui.

## Build & run depuis VS Code (F5)

`F5` lance la configuration sélectionnée dans le menu déroulant **Run and Debug** :

| Configuration | Effet |
|---|---|
| **Build & Install -> Emulateur** (défaut) | démarre l'AVD s'il ne tourne pas, attend la fin du boot, build, installe, lance l'app |
| **Build & Install -> Appareil USB** | même chose sur le téléphone branché en USB |

Même chose en tâches : `Ctrl+Shift+B` pour l'émulateur, ou `Ctrl+Shift+P` →
*Run Task* pour choisir la cible.

Le script derrière tout ça : [.vscode/run-android.ps1](.vscode/run-android.ps1). Il
retrouve le SDK via `local.properties` (ou `ANDROID_HOME`), sélectionne la bonne cible,
et force `ANDROID_SERIAL` pour que Gradle installe au bon endroit même si un émulateur
et un téléphone sont connectés en même temps.

## Build en ligne de commande

```bash
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk

# installation directe sur un appareil branché en USB (débogage USB activé)
./gradlew installDebug
```

`local.properties` (chemin du SDK Android) est généré localement et non versionné.

## Structure

- [MetroTokens.kt](app/src/main/java/dev/metrocore/navbar/MetroTokens.kt) — les constantes
  portées de metrocore, avec la conversion WVGA → écran réel.
- [NavBarConfig.kt](app/src/main/java/dev/metrocore/navbar/NavBarConfig.kt) — l'état
  complet + sa persistance.
- [NavBarView.kt](app/src/main/java/dev/metrocore/navbar/NavBarView.kt) — construction de
  la barre. Le **même** code sert à l'overlay et à l'aperçu des réglages, donc l'aperçu
  ne peut pas mentir.
- [NavKeyView.kt](app/src/main/java/dev/metrocore/navbar/NavKeyView.kt) — une touche :
  appui, maintien, retour visuel, haptique.
- [NavBarService.kt](app/src/main/java/dev/metrocore/navbar/NavBarService.kt) — le service
  d'accessibilité, et les deux raisons de s'effacer : plein écran, verrouillage.
- [SystemBars.kt](app/src/main/java/dev/metrocore/navbar/SystemBars.kt) — ce que le
  système réserve déjà en bas de l'écran.
- [MetroUi.kt](app/src/main/java/dev/metrocore/navbar/MetroUi.kt) — les briques Metro
  (interrupteur, pastilles, radios, ListPicker, slider), dessinées à la main.
- [PanoramaView.kt](app/src/main/java/dev/metrocore/navbar/PanoramaView.kt) — le panorama.
- [MainActivity.kt](app/src/main/java/dev/metrocore/navbar/MainActivity.kt) — l'écran de
  réglages.
- [AppTargets.kt](app/src/main/java/dev/metrocore/navbar/AppTargets.kt) — l'inventaire des
  applications lançables et des fournisseurs d'actions rapides.

### Un piège de layout, pour mémoire

Le rail du panorama fait la largeur de **tout** le panorama, pas celle de l'écran. Un
parent qui découpe ses enfants les découpe à leurs propres bornes : un rail large d'un
écran se fait rogner à un écran, et une section posée à 864 px n'est alors dessinée que
sur `1080 - 864` px. Le symptôme est reconnaissable — la coupure tombe pile sur la
largeur d'écran, alors que mesures et `layout` sont tous corrects.

Les réglages passent par les `SharedPreferences` et le service les écoute : l'écran
écrit, la barre se reconstruit toute seule. Aucun binder à maintenir entre les deux.

## La place en bas de l'écran

**Aucun overlay ne peut réserver d'espace.** Ni `TYPE_ACCESSIBILITY_OVERLAY`, ni
`TYPE_APPLICATION_OVERLAY` : seul le système pose des insets, et seulement pour sa propre
barre. La Barre se dessine donc *par-dessus*, et c'est vrai de toutes les barres de
navigation tierces. Ce qui change d'un mode de navigation à l'autre, c'est ce qu'il y a
dessous.

- **Navigation à 3 boutons** — le système réserve déjà sa bande (~48 dp) et le contenu
  des applications s'arrête au-dessus. La Barre se pose sur cette bande : elle recouvre
  la barre système, pas le contenu. C'est le mode le plus confortable.
- **Navigation gestuelle** — le système ne réserve que la poignée (~24 dp) et le contenu
  descend jusqu'en bas. Une barre plus haute que cette poignée mord donc sur le contenu.

### Le référentiel : la place de la barre système

La hauteur automatique n'est plus dérivée de la proportion WP mais **de ce que le système
réserve réellement**, mesuré par
[SystemBars.kt](app/src/main/java/dev/metrocore/navbar/SystemBars.kt). En 3 boutons, La
Barre vient donc se poser exactement sur la bande système : elle ne recouvre rien de plus
que ce que la barre système recouvrait déjà.

Cette formulation est prudente exprès. Depuis qu'Android impose le bord à bord, une
application qui n'applique pas ses insets dessine sous la bande réservée, et son contenu
disparaît derrière la barre système — un clavier de jeu, par exemple. Ça arrive avec la
barre stock, sur n'importe quel appareil, et La Barre posée au même endroit n'y ajoute
rien : le correctif appartient à l'application, pas à nous.

Le changement coûte étonnamment peu en fidélité. La proportion WP — 60 px sur 480 —
donne 51 dp sur un écran de 411 dp de large ; une barre système Android en fait 48. Les
deux référentiels tombaient déjà presque au même endroit, ce qui est moins un hasard
qu'un rappel que 48 dp est la hauteur d'une rangée de boutons pour un pouce.

En navigation gestuelle il n'y a plus de bande à prendre, seulement la poignée. S'y caler
donnerait une barre de 24 dp : sous n'importe quel minimum tactile — le token
`TOUCH_MIN` de metrocore, déjà bas, est à 34 dp — et posée pile sur la zone du geste.
Il n'y a donc plus de référentiel du tout : on revient à la proportion WP et le
recouvrement est assumé. Le réglage **quand l'afficher**, dans *comportement*, tranche
entre les deux seules réponses possibles :

- **toujours** — la barre est là partout, elle recouvre là où il n'y a pas la place.
  C'est le défaut : une barre absente au premier lancement passe pour une panne.
- **seulement là où elle tient** — elle ne s'affiche que là où le système lui laisse une
  bande, donc pas en navigation gestuelle.

### Se masquer

- **masquer en plein écran** — la barre s'efface quand l'application au premier plan
  prend tout l'écran. C'est ce qui règle les jeux et les lecteurs vidéo, quel que soit le
  mode de navigation. Actif par défaut.

Le critère est **l'absence de la barre de navigation système** : une fenêtre `TYPE_SYSTEM`
collée au bord bas et mince. C'est sa place qu'occupe La Barre, donc c'est sur elle qu'il
faut se caler — et pas sur la barre d'état, mesure à l'appui : l'appareil photo masque la
barre d'état mais garde la barre de navigation, et se fier à la première faisait
disparaître La Barre alors qu'il restait la place.

Trois approches plus simples ont été essayées et **mesurées fausses** :

- *comparer les bornes de la fenêtre active à celles de l'écran* — depuis le bord à bord
  imposé, une application couvre tout l'écran même barres affichées. Sur l'écran d'accueil
  déjà : `type=1 Rect(0, 0 - 1080, 2400)` sur un écran de 2400.
- *l'absence de toute fenêtre `TYPE_SYSTEM`* — ce type couvre plus que les barres, il en
  reste presque toujours une, si bien que la condition n'était jamais vraie.
- *`rootWindowInsets`*, qui aurait été l'API juste — elle rend `null` sur un overlay
  d'accessibilité, le système ne lui dispatche pas d'insets.

Tant que La Barre n'a jamais reconnu de barre de navigation sur l'appareil, elle ne masque
rien : sans ce garde-fou, un fabricant qui exposerait ses barres autrement la ferait
disparaître en permanence, ce qui est bien pire que de ne jamais se masquer.

**Le prix à payer** : `getWindows()` n'est alimenté que si le service déclare
`canRetrieveWindowContent="true"` — Android conditionne `flagRetrieveInteractiveWindows`
à cette capacité. Avec `false`, la liste est vide et aucune détection n'est possible. La
Barre ne lit toujours aucun contenu — jamais de `getRootInActiveWindow()`, jamais un nœud
— mais elle déclare désormais la capacité de le faire. Voir [SIGNING.md](SIGNING.md) :
c'est la capacité déclarée qui compte dans le formulaire Play.

Pour voir ce qu'un appareil expose réellement, passer `DEBUG` à `true` dans
[NavBarService.kt](app/src/main/java/dev/metrocore/navbar/NavBarService.kt) et lire
`adb logcat -s LaBarre` : chaque évaluation trace le type et les bornes de toutes les
fenêtres.

**masquer sur l'écran de verrouillage** suit la même logique, via `KeyguardManager` et
les diffusions d'allumage d'écran. Actif par défaut lui aussi.

## Limites connues

- Pas de swipe-up pour rappeler la barre quand elle s'est masquée : elle revient d'elle
  même en quittant le plein écran.
- En navigation gestuelle, la barre recouvre le bas du contenu. Il n'y a pas de correctif
  possible (voir ci-dessus) : seulement le masquage en plein écran, ou ne pas l'afficher
  du tout dans ce mode.
- L'arabe est traduit mais la mise en page n'est pas en miroir (voir *Langues*).

## Licence et crédits

**[MPL-2.0](LICENSE)** — la même que [metrocore](https://metrocore.dev), dont ce projet
porte les métriques, les couleurs, les courbes et les glyphes.

La MPL est un copyleft **par fichier** : chaque source porte donc son en-tête, y compris
celles que [tools/gen-icons.mjs](tools/gen-icons.mjs) génère. Concrètement, tu peux
réutiliser ce code dans un projet fermé ; ce que tu modifies dans *ces* fichiers-là doit
rester ouvert.

Windows Phone, Windows et le logo Windows sont des marques de Microsoft. Ce projet est
une reconstruction indépendante, sans lien avec Microsoft. Le drapeau Windows n'est
fourni que comme glyphe optionnelle ; le défaut est le logo metrocore.

<p align="center">
  <img src="README/surf.jpg" width="100%" alt="">
</p>

<p align="center">
  2026 — <b>NoMercy Studios</b> —
  <img src="app/src/main/res/drawable-nodpi/basque_flag.png" width="20" alt="Ikurriña">
  Made in Basque Country
</p>
