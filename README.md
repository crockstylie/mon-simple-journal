# 📓 Mon Simple Journal

**Mon Simple Journal** est une application Android de journal quotidien minimaliste, **100 % hors-ligne**, conçue pour écrire sans aucune distraction dès l'ouverture de l'application, retrouver instantanément n'importe quel souvenir grâce à une recherche par occurrence précise (insensible aux accents), et conserver la maîtrise totale de ses écrits au format universel **Markdown (`.md`)** et **Archive (`.zip`)**.

---

## ✨ Fonctionnalités principales

### ✍️ Écriture immédiate, Horodatage & Continuité naturelle
* **Ouverture directe sur la page du jour** : dès que vous lancez l'application, après un Splash Screen sobre reprenant l'icône XML minimaliste, vous êtes directement sur votre page d'écriture du jour.
* **Titre automatique intelligent** : la date du jour en toutes lettres accompagnée du nom du jour (ex. *« Lundi 9 novembre 2026 »*) s'inscrit automatiquement en titre. Le format est personnalisable dans les paramètres.
* **Même journal toute la journée & Horodatage en un clic** : chaque fois que vous quittez et rouvrez l'application dans la journée, vous retrouvez le journal du jour avec sauvegarde automatique instantanée, et un bouton discret permet d'insérer l'heure courante (`— 22:15 —`) au curseur.
* **Continuité après minuit (23h55 ➔ 00h00)** : si vous commencez à écrire tard le soir à 23h55 et que minuit passe pendant votre session, l'application ne vous interrompt pas et continue dans le même journal.
* **Écran maintenu actif** : l'écran ne s'éteint pas pendant que vous laissez votre téléphone posé devant vous pour réfléchir.

### 🔒 Journaux passés en Lecture Seule & Verrouillage Biométrique optionnel
* Conformément à l'esprit d'un véritable journal papier, les journaux des jours passés s'ouvrent **strictement en lecture seule** afin de préserver l'authenticité de vos écrits passés sans risque de modification accidentelle.
* Option de **verrouillage biométrique / code du téléphone** dans les paramètres pour protéger l'accès à vos écrits.

### 🔍 Recherche d'occurrences sans accents & Saut direct (`▲` / `▼`)
* Recherchez n'importe quel mot ou expression à travers l'ensemble de vos journaux, **même sans taper les accents** (chercher `"ete"` trouve `"été"` et `"Été"`).
* Visualisez chaque occurrence sous forme d'une **liste détaillée de liens avec extrait du contexte**.
* Touchez une occurrence : l'application ouvre le journal correspondant, **fait défiler automatiquement la page jusqu'à l'endroit exact du mot**, le surligne et affiche une barre discrète (`2/5 [▲] [▼]`) pour passer d'une occurrence à l'autre.

### 🎨 Thème Dark/Light Automatique, Contrastes (*Hungry Cat Nonogram*) & Police ajustable
* **Respect du thème Système (Dark / Light)** : s'adapte automatiquement au mode clair ou sombre de votre smartphone par défaut.
* **Presets de contrastes reposants** inspirés de *Hungry Cat Nonogram* : **Noir profond**, **Gris ardoise**, **Gris papier**, **Blanc pur** et **Sépia**.
* **Personnalisation totale** : choisissez vous-même la couleur exacte du fond et du texte via la roue chromatique ou un code Hexadécimal (`#RRGGBB`), et ajustez la **taille de la police** (*Petit, Normal, Grand, Très grand*).

### 📦 Sauvegarde universelle `.md`, Migration `.zip` & Fusion sans perte
* **Format Markdown (`.md`)** : exportez vos journaux au format `.md` standard pour les ouvrir sur ordinateur ou dans n'importe quel logiciel tiers (Obsidian, Notion, Typora, éditeur de texte).
* **Archive `.zip` en un clic avec fusion intelligente** : exportez l'intégralité de vos journaux dans un simple fichier `.zip` et réimportez-le sur votre nouveau téléphone sans jamais écraser vos notes existantes.

---

## 🛠️ Stack Technique & Architecture

* **Namespace / Application ID** : `fr.antoinehory.monsimplejournal`
* **Langage** : Kotlin 2.2 (`jvmTarget = 11`)
* **UI** : Jetpack Compose (Material 3), Edge-to-Edge, thèmes de contraste dynamiques (`MonSimpleJournalTheme`), icône vectorielle XML & Splash Screen Android 12+ (`core-splashscreen`).
* **Architecture** : MVVM + Clean Architecture (`ui`, `domain`, `data`)
* **Persistance** : Room Database (`AppDatabase`, Kotlin Coroutines & `Flow`) + DataStore Preferences
* **Sécurité & Fichiers** : AndroidX Biometric + Storage Access Framework (SAF) + `java.util.zip` natif (zéro permission de stockage requise)

---

## 🚀 Compilation et Installation

### Prérequis
* **Android Studio** (Meerkat ou plus récent)
* **JDK 17+**
* **Android SDK 37** (`minSdk = 29` / Android 10+)

### Commandes Gradle
```bash
# Compiler l'APK de debug
./gradlew :app:assembleDebug

# Lancer les tests unitaires
./gradlew :app:testDebugUnitTest
```

---

## 🔒 Confidentialité

**Mon Simple Journal** fonctionne **100 % hors-ligne** et ne demande aucune permission système intrusive. Vos écrits vous appartiennent intégralement et ne quittent jamais votre téléphone. Consultez la [Politique de Confidentialité](POLITIQUE_DE_CONFIDENTIALITE.md) pour plus de détails.

---

## 👤 Auteur

Développé avec passion par **Antoine « Crock » Hory**
* 🌐 Site web : [antoinehory.fr](https://antoinehory.fr)
* 📧 Contact : [contact@antoinehory.fr](mailto:contact@antoinehory.fr)
* ☕ Soutenir le projet : [PayPal.me/kuroku](https://paypal.me/kuroku)
