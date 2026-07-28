# Publier l'APK sur GitHub

GitHub Releases est la voie la plus directe pour cette app : aucune revue, aucun risque
de retrait, et pas la politique d'accessibilité de Play (voir [SIGNING.md](SIGNING.md)).

Il faut d'abord une clé de signature. **Ne distribue pas l'APK de debug** : il est signé
avec la clé de debug du SDK Android, que tout le monde possède — n'importe qui peut donc
fabriquer une mise à jour qu'Android acceptera comme venant de toi.

---

## 1. Créer la clé (une seule fois, à garder à vie)

```bash
keytool -genkeypair -v \
  -keystore labarre.jks \
  -alias labarre \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

Hors de Play, **il n'y a aucune récupération possible**. Si tu perds ce fichier ou son
mot de passe, tu ne peux plus jamais publier de mise à jour : Android refusera
d'installer par-dessus un APK signé par une autre clé. Sauvegarde-le ailleurs que dans
le dépôt — gestionnaire de mots de passe, disque chiffré, ce que tu veux, mais deux
copies.

## 2. Le déclarer localement

`keystore.properties` à la racine — déjà ignoré par git, comme le `.jks` :

```properties
storeFile=labarre.jks
storePassword=…
keyAlias=labarre
keyPassword=…
```

---

## Voie A — à la main (la plus simple pour la première fois)

```bash
# 1. monter la version dans app/build.gradle.kts
#    versionCode = 2 ; versionName = "1.0.1"

# 2. construire
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk

# 3. s'authentifier une fois
gh auth login

# 4. taguer et publier
git tag v1.0.0
git push origin v1.0.0
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "La Barre v1.0.0" \
  --generate-notes
```

Sans le CLI `gh` : sur GitHub, **Releases → Draft a new release**, choisir le tag,
glisser l'APK dans *Attach binaries*.

Vérifie que le fichier s'appelle bien `app-release.apk` et **pas**
`app-release-unsigned.apk` — ce dernier veut dire que `keystore.properties` n'a pas été
trouvé, et il ne s'installe pas.

---

## Voie B — automatique (recommandée ensuite)

[`.github/workflows/release.yml`](.github/workflows/release.yml) construit et publie
l'APK signé à chaque tag. Il faut lui donner la clé, une fois.

Encoder le keystore en base64 :

```bash
# Linux / macOS / Git Bash
base64 -w0 labarre.jks > labarre.jks.b64

# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("labarre.jks")) | Set-Content labarre.jks.b64
```

Puis dans **Settings → Secrets and variables → Actions → New repository secret** :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | le contenu de `labarre.jks.b64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `labarre` |
| `KEY_PASSWORD` | mot de passe de la clé |

Supprime `labarre.jks.b64` ensuite : c'est ta clé en clair.

Ensuite, publier tient en deux commandes :

```bash
git tag v1.0.1
git push origin v1.0.1
```

Le workflow échoue explicitement si un secret manque, plutôt que de publier un APK non
signé sans que ça se voie.

---

## À chaque version

- **Monter `versionCode`** dans [app/build.gradle.kts](app/build.gradle.kts). Android
  refuse d'installer par-dessus un `versionCode` égal ou inférieur — c'est l'erreur la
  plus courante quand une mise à jour « ne s'installe pas ».
- Toujours **la même clé**. Changer de clé oblige les gens à désinstaller d'abord, ce
  qui efface leurs réglages.

## Ce que voient les gens qui installent

Android bloque par défaut l'installation hors Play. Au premier APK, le téléphone
proposera d'autoriser la source (le navigateur, ou le gestionnaire de fichiers) —
c'est normal et ça ne se demande qu'une fois par source.

Ça vaut la peine de le dire dans les notes de version, avec le rappel que la barre
demande ensuite d'activer son service d'accessibilité.
