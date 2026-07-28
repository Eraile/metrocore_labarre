# Publier une version

La clé de signature est **locale et le reste**. Rien de secret ne part sur GitHub : on
construit l'APK ici, on n'envoie que le binaire.

## La clé

`labarre.jks` à la racine, avec ses mots de passe dans `keystore.properties`. Les deux
sont ignorés par git — vérifiable à tout moment :

```bash
git check-ignore -v labarre.jks keystore.properties
```

> **Sauvegarde ces deux fichiers hors du projet, maintenant.**
> Hors de Play il n'existe aucune récupération. Perdre la clé ou son mot de passe
> signifie que tu ne peux plus jamais publier de mise à jour : Android refuse
> d'installer par-dessus un APK signé par une autre clé. Les gens devraient
> désinstaller — et perdraient leurs réglages.
>
> Deux copies, ailleurs : gestionnaire de mots de passe, disque chiffré, clé USB.

Empreinte du certificat actuel, pour pouvoir vérifier plus tard qu'il s'agit bien de la
même clé :

```
SHA-256  fe:b0:fe:04:ca:39:d0:df:be:90:eb:5e:c1:c6:8a:ad:eb:5c:78:4e:c6:0d:9b:52:4a:3d:8c:d9:1e:09:56:a5
DN       CN=NoMercy Studios, O=NoMercy Studios, L=Bayonne, C=FR
```

## Construire

```bash
./gradlew assembleRelease
```

Sort `app/build/outputs/apk/release/app-release.apk`.

Vérifier que c'est bien signé — le nom du fichier suffit à trancher :
`app-release-**unsigned**.apk` veut dire que `keystore.properties` n'a pas été trouvé,
et il ne s'installera pas.

```bash
# verification complete
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## Publier sur GitHub

Le dossier `dist/` (ignoré par git) contient l'APK renommé, prêt à envoyer.

**Par le web**, sans rien installer :
GitHub → **Releases** → *Draft a new release* → *Choose a tag* → `v1.0.0` (*Create new
tag on publish*) → glisser `dist/la-barre-v1.0.apk` dans *Attach binaries* → *Publish*.

**Par le CLI**, si tu installes `gh` un jour :

```bash
gh auth login                       # une fois
git tag v1.0.0 && git push origin v1.0.0
gh release create v1.0.0 dist/la-barre-v1.0.apk \
  --title "La Barre v1.0.0" --generate-notes
```

## À chaque nouvelle version

1. **Monter `versionCode`** dans [app/build.gradle.kts](app/build.gradle.kts), et
   `versionName` si tu veux. Android refuse d'installer par-dessus un `versionCode` égal
   ou inférieur — c'est l'explication de la plupart des « la mise à jour ne s'installe
   pas ».
2. `./gradlew assembleRelease`
3. Copier dans `dist/` sous un nom versionné, publier la Release.

Toujours **la même clé**, sinon la mise à jour ne s'installera pas par-dessus.

## Et l'automatisation ?

Un workflow GitHub Actions pourrait construire et publier tout seul, mais il faudrait
déposer la clé (encodée) dans les secrets du dépôt. C'est refusé ici volontairement : la
clé ne quitte pas la machine. La contrepartie est ces trois commandes à chaque version.

Si tu changes d'avis un jour, le principe est : `base64` du `.jks` + les mots de passe
dans *Settings → Secrets and variables → Actions*, et un job qui reconstruit
`keystore.properties` avant `assembleRelease`.
