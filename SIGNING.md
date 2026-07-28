# Signer et publier

## Où on en est

L'APK produit jusqu'ici est un **build debug**, signé automatiquement avec la clé de
debug d'Android — celle que le SDK génère pour tout le monde. Ça installe et ça tourne,
mais ça ne se distribue pas : Google Play refuse les APK signés avec cette clé.

Le bloc `release` de [app/build.gradle.kts](app/build.gradle.kts) cherche une vraie clé
dans `keystore.properties`. Sans ce fichier, `assembleRelease` sort un binaire **non
signé** — volontairement. Une release silencieusement signée en debug est le genre de
chose qui se découvre le jour de la publication.

## 1. Créer la clé (une seule fois, à garder à vie)

```bash
keytool -genkeypair -v \
  -keystore labarre-upload.jks \
  -alias labarre \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

`keytool` est fourni avec le JDK (`$JAVA_HOME/bin`).

> **Sauvegarde ce fichier et ses mots de passe hors du dépôt.** Avec Play App Signing
> (voir plus bas) une clé d'upload perdue se remplace en ouvrant un ticket chez Google,
> mais ça prend des jours. Sans Play App Signing, une clé perdue signifie qu'on ne peut
> **plus jamais** mettre l'app à jour — il faut republier sous un autre nom de paquet.

## 2. La déclarer localement

À la racine, un fichier `keystore.properties` — **déjà ignoré par git** :

```properties
storeFile=labarre-upload.jks
storePassword=…
keyAlias=labarre
keyPassword=…
```

Le `.jks` lui-même est ignoré aussi. Ne les commite ni l'un ni l'autre.

## 3. Construire

```bash
./gradlew bundleRelease   # AAB — c'est ce que Play demande
./gradlew assembleRelease # APK — pour distribuer hors Play
```

L'AAB sort dans `app/build/outputs/bundle/release/`.

Pense à monter `versionCode` dans [app/build.gradle.kts](app/build.gradle.kts) à chaque
envoi : Play refuse deux fois le même.

> La clé de ce projet est déjà créée : `labarre.jks` à la racine, mots de passe dans
> `keystore.properties`. Voir [RELEASING.md](RELEASING.md) pour construire et publier.

## 4. Play App Signing

À la première publication, Play propose de gérer la clé de signature à ta place. La clé
créée ci-dessus devient alors ta **clé d'upload** (celle qui prouve que c'est bien toi
qui envoies), et Google détient la clé de signature réelle. C'est le mode par défaut
depuis 2021 et il vaut mieux l'accepter : c'est le seul qui rend une clé perdue
récupérable.

## Le vrai obstacle : c'est un service d'accessibilité

Il faut le savoir avant d'investir du temps dans la fiche Play.

La Barre fonctionne via un `AccessibilityService`. C'est la seule API Android qui permet
à une app tierce de déclencher *précédent*, *accueil* et *applis récentes* — il n'y a pas
d'alternative. Mais Google **restreint activement** cette API aux applications qui
servent réellement des personnes en situation de handicap, et supprime celles qui s'en
servent pour autre chose.

Concrètement, à la publication :

- Play affiche un formulaire de déclaration obligatoire pour l'usage de
  l'`AccessibilityService` : il faut décrire la fonction et justifier qu'aucune autre
  API ne convient.
- Les apps de personnalisation d'interface qui passent par cette API sont **régulièrement
  refusées ou retirées**. Ça arrive à des lanceurs et des barres de navigation
  alternatives depuis des années.
- Le service ne lit aucun contenu d'écran ici (`canRetrieveWindowContent="false"`,
  `accessibilityFlags` minimal), et ça se plaide — mais ça ne garantit rien.

**Les voies qui marchent en pratique**, par ordre de friction :

1. **APK direct** (site, GitHub Releases) — aucune revue, aucun risque de retrait.
   C'est ce que font la plupart des projets de ce type.
2. **F-Droid / IzzyOnDroid** — pas de politique d'accessibilité restrictive.
3. **Play** — possible, mais prévois la déclaration, un texte de fiche qui explique
   clairement pourquoi l'API est nécessaire, et l'éventualité d'un refus.

Rien de tout ça n'est bloquant aujourd'hui : signer proprement est utile quelle que soit
la voie choisie, et l'APK signé se distribue tout de suite hors Play.
