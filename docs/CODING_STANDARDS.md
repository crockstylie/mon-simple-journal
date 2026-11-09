# CODING_STANDARDS.md — Standards de Code & Best Practices

> Ce document définit les conventions de code, les idiomes Kotlin/Compose et les bonnes pratiques obligatoires pour garantir un code propre, performant, testable et maintenable sur **Journal**.

---

## 1. Conventions Kotlin & Style

- **Langue du code** :
    - Identifiants (classes, fonctions, propriétés, paramètres, tables SQL) en **anglais** clair et explicite (`JournalEntryEntity`, `SearchOccurrence`, `sessionDate`, `isReadOnly`).
    - Documentation KDoc et commentaires explicatifs en **français** (ou anglais technique cohérent).
- **Immuabilité par défaut** :
    - Toujours privilégier `val` à `var`.
    - Utiliser des collections immuables (`List`, `Set`, `Map`) dans les modèles exposés à l'UI.
- **Null-safety & Validation** :
    - Proscrire l'opérateur `!!` (non-null assertion). Utiliser `?:`, `let`, `requireNotNull()` ou des retours anticipés (`?: return`).
- **Injection de l'horloge (`java.time.Clock`)** :
    - Ne jamais appeler `LocalDate.now()` ou `System.currentTimeMillis()` en dur dans les ViewModels ou Repositories sans passer par une instance `Clock = Clock.systemDefaultZone()` injectée au constructeur. Cela est indispensable pour tester unitairement le passage de minuit (`23h55` ➔ `00h00`).
- **Gestion des erreurs** :
    - Utiliser `runCatching { ... }` / `Result<T>` ou des hiérarchies scellées (`sealed interface`) pour représenter les états d'export/import `.zip`.
    - Ne jamais avaler une `CancellationException` dans une coroutine.

---

## 2. Coroutines, Concurrence & Threading

- **Injection des Dispatchers** :
    - Ne jamais coder en dur `Dispatchers.IO` ou `Dispatchers.Default` à l'intérieur des classes métier/repositories ; les passer au constructeur (avec valeur par défaut) pour faciliter les tests unitaires.
- **Isolation du Main Thread (Zéro ANR)** :
    - **Strictement interdit sur le Main Thread** : requêtes SQLite/Room, écriture DataStore, calcul d'indexation des occurrences sur de longs textes, et lecture/écriture de fichiers `.md` ou `.zip` via `ContentResolver`.
- **Sauvegarde automatique (`Debounce` + `Flush on Stop`)** :
    - Dans `EditorViewModel`, les frappes clavier mettent à jour l'état UI immédiatement en mémoire pour une latence zéro, et déclenchent une persistance Room avec un `debounce(300L)` sur `Dispatchers.IO`.
    - Appeler systématiquement une sauvegarde immédiate (`flushImmediate()`) lorsque l'écran passe en arrière-plan (`Lifecycle.Event.ON_STOP`) pour garantir zéro perte de texte si l'utilisateur ferme l'application juste après avoir tapé un mot.

---

## 3. Jetpack Compose & UI Best Practices

- **State Hoisting (Hissage d'état)** :
    - Séparer systématiquement chaque écran en deux niveaux :
        1. `EditorRoute(viewModel: EditorViewModel, ...)` : collecte les `StateFlow` avec `collectAsStateWithLifecycle()`.
        2. `EditorScreen(uiState: EditorUiState, onEvent: (EditorUiEvent) -> Unit, modifier: Modifier = Modifier)` : Composable **stateless**, pur, facile à prévisualiser (`@Preview`) et à tester.
- **Minimalisme & Concentration sur l'écriture** :
    - L'écran d'accueil est l'éditeur du jour lui-même : pas d'écran d'accueil intermédiaire, pas de tableau de bord surchargé.
    - Le titre affiche automatiquement la date du jour formatée selon le choix de l'utilisateur (ex. `Lundi 9 novembre 2026`).
    - Les icônes de navigation (Historique, Recherche, Paramètres) restent discrètes dans l'en-tête pour laisser 90 % de l'écran au texte.
- **Maintien de l'écran allumé (`Keep Screen On`)** :
    - Lorsque l'option est active dans les préférences, appliquer `Modifier.keepScreenOn()` sur le conteneur racine de `EditorScreen` afin que l'écran ne s'assombrisse pas pendant que l'utilisateur réfléchit.
- **Saut direct et surlignage d'occurrence (`TextLayoutResult`)** :
    - Dans `ReaderScreen` (et `EditorScreen` si ouvert depuis une recherche), utiliser `AnnotatedString` (`SpanStyle(background = highlightColor)`) pour mettre en évidence toutes les occurrences du mot recherché, et une couleur d'accent plus marquée sur l'occurrence cible (`targetCharOffset`).
    - Utiliser le callback `onTextLayout = { layoutResult -> ... }` pour récupérer le rectangle `layoutResult.getBoundingBox(targetCharOffset)` et déclencher `scrollState.animateScrollTo(rect.top.toInt())` dans un `LaunchedEffect`.
- **Respect strict de la Lecture Seule sur les journaux passés** :
    - Pour tout journal dont `isoDate != activeSessionIsoDate`, utiliser un `Text` sélectionnable (`SelectionContainer`) et **non** un `TextField` éditable, afin d'empêcher toute modification accidentelle et toute ouverture intempestive du clavier.
- **Edge-to-Edge & Clavier (`IME`)** :
    - Utiliser `Modifier.imePadding()` et `WindowInsets.safeDrawing` sur l'éditeur pour que le curseur de texte reste toujours visible au-dessus du clavier virtuel.

---

## 4. Persistance Locale (Room & DataStore)

- **Séparation Entité / Modèle de Domaine** :
    - Les classes `@Entity` (`JournalEntryEntity`) restent dans `data/local/entity/`.
    - Fournir des fonctions d'extension `toDomain()` et `toEntity()` pour ne jamais coupler les Composables aux annotations Room.
- **Protection en base contre la modification du passé** :
    - Dans `JournalRepository`, la méthode `saveActiveDayContent(sessionDate: LocalDate, content: String)` ne doit autoriser l'écriture que sur la `sessionDate` active. Seule la méthode dédiée `importEntriesFromBackup(entries: List<JournalEntry>)` peut insérer ou mettre à jour des dates antérieures.

---

## 5. Sécurité & Fiabilité de l'Export / Import (`.md` & `.zip`)

- **Zéro Permission de Stockage (`Storage Access Framework`)** :
    - Utiliser exclusivement `ActivityResultContracts.CreateDocument("application/zip")`, `CreateDocument("text/markdown")` et `ActivityResultContracts.OpenDocument()` avec le `ContentResolver`. Ne déclarer aucune permission `READ_EXTERNAL_STORAGE` ou `WRITE_EXTERNAL_STORAGE`.
- **Protection contre la vulnérabilité *Zip Slip*** :
    - Lors de la lecture d'une archive `.zip` avec `ZipInputStream`, vérifier systématiquement que le nom de chaque `ZipEntry` ne contient pas de séquence de traversée de répertoire (`..` ou `/`) et correspond bien à un fichier `.md` valide avant de le parser.
- **Encodage UTF-8 strict** :
    - Toujours spécifier explicitement `Charsets.UTF_8` lors de la lecture et de l'écriture des flux Markdown dans le `.zip` afin de préserver parfaitement les accents français et les caractères spéciaux sur tous les systèmes.