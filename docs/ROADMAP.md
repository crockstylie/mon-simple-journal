# ROADMAP.md — Suivi d'Avancement du Projet

> Ce fichier trace l'état exact du développement de **Mon Simple Journal**. L'agent IA doit le consulter pour savoir où en est le projet et le mettre à jour après chaque étape terminée.

Légende : `[x]` Terminé | `[/]` En cours | `[ ]` À faire

---

## Phase 1 : Socle, Dépendances, Icône XML & Splash Screen
- [x] Mise en place de la documentation "Source de Vérité" (`AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`)
- [x] Initialisation du projet Android `fr.antoinehory.monsimplejournal` (Kotlin + Jetpack Compose + Material 3)
- [x] Configuration complète de `gradle/libs.versions.toml` et `app/build.gradle.kts` :
    - Jetpack Compose BOM (`2026.02.01`) + Navigation Compose + Material Icons Extended
    - Room (`2.8.5`) + plugin KSP (`2.3.13`)
    - DataStore Preferences (`1.1.3`)
    - Core SplashScreen (`1.2.0`)
    - Biometric (`1.1.0`)
- [x] Création de l'icône d'application minimaliste en XML pur (`ic_launcher_foreground.xml` / `ic_launcher_background.xml`) et configuration du Splash Screen (`themes.xml` + `installSplashScreen()`) reprenant l'icône

## Phase 2 : Domaine, Formatage de Date, Normalisation sans Accents & Persistance (Room + DataStore)
- [x] Création des modèles métier dans `domain/` :
    - `JournalEntry` (`isoDate`, `epochDay`, `content`, `createdAt`, `updatedAt`)
    - `JournalDateFormatter` (support de plusieurs formats de date en toutes lettres + nom du jour, ex. *"Lundi 9 novembre 2026"*, *"9 novembre 2026 — Lundi"*, *"Lundi 9 nov. 2026"*, ainsi que l'horodatage `— HH:mm —`)
    - `ContrastPalette`, `ThemeMode` (Automatique Système Dark/Light, Presets *Hungry Cat Nonogram* : Noir, Gris Ardoise, Gris Papier, Blanc, Sépia, et Personnalisé) & `FontScaleOption` (Petit, Normal, Grand, Très grand)
    - `SearchOccurrence` & `DiacriticsMatcher` (recherche d'occurrences insensible aux accents `NFD` et à la casse avec préservation exacte des `charOffset`)
- [x] Création de la couche Room (`data/local/`) :
    - `JournalEntryEntity` et fonctions de mapping `toDomain()` / `toEntity()`
    - `JournalDao` (observation du jour, liste des journaux passés, observation complète pour recherche normalisée, `upsert` et `upsertAll`)
    - `AppDatabase` (`mon_simple_journal.db`)
- [x] Création de `UserPreferencesRepository` (DataStore) :
    - Format de date du titre
    - Mode de thème (`SYSTEM_AUTO` par défaut suivant le Dark/Light du smartphone, ou Preset fixe, ou Couleurs personnalisées `backgroundColorArgb` / `textColorArgb`)
    - Taille de police (`FontScaleOption`)
    - Option `keepScreenOn` (activée à `true` par défaut)
    - Option `biometricLockEnabled` (désactivée à `false` par défaut)
- [x] Création de `JournalRepository` avec protection d'immuabilité des journaux passés et fusion intelligente lors de l'import

## Phase 3 : Écran Principal d'Écriture du Jour (`ui/editor/`)
- [x] Création de `EditorUiState`, `EditorUiEvent` et `EditorViewModel` :
    - Ouverture automatique sur le journal du jour (`LocalDate.now(clock)`)
    - Verrouillage de `sessionDate` pour gérer le passage de `23h55` à `00h00` sans interruption
    - Reprise transparente du même journal toute la journée si on quitte et relance l'application
    - Action d'insertion de l'heure courante (`— HH:mm —`) à la position du curseur
    - Sauvegarde automatique avec `debounce(300ms)` + sauvegarde immédiate sur `ON_STOP`
- [x] Création de `EditorScreen` :
    - En-tête minimaliste avec la date du jour en toutes lettres en guise de titre automatique
    - Bouton discret « Insérer l'heure » + accès aux 3 volets secondaires (Archives, Recherche, Paramètres)
    - Zone de traitement de texte simple, fluide et plein écran (`imePadding`)
    - Application de `Modifier.keepScreenOn()` lorsque l'option est active

## Phase 4 : Archives & Consultation en Lecture Seule (`ui/archive/`)
- [x] Création de `ArchiveViewModel` et `ArchiveScreen` listant les journaux des jours précédents par ordre antéchronologique (avec aperçu de la première ligne et nombre de mots)
- [x] Création de `ReaderScreen` (vue d'un journal passé) :
    - Mode **100 % Lecture Seule** (aucune modification possible, pas d'ouverture clavier, texte sélectionnable pour copie)
    - Bouton d'export individuel de ce journal en `.md`

## Phase 5 : Recherche d'Occurrences sans Accents, Saut Direct & Barre `▲` / `▼` (`ui/search/`)
- [x] Implémentation de l'extracteur d'occurrences `DiacriticsMatcher` dans `JournalRepository` / `SearchViewModel` :
    - Découpage de chaque occurrence du mot recherché (insensible aux accents et à la casse) dans tous les journaux
    - Génération de la liste détaillée de liens avec extrait contextuel et mot mis en gras/couleur
- [x] Création de `SearchScreen` :
    - Barre de recherche instantanée avec compteur du nombre total d'occurrences trouvées
    - Liste cliquable d'occurrences
- [x] Implémentation du saut direct (`Jump-to-Occurrence`) et de la barre flottante `2/5 [▲] [▼] [✕]` dans `ReaderScreen` et `EditorScreen` :
    - Surlignage des occurrences via `AnnotatedString` (avec mise en évidence distincte de l'occurrence active)
    - Calcul de la position via `TextLayoutResult.getBoundingBox(targetCharOffset)` et défilement automatique (`animateScrollTo`) jusqu'à l'endroit exact du mot
    - Boutons `▲` (occurrence précédente) et `▼` (occurrence suivante) pour naviguer dans le même journal

## Phase 6 : Paramètres, Thèmes (Dark/Light + Presets + Custom), Biométrie & Sauvegarde `.md` / `.zip`
- [x] Mise à jour de `MonSimpleJournalTheme` (`ui/theme/Theme.kt`) :
    - Support du mode Système Dark/Light (`isSystemInDarkTheme()`), des presets *Hungry Cat Nonogram*, des couleurs personnalisées, de la taille de police et de l'adaptation automatique du contraste des barres système (`isAppearanceLightStatusBars`)
- [x] Création de `SettingsScreen` :
    - Sélecteur du format de date du titre avec aperçu en direct
    - Sélecteur de thème : **Automatique (Dark/Light du téléphone)**, **Presets *Hungry Cat Nonogram*** (Noir, Gris ardoise, Gris papier, Blanc, Sépia) et **Color Picker personnalisé** (roue HSV + code Hex `#RRGGBB` pour fond et texte)
    - Sélecteur de la taille du texte (*Petit*, *Normal*, *Grand*, *Très grand*)
    - Switch « Garder l'écran actif pendant l'écriture »
    - Switch « Verrouillage biométrique / code du téléphone »
- [x] Création de `MarkdownZipManager` (`data/backup/`) :
    - Export d'un ou de tous les journaux en fichiers `.md`
    - Export complet en une archive `.zip` unique (`YYYY-MM-DD.md`)
    - Import d'une archive `.zip` (ou de fichiers `.md`) avec **fusion intelligente sans perte** (`\n\n---\n\n`) si une date existe déjà localement avec un texte différent
- [x] Création de `InfoScreen` (À propos, liens développeur, notation Play Store et PayPal) + traduction 4 langues (`fr`, `en`, `es`, `pt`)

## Phase 7 : Tests Unitaires, Stabilisation & Finitions
- [x] Tests unitaires JVM :
    - Continuité d'écriture au passage de minuit (`23h55` ➔ `00h00`) avec `Clock` simulée
    - Garantie de lecture seule sur les journaux antérieurs
    - Recherche insensible aux accents (`"ete"` ➔ `"été"`, `"Été"`) et exactitude des `charOffset`
    - Cycle complet d'export `.zip` puis réimport `.zip` avec test de fusion sans perte et protection *Zip Slip*
- [x] Tests instrumentés de `JournalDao`
- [x] Audit de performance Compose et vérification Edge-to-Edge avec clavier ouvert
