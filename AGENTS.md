# AGENTS.md — Source de Vérité Principale (Mon Simple Journal)

> **RÈGLE CRITIQUE POUR L'AGENT IA :**
> Ce fichier est ton point d'entrée obligatoire. Pour préserver ta fenêtre de contexte et garantir un code cohérent, **ne lis pas l'intégralité du codebase à l'aveugle**. Consulte d'abord cet index et uniquement le fichier de documentation ou le Skill correspondant à ta tâche.

---

## 1. Index des Sources de Vérité

| Fichier | Rôle | Quand le lire / mettre à jour ? |
| :--- | :--- | :--- |
| [`AGENTS.md`](./AGENTS.md) | Règles globales, stack technique, skills, garde-fous | Au début de chaque tâche |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | Structure des packages, flux de données, index des fichiers | Avant de créer/modifier une classe ou l'architecture |
| [`docs/CODING_STANDARDS.md`](./docs/CODING_STANDARDS.md) | Standards Kotlin/Compose, bonnes pratiques Room/DataStore/SAF | Avant d'écrire ou refactoriser du code |
| [`docs/ROADMAP.md`](./docs/ROADMAP.md) | État d'avancement, tâches faites / en cours / à venir | Au début et à la fin de chaque fonctionnalité |
| [`docs/DECISIONS.md`](./docs/DECISIONS.md) | Décisions techniques (ADR), gestion du minuit, thèmes, recherche & ZIP | Lors d'un choix technique ou d'un arbitrage |
| [`README.md`](./README.md) | Présentation générale du projet, fonctionnalités et commandes Gradle | Lors d'un changement majeur de fonctionnalité ou de build |
| [`CHANGELOG.md`](./CHANGELOG.md) | Historique des versions et nouveautés | À chaque livraison de version |
| [`PLAY_STORE.md`](./PLAY_STORE.md) | Textes Google Play Console traduits en 4 langues (`fr`, `en`, `es`, `pt`) | Avant publication sur le Play Store |
| [`POLITIQUE_DE_CONFIDENTIALITE.md`](./POLITIQUE_DE_CONFIDENTIALITE.md) | Politique de confidentialité officielle (100 % hors-ligne) | En cas d'évolution touchant aux données ou permissions |

---

## 2. Protocole d'Économie de Contexte

1. **Ciblage chirurgical** : Utilise la cartographie dans [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) pour identifier directement le fichier concerné sans scanner tout le projet.
2. **Lectures partielles** : Utilise les outlines de fichiers (`view_file_outline`) ou des plages de lignes (`startLine`/`endLine`) sur les fichiers volumineux.
3. **Mise à jour continue** :
    - Dès qu'un nouveau fichier Kotlin est créé ou déplacé, mets à jour la cartographie dans [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md).
    - Dès qu'une étape est validée, coche-la dans [`docs/ROADMAP.md`](./docs/ROADMAP.md).
    - Dès qu'une décision structurante est prise, ajoute une entrée concise dans [`docs/DECISIONS.md`](./docs/DECISIONS.md).

---

## 3. Skills Agentiques à Activer selon la Tâche

Avant d'implémenter l'un des sujets ci-dessous, **lis le fichier `SKILL.md` correspondant** via `read_file` pour appliquer les patterns officiels Android Studio :

| Domaine d'intervention | Skill à activer | Chemin du Skill |
| :--- | :--- | :--- |
| **Insets & Affichage plein écran** (Barre d'état/navigation, gestion du clavier `imePadding`) | `edge-to-edge` | `C:\Users\hory\.android-studio\skills\edge-to-edge\SKILL.md` |
| **Performance UI Compose** (Stabilité, recompositions lors de la frappe et du défilement) | `jetpack-compose-performance` | `C:\Users\hory\.android-studio\skills\jetpack-compose-performance\SKILL.md` |
| **Audit ANR & Threading** (Sauvegarde auto Room, recherche plein texte, export/import `.zip`) | `anr-audit-and-fix` | `C:\Users\hory\.android-studio\skills\anr-audit-and-fix\SKILL.md` |
| **UI Adaptative** (Rotation d'écran, tablettes, pliables) | `adaptive` | `C:\Users\hory\.android-studio\skills\adaptive\SKILL.md` |
| **Tests Unitaires & UI** (JUnit, Compose UI Test, DAO tests, Zip/Markdown tests) | `testing-setup` | `C:\Users\hory\.android-studio\skills\testing-setup\SKILL.md` |

---

## 4. Stack Technique de Référence

- **Nom de l'application** : `Mon Simple Journal`
- **Package racine / Namespace** : `fr.antoinehory.monsimplejournal`
- **SDK** : `minSdk = 29`, `targetSdk = 37`, `compileSdk = 37`
- **Langage & Build** : Kotlin `2.2.10`, AGP `9.5.1`, KSP `2.3.12`, Java 11, Version Catalog (`gradle/libs.versions.toml`)
- **UI** : Jetpack Compose (BOM `2026.02.01`), Material 3, Edge-to-Edge activé, Thème dynamique multi-contrastes inspiré de *Hungry Cat Nonogram* (`MonSimpleJournalTheme`)
- **Persistance Locale (Entrées)** : AndroidX Room (`2.8.5`) — `room-runtime`, `room-ktx`, `room-compiler` (via KSP)
- **Préférences Utilisateur** : AndroidX DataStore Preferences (`1.1.3`) — format de date, preset de couleurs, couleurs personnalisées, maintien de l'écran actif
- **Import / Export (`.md` & `.zip`)** : Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`) + `java.util.zip` natif (zéro permission de stockage requise)

---

## 5. Garde-Fous Critiques (Résumé)

> Pour le détail complet des règles de code, consulter [`docs/CODING_STANDARDS.md`](./docs/CODING_STANDARDS.md).

1. **MVVM & UDF strict** : État immuable exposé via `StateFlow` (`collectAsStateWithLifecycle()`), Composables sans logique métier, E/S ou BDD.
2. **Ouverture directe & Continuité à minuit (23h55 ➔ 00h00)** :
    - Au lancement, l'application ouvre directement l'éditeur sur le journal du jour (`LocalDate.now(clock)`).
    - La date de l'entrée active est **verrouillée pour la session d'écriture en cours** : si l'utilisateur commence à écrire à 23h55 et que minuit passe, l'édition continue sans coupure dans le même journal.
    - Si l'utilisateur quitte et relance l'application au cours de la même journée, il retrouve et continue d'éditer le journal de cette journée.
3. **Immuabilité des journaux passés (Lecture seule stricte)** :
    - Seul le journal de la session du jour est modifiable.
    - Tous les journaux des jours précédents sont consultables **exclusivement en lecture seule** (`readOnly = true`, aucune modification possible via l'UI ou le ViewModel, hormis lors d'un import de sauvegarde `.zip` / `.md`).
4. **Sauvegarde automatique non-bloquante & Zéro perte** :
    - Sauvegarder automatiquement le texte au fil de la frappe (debounce `300 ms`) ainsi que lors du passage en arrière-plan (`Lifecycle.Event.ON_STOP`) sur `Dispatchers.IO`.
5. **Écran toujours actif (`Keep Screen On`)** :
    - Appliquer `Modifier.keepScreenOn()` sur l'écran d'édition selon le réglage utilisateur (activé par défaut) pour que l'écran ne s'éteigne pas pendant la réflexion/écriture.
