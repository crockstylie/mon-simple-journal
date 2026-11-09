# ROADMAP.md — Suivi d'Avancement du Projet

> Ce fichier trace l'état exact du développement de **Journal**. L'agent IA doit le consulter pour savoir où en est le projet et le mettre à jour après chaque étape terminée.

Légende : `[x]` Terminé | `[/]` En cours | `[ ]` À faire

---

## Phase 1 : Socle, Dépendances & Documentation IA
- [x] Mise en place de la documentation "Source de Vérité" (`AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`)
- [ ] Initialisation du projet Android `fr.antoinehory.journal` (Kotlin + Jetpack Compose + Material 3)
- [ ] Configuration de `gradle/libs.versions.toml` et `app/build.gradle.kts` :
    - Jetpack Compose BOM (`2026.02.01`) + Navigation Compose
    - Room (`2.8.5`) + plugin KSP (`2.3.12`)
    - DataStore Preferences (`1.1.3`)
    - Core SplashScreen (`1.2.0`)
- [ ] Création de l'icône d'application minimaliste et du Splash Screen sobre

## Phase 2 : Domaine, Formatage de Date & Persistance Locale (Room + DataStore)
- [ ] Création des modèles métier dans `domain/` :
    - `JournalEntry` (`isoDate`, `epochDay`, `content`, `createdAt`, `updatedAt`)
    - `JournalDateFormatter` (support de plusieurs formats de date en toutes lettres + nom du jour, ex. *"Lundi 9 novembre 2026"*, *"9 novembre 2026 — Lundi"*, *"Lundi 9 nov. 2026"*)
    - `ContrastPalette` & `ContrastPreset` (Noir, Gris Ardoise, Gris Papier, Blanc, Sépia, Personnalisé)
    - `SearchOccurrence` (`isoDate`, `formattedDate`, `occurrenceIndex`, `charOffset`, `matchLength`, `snippetBefore`, `matchText`, `snippetAfter`)
- [ ] Création de la couche Room (`data/local/`) :
    - `JournalEntryEntity` et fonctions de mapping `toDomain()` / `toEntity()`
    - `JournalDao` (observation du jour, liste des journaux passés, recherche plein texte, `upsert` et `upsertAll`)
    - `AppDatabase` (`journal.db`)
- [ ] Création de `UserPreferencesRepository` (DataStore) :
    - Format de date du titre
    - Preset de contraste actif + couleurs personnalisées (`backgroundColorArgb`, `textColorArgb`)
    - Option `keepScreenOn` (activée à `true` par défaut)
- [ ] Création de `JournalRepository` avec protection d'immuabilité des journaux passés

## Phase 3 : Écran Principal d'Écriture du Jour (`ui/editor/`)
- [ ] Création de `EditorUiState`, `EditorUiEvent` et `EditorViewModel` :
    - Ouverture automatique sur le journal du jour (`LocalDate.now(clock)`)
    - Verrouillage de `sessionDate` pour gérer le passage de `23h55` à `00h00` sans interruption
    - Reprise transparente du même journal toute la journée si on quitte et relance l'application
    - Sauvegarde automatique avec `debounce(300ms)` + sauvegarde immédiate sur `ON_STOP`
- [ ] Création de `EditorScreen` :
    - En-tête minimaliste avec la date du jour en toutes lettres en guise de titre automatique
    - Zone de traitement de texte simple, fluide et plein écran (`imePadding`)
    - Application de `Modifier.keepScreenOn()` lorsque l'option est active
    - Accès rapide et discret aux 3 volets secondaires : Archives (anciens journaux), Recherche et Paramètres

## Phase 4 : Archives & Consultation en Lecture Seule (`ui/archive/`)
- [ ] Création de `ArchiveViewModel` et `ArchiveScreen` listant les journaux des jours précédents par ordre antéchronologique (avec aperçu de la première ligne et nombre de mots)
- [ ] Création de `ReaderScreen` (vue d'un journal passé) :
    - Mode **100 % Lecture Seule** (aucune modification possible, pas d'ouverture clavier, texte sélectionnable pour copie)
    - Bouton d'export individuel de ce journal en `.md`

## Phase 5 : Recherche d'Occurrences & Saut Direct (`ui/search/`)
- [ ] Implémentation de l'extracteur d'occurrences dans `JournalRepository` / `SearchViewModel` :
    - Découpage de chaque occurrence du mot recherché dans tous les journaux
    - Génération de la liste détaillée de liens avec extrait contextuel et mot mis en gras/couleur
- [ ] Création de `SearchScreen` :
    - Barre de recherche instantanée avec compteur du nombre total d'occurrences trouvées
    - Liste cliquable d'occurrences
- [ ] Implémentation du saut direct (`Jump-to-Occurrence`) dans `ReaderScreen` et `EditorScreen` :
    - Surlignage des occurrences via `AnnotatedString`
    - Calcul de la position via `TextLayoutResult.getBoundingBox(targetCharOffset)` et défilement automatique (`animateScrollTo`) jusqu'à l'endroit exact du mot

## Phase 6 : Paramètres, Thèmes de Contraste & Sauvegarde `.md` / `.zip` (`ui/settings/` & `data/backup/`)
- [ ] Création de `SettingsScreen` :
    - Sélecteur du format de date du titre avec aperçu en direct
    - Sélecteur de fonds de contraste type *Hungry Cat Nonogram* (presets Noir, Gris, Blanc, Sépia) + Color Picker complet (roue HSV + code Hex `#RRGGBB`) pour choisir soi-même la couleur du fond et du texte
    - Switch « Garder l'écran actif pendant l'écriture »
- [ ] Création de `MarkdownZipManager` (`data/backup/`) :
    - Export d'un ou de tous les journaux en fichiers `.md`
    - Export complet en une archive `.zip` unique (`YYYY-MM-DD.md`)
    - Import d'une archive `.zip` (ou de fichiers `.md`) pour restaurer tous ses journaux lors d'un changement de téléphone
- [ ] Création de `InfoScreen` (À propos, liens développeur, notation Play Store et PayPal) + traduction 4 langues (`fr`, `en`, `es`, `pt`)

## Phase 7 : Tests Unitaires, Stabilisation & Finitions
- [ ] Tests unitaires JVM :
    - Continuité d'écriture au passage de minuit (`23h55` ➔ `00h00`) avec `Clock` simulée
    - Garantie de lecture seule sur les journaux antérieurs
    - Extraction exacte des offsets d'occurrences et extraits de recherche
    - Cycle complet d'export `.zip` puis réimport `.zip` en mémoire (vérification de l'intégrité du Markdown et protection *Zip Slip*)
- [ ] Tests instrumentés de `JournalDao`
- [ ] Audit de performance Compose et vérification Edge-to-Edge avec clavier ouvert