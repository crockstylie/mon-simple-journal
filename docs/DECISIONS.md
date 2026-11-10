# DECISIONS.md — Journal des Décisions Techniques (ADR)

> Ce fichier consigne les choix d'architecture et les contraintes techniques clés du projet **Mon Simple Journal** pour éviter de remettre en question des décisions déjà arbitrées.

---

## ADR-001 : Ouverture directe sur l'éditeur du jour, Horodatage & Ancrage de la date de session à 23h55
- **Date** : 2026-11-09
- **Contexte** : L'utilisateur souhaite que **Mon Simple Journal** s'ouvre instantanément sur la page d'écriture du jour avec la date en toutes lettres + le nom du jour en titre automatique. De plus, s'il écrit à `23h55` et que minuit (`00h00`) passe pendant qu'il écrit, le journal ne doit surtout pas se fermer ni basculer brutalement sur une page blanche du lendemain. Enfin, s'il quitte et revient dans la même journée, il doit retrouver le même journal et pouvoir horodater ses pensées.
- **Décision** :
    1. `MainActivity` démarre directement sur la destination `EditorRoute` sans écran intermédiaire.
    2. `EditorViewModel` capture une `sessionDate: LocalDate = LocalDate.now(clock)` à l'ouverture de la session et charge (ou crée à la première frappe) l'entrée Room ayant `isoDate = sessionDate.toString()`.
    3. Tant que la session d'écriture est active à l'écran, `sessionDate` reste verrouillée : le passage de `23h55` à `00h00` n'altère pas l'entrée en cours d'édition.
    4. Lorsque l'utilisateur quitte et relance l'application la même journée, `LocalDate.now(clock)` renvoie la même date `YYYY-MM-DD`, ce qui recharge automatiquement le journal du jour en mode édition.
    5. Un bouton discret « Insérer l'heure » dans la barre supérieure insère `— HH:mm —` à l'emplacement du curseur.
- **Conséquences** :
    - Zéro friction à l'ouverture, continuité totale à minuit et persistance transparente toute la journée.

## ADR-002 : Immuabilité stricte des journaux passés (Mode Lecture Seule)
- **Date** : 2026-11-09
- **Contexte** : Le principe d'un journal intime fidèle est qu'on écrit au présent et qu'on ne réécrit pas le passé après coup. Les journaux des jours précédents doivent être consultables mais jamais modifiables.
- **Décision** :
    1. Créer un écran distinct `ReaderScreen` (utilisant `SelectionContainer` + `Text`, sans `BasicTextField`) dédié à la consultation des entrées dont `isoDate < sessionDate`.
    2. Dans `JournalRepository`, séparer l'API d'écriture quotidienne (`updateSessionEntry`) de l'API de consultation (`observePastEntries`), en refusant toute modification unitaire d'une entrée antérieure à `sessionDate`.
- **Conséquences** :
    - Garantie fonctionnelle et UX qu'aucun ancien journal ne peut être altéré par erreur en le relisant ou en cliquant sur un résultat de recherche.

## ADR-003 : Documentation modulaire orientée IA & Catalogue de Skills (Source de Vérité)
- **Date** : 2026-11-09
- **Contexte** : Empêcher la saturation de la fenêtre de contexte de l'IA dans Android Studio (Antigravity) et maintenir une cohérence stricte tout au long du projet **Mon Simple Journal**.
- **Décision** : Structurer la connaissance du projet en fichiers Markdown légers et complémentaires (`AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`) et référencer explicitement les Skills Android Studio pertinents dans `AGENTS.md`.
- **Conséquences** :
    - L'IA lit d'abord `AGENTS.md` puis uniquement les fichiers ou Skills pertinents pour la tâche en cours.

## ADR-004 : Persistance locale avec AndroidX Room (Entrées) & DataStore (Préférences)
- **Date** : 2026-11-09
- **Contexte** : L'application est 100 % hors-ligne et doit stocker efficacement des centaines ou milliers d'entrées textuelles quotidiennes ainsi que les préférences visuelles de l'utilisateur.
- **Décision** :
    - Utiliser `androidx.room:room-runtime` et `room-ktx` (`2.8.5`) via KSP (`2.3.12`) pour les entrées de journal (`journal_entries` dans `mon_simple_journal.db`).
    - Utiliser `androidx.datastore:datastore-preferences` (`1.1.3`) pour stocker le format de date choisi, le mode de thème (Système Auto / Preset fixe / Personnalisé), les couleurs personnalisées (fond/texte), la taille de police, l'option `keepScreenOn` et l'option `biometricLockEnabled`.
- **Conséquences** :
    - Observation réactive de bout en bout via Kotlin `Flow` et `StateFlow`.

## ADR-005 : Thème Système Dark/Light, Presets *Hungry Cat Nonogram*, Couleurs Custom & Taille de Police
- **Date** : 2026-11-09
- **Contexte** : L'utilisateur souhaite que l'application respecte le mode clair/sombre du smartphone tout en proposant des fonds de contrastes (noir, gris, blanc, sépia) inspirés du jeu *Hungry Cat Nonogram*, la liberté de choisir soi-même ses propres couleurs, et le réglage de la taille du texte.
- **Décision** :
    1. Définir un mode par défaut **`SYSTEM_AUTO`** qui détecte `isSystemInDarkTheme()` sur le smartphone et applique automatiquement un preset clair le jour (*Gris Papier*) et un preset sombre la nuit (*Gris Ardoise*).
    2. Permettre à l'utilisateur de choisir un preset fixe (`ContrastPreset` : *Noir OLED*, *Gris Ardoise*, *Gris Papier*, *Blanc Pur*, *Sépia Monogramme*) ou le mode *Personnalisé* (roue HSV + code Hex `#RRGGBB` pour le fond et le texte).
    3. Adapter dynamiquement la couleur des icônes de la barre d'état et de navigation (`isAppearanceLightStatusBars`) en fonction de la luminance réelle de la couleur de fond choisie.
    4. Proposer 4 tailles de police (*Petit*, *Normal*, *Grand*, *Très grand*) dans les paramètres.
- **Conséquences** :
    - Intégration native parfaite avec le thème Dark/Light d'Android et personnalisation visuelle complète.

## ADR-006 : Recherche par occurrence insensible aux accents, Saut direct (`TextLayoutResult`) & Navigation `▲` / `▼`
- **Date** : 2026-11-09
- **Contexte** : La recherche d'un mot doit tolérer l'absence d'accents (ex. taper `"ete"` doit trouver `"été"`), lister chaque occurrence sous forme de lien avec son contexte et, au clic, emmener directement l'utilisateur à l'endroit exact du texte avec possibilité de passer à l'occurrence suivante/précédente.
- **Décision** :
    1. Normaliser les textes avec `java.text.Normalizer.Form.NFD` (suppression des diacritiques `\p{Mn}`) dans `DiacriticsMatcher` tout en conservant les `charOffset` exacts du texte original.
    2. Au clic sur une occurrence, naviguer vers la vue du journal avec `targetCharOffset`, `searchQuery` et l'index de l'occurrence.
    3. Surligner toutes les occurrences via `AnnotatedString`, utiliser `onTextLayout` (`TextLayoutResult.getBoundingBox(targetCharOffset)`) pour scroller automatiquement jusqu'à l'occurrence cible, et afficher une barre flottante discrète (`2/5 [▲] [▼] [✕]`) pour sauter d'une occurrence à l'autre dans la page.
- **Conséquences** :
    - Recherche ultra-tolérante en français/espagnol/portugais et navigation chirurgicale au cœur des longs journaux.

## ADR-007 : Sauvegarde `.md`, Archive `.zip` via SAF & Fusion Intelligente sans Perte
- **Date** : 2026-11-09
- **Contexte** : Pour changer de téléphone ou lire ses journaux sur ordinateur dans d'autres logiciels, l'utilisateur doit pouvoir tout exporter en `.md` et utiliser une archive `.zip` simple pour tout réimporter sur son nouveau téléphone sans jamais risquer d'écraser un journal déjà commencé sur le nouvel appareil.
- **Décision** :
    1. Utiliser le **Storage Access Framework (SAF)** d'Android (`CreateDocument` / `OpenDocument`) afin de ne requérir aucune permission système intrusive.
    2. Chaque journal correspond à un fichier standard `YYYY-MM-DD.md` commençant par `# {Titre Date Formatée}` suivi d'une ligne vide puis du texte.
    3. L'export complet génère un fichier `.zip` regroupant tous les fichiers `YYYY-MM-DD.md` via `java.util.zip.ZipOutputStream`.
    4. Lors de l'import `.zip` (ou `.md`), si une date `YYYY-MM-DD` existe déjà en base avec un texte différent, fusionner les deux textes avec un séparateur Markdown `\n\n---\n\n` au lieu d'écraser l'existant.
- **Conséquences** :
    - Portabilité universelle (Obsidian, Notion, VS Code) et garantie de zéro perte de données lors d'un changement de téléphone.

## ADR-008 : Maintien de l'écran actif pendant l'écriture (`Keep Screen On`)
- **Date** : 2026-11-09
- **Contexte** : Lorsqu'on écrit son journal et qu'on pose le téléphone sur la table quelques minutes pour réfléchir, l'écran ne doit pas s'éteindre ni se verrouiller sans arrêt.
- **Décision** :
    - Utiliser le modificateur natif Compose `Modifier.keepScreenOn()` sur `EditorScreen` piloté par une préférence utilisateur `keepScreenOn: Boolean` (activée à `true` par défaut, et commutable dans les paramètres).
- **Conséquences** :
    - L'écran reste allumé tant que l'application est ouverte sur le journal, tout en permettant à l'utilisateur de désactiver ce comportement dans les paramètres s'il veut économiser sa batterie.

## ADR-009 : Icône XML Minimaliste & Splash Screen Unifié (`androidx.core:core-splashscreen`)
- **Date** : 2026-11-09
- **Contexte** : L'application doit avoir une identité visuelle simple, sobre et légère : une icône simple dessinée en XML (`VectorDrawable`) et un Splash Screen simple reprenant exactement cette icône à l'ouverture.
- **Décision** :
    1. Concevoir l'icône adaptative (`ic_launcher_foreground.xml` et `ic_launcher_background.xml`) directement en VectorDrawable XML pur (un carnet minimaliste avec une ligne d'écriture/plume géométrique), sans aucun asset bitmap lourd.
    2. Configurer l'API officielle `androidx.core:core-splashscreen` dans `themes.xml` (`windowSplashScreenAnimatedIcon = @drawable/ic_launcher_foreground`) et appeler `installSplashScreen()` dans `MainActivity.onCreate()`.
- **Conséquences** :
    - Poids APK minimal, netteté vectorielle parfaite sur toutes les densités d'écran et transition fluide vers l'éditeur du jour.

## ADR-010 : Verrouillage Biométrique / Code de l'Appareil Optionnel (`androidx.biometric`)
- **Date** : 2026-11-09
- **Contexte** : Bien que l'application s'ouvre par défaut sans friction, certains utilisateurs souhaitent protéger l'accès à leur journal intime par leur empreinte digitale ou le code de déverrouillage de leur téléphone.
- **Décision** :
    - Intégrer `androidx.biometric:biometric` (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`) piloté par une option `biometricLockEnabled: Boolean` dans les paramètres (**désactivée par défaut** pour préserver l'ouverture immédiate, activable en un clic).
- **Conséquences** :
    - Confidentialité renforcée à la demande, 100 % locale sans compte ni mot de passe propriétaire à retenir.

## ADR-011 : Barre d'outils de mise en forme WYSIWYG (Titres H1/H2/H3, Gras, Italique, Souligné, Listes UL/OL) sans symboles Markdown visibles
- **Date** : 2026-11-09
- **Contexte** : L'utilisateur souhaite disposer d'outils de mise en forme simples pendant l'écriture (titres de niveau, gras, italique, souligné, listes à puces `UL` et listes numérotées `OL`), **sans jamais voir les symboles liés au Markdown (`#`, `**`, `*`, `__`) dans l'interface**, tout en voyant dans la barre d'outils les boutons actifs (`H1`, `Gras`, etc.) selon la position du curseur.
- **Décision** :
    1. Dans [`MarkdownTextFormatter`](../app/src/main/java/fr/antoinehory/monsimplejournal/domain/MarkdownTextFormatter.kt) (`formatForDisplay`), masquer intégralement les délimiteurs Markdown (`# `, `## `, `### `, `***`, `**`, `*`, `__`) et transformer `- ` en puce `• `, tout en générant un `OffsetMapping` bidirectionnel exact (`origToTrans` / `transToOrig`) pour le curseur et le saut aux occurrences.
    2. Calculer en temps réel `activeFormats: Set<MarkdownFormatAction>` (`detectActiveFormats`) en fonction de la ligne et de la position du curseur/sélection afin d'illuminer les boutons correspondants (`H1`, `H2`, `H3`, `B`, `I`, `U`, `UL`, `OL`) dans [`FormattingToolbar`](../app/src/main/java/fr/antoinehory/monsimplejournal/ui/components/FormattingToolbar.kt).
- **Conséquences** :
    - Rendu 100 % WYSIWYG épuré dans l'éditeur, la lecture seule, les archives et la recherche, retour visuel immédiat dans la barre d'outils, et stockage/export `.md` universel préservé.

## ADR-012 : Sélection de la famille de police système (`Sans-serif`, `Serif`, `Monospace`)
- **Date** : 2026-11-09
- **Contexte** : L'utilisateur souhaite pouvoir choisir facilement depuis les paramètres entre une police système Sans-serif, une police Serif (style livre) et une police Monospace (style machine à écrire).
- **Décision** :
    - Ajouter l'énumération `FontFamilyOption` (`SANS_SERIF`, `SERIF`, `MONOSPACE`) dans `domain/ContrastPalette.kt`, la persister dans `UserPreferencesRepository` (DataStore) et l'injecter dans `journalTypography(fontScaleOption, fontFamilyOption)` (`ui/theme/Type.kt`).
- **Conséquences** :
    - Personnalisation typographique instantanée sur l'ensemble de l'application (éditeur, archives, lecture seule) sans alourdir l'APK avec des fichiers de polices externes.

## ADR-013 : Suppression du `packageScope` R8 partiel en build Release
- **Date** : 2026-11-09
- **Contexte** : Le build Release (`versionCode = 1`) plantait immédiatement au démarrage sur Samsung Galaxy A56 avec `java.lang.IllegalAccessError: Class kotlin.sequences.d extended by class ot1 is inaccessible`. L'option `packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")` (optimisation R8 partielle générée par défaut par le template AGP 9) laissait `kotlin.sequences.SequencesKt__SequenceBuilderKt` (`kotlin.sequences.d`, visibilité package-private) dans son package d'origine car référencée depuis `fr.antoinehory.monsimplejournal.**` (hors scope), tout en déplaçant sa sous-classe `SequencesKt___SequencesJvmKt` (`ot1`) à la racine du package par défaut.
- **Décision** :
    - Supprimer la restriction `packageScope` dans [`app/build.gradle.kts`](../app/build.gradle.kts) (`optimization { enable = true }`) afin que R8 optimise l'ensemble de l'application de manière uniforme et cohérente.
- **Conséquences** :
    - Toute la hiérarchie `SequencesKt` et les classes de l'application sont optimisées uniformément sans violation de visibilité inter-packages au chargement sur ART.

## ADR-014 : Isolation du `BasicTextField` par `sessionDate`, bascule immédiate au retour après minuit et auto-réparation
- **Date** : 2026-11-10
- **Contexte** : Sur smartphone (Samsung Galaxy A56), lors de la réouverture de l'application après minuit (`00h30`), le titre passait bien au nouveau jour mais le texte de la veille restait affiché et était sauvegardé sous la nouvelle date (si bien qu'à `10h30`, le journal de la veille apparaissait toujours sous le titre du jour). Cela provenait d'un callback `onValueChange` déclenché par l'`InputConnection` du clavier Android (`BasicTextField`) au moment du `ON_RESUME` avec l'ancien buffer de texte juste après le basculement de `activeSessionDate`. De plus, la règle de continuité à minuit doit uniquement éviter de couper quelqu'un en pleine rédaction tant que l'écran reste actif, mais ouvrir immédiatement une nouvelle entrée dès que l'utilisateur quitte l'application et revient après minuit.
- **Décision** :
    1. Encapsuler `BasicTextField` dans `key(uiState.sessionDate)` dans [`EditorScreen.kt`](../app/src/main/java/fr/antoinehory/monsimplejournal/ui/editor/EditorScreen.kt) et attacher `sessionDate` à chaque `EditorUiEvent.OnTextChanged`.
    2. Dans [`EditorViewModel.kt`](../app/src/main/java/fr/antoinehory/monsimplejournal/ui/editor/EditorViewModel.kt), conserver `activeSessionDate` tant que l'écran reste actif (aucune coupure à minuit en pleine rédaction), mais basculer immédiatement sur `LocalDate.now(clock)` au `ON_RESUME` dès lors que l'application est passée par `ON_STOP` (`hasStoppedInBackground`) et que la date a changé.
    3. Dans [`JournalRepository.kt`](../app/src/main/java/fr/antoinehory/monsimplejournal/data/repository/JournalRepository.kt) (`loadActiveSessionEntry`), détecter et supprimer automatiquement un doublon accidentel où l'entrée du jour courant est strictement identique à celle de la veille (tout en conservant intacte l'archive de la veille).
- **Conséquences** :
    - Aucune coupure si l'on écrit à `23h59` ➔ `00h05` sans quitter l'écran, bascule immédiate sur un journal vierge dès que l'on quitte et rouvre l'application après minuit, et réparation automatique pour la base `1.0.1`.

## ADR-015 : Navigation inter-journaux (Archives & Recherche globale) et Historique de recherche persistant
- **Date** : 2026-11-10
- **Contexte** : L'utilisateur souhaite :
    1. Pouvoir naviguer directement d'un journal d'archive à l'autre depuis `ReaderScreen` avec la même barre flottante (`▲` / `▼`) que celle des occurrences de recherche.
    2. Disposer d'un historique de recherche cliquable dans le bas de l'écran de recherche vide lorsqu'il vient de cliquer sur le bouton de recherche.
    3. Pouvoir utiliser les flèches `▲` / `▼` d'un résultat de recherche pour naviguer non seulement entre les occurrences du journal ouvert, mais aussi basculer automatiquement vers les autres journaux contenant le mot recherché.
- **Décision** :
    1. **Barre de navigation unifiée (`OccurrenceNavigatorBar`)** : Accepter un libellé personnalisable (`label: String`, ex. `"2 / 5"` ou `"1 / 3 • Global 2 / 8"`) et rendre le bouton de fermeture `onClose` optionnel (`null` en mode navigation d'archives simples, visible en mode recherche pour quitter le surlignage et revenir à la navigation d'archives).
    2. **Navigation inter-archives & inter-résultats de recherche (`ReaderViewModel`, `EditorViewModel`, `JournalNavHost`)** :
        - En mode lecture d'archive sans recherche (`highlightQuery.isEmpty()`), `ReaderViewModel` observe la liste ordonnée des dates d'archives (`observePastEntries`) et permet de passer à l'archive précédente/suivante via `▲` / `▼`.
        - En mode recherche active (`highlightQuery.isNotEmpty()`), `ReaderViewModel` et `EditorViewModel` observent l'ensemble des occurrences globales (`searchOccurrences(highlightQuery)`). Lorsque `▲` ou `▼` dépasse la première ou la dernière occurrence du journal courant, le ViewModel cible automatiquement le journal suivant/précédent contenant une occurrence (et bascule entre `EditorRoute` et `ReaderRoute` si nécessaire).
    3. **Historique de recherche (`UserPreferencesRepository` & `SearchScreen`)** :
        - Persister les dernières requêtes de recherche (jusqu'à 10 entrées uniques) dans DataStore (`recentSearchQueries`). Une requête est enregistrée lorsque l'utilisateur clique sur un résultat ou valide la recherche au clavier.
        - Afficher ces recherches récentes sous forme de puces cliquables ancrées en bas de `SearchScreen` (juste au-dessus du clavier grâce à `imePadding()`) lorsque le champ de recherche est vide.
- **Conséquences** :
    - Navigation ultra-fluide à travers tous les journaux d'archives et tous les résultats de recherche sans jamais avoir à faire d'allers-retours manuels.


