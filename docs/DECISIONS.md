# DECISIONS.md — Journal des Décisions Techniques (ADR)

> Ce fichier consigne les choix d'architecture et les contraintes techniques clés du projet **Journal** pour éviter de remettre en question des décisions déjà arbitrées.

---

## ADR-001 : Ouverture directe sur l'éditeur du jour & Ancrage de la date de session à 23h55
- **Date** : 2026-11-09
- **Contexte** : L'utilisateur souhaite que l'application s'ouvre instantanément sur la page d'écriture du jour avec la date en titre automatique. De plus, s'il écrit à `23h55` et que minuit (`00h00`) passe pendant qu'il écrit, le journal ne doit surtout pas se fermer ni basculer brutalement sur une page blanche du lendemain. Enfin, s'il quitte et revient dans la même journée, il doit retrouver le même journal.
- **Décision** :
    1. `MainActivity` démarre directement sur la destination `EditorRoute` sans écran intermédiaire.
    2. `EditorViewModel` capture une `sessionDate: LocalDate = LocalDate.now(clock)` à l'ouverture de la session et charge (ou crée à la première frappe) l'entrée Room ayant `isoDate = sessionDate.toString()`.
    3. Tant que la session d'écriture est active à l'écran, `sessionDate` reste verrouillée : le passage de `23h55` à `00h00` n'altère pas l'entrée en cours d'édition.
    4. Lorsque l'utilisateur quitte et relance l'application la même journée, `LocalDate.now(clock)` renvoie la même date `YYYY-MM-DD`, ce qui recharge automatiquement le journal du jour en mode édition.
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
- **Contexte** : Empêcher la saturation de la fenêtre de contexte de l'IA dans Android Studio (AntiGravity) et maintenir une cohérence stricte tout au long du projet.
- **Décision** : Structurer la connaissance du projet en 5 fichiers Markdown légers et complémentaires (`AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`) et référencer explicitement les Skills Android Studio pertinents dans `AGENTS.md`.
- **Conséquences** :
    - L'IA lit d'abord `AGENTS.md` puis uniquement les fichiers ou Skills pertinents pour la tâche en cours.

## ADR-004 : Persistance locale avec AndroidX Room (Entrées) & DataStore (Préférences)
- **Date** : 2026-11-09
- **Contexte** : L'application est 100 % hors-ligne et doit stocker efficacement des centaines ou milliers d'entrées textuelles quotidiennes ainsi que les préférences visuelles de l'utilisateur.
- **Décision** :
    - Utiliser `androidx.room:room-runtime` et `room-ktx` (`2.8.5`) via KSP (`2.3.12`) pour les entrées de journal (`journal_entries`).
    - Utiliser `androidx.datastore:datastore-preferences` (`1.1.3`) pour stocker le format de date choisi, le preset de contraste actif, les couleurs personnalisées (fond/texte) et l'option `keepScreenOn`.
- **Conséquences** :
    - Observation réactive de bout en bout via Kotlin `Flow` et `StateFlow`.

## ADR-005 : Thèmes de contrastes inspirés de *Hungry Cat Nonogram* & Couleurs personnalisées
- **Date** : 2026-11-09
- **Contexte** : L'utilisateur souhaite un confort visuel optimal avec plusieurs fonds de contrastes (noir, gris, blanc, etc.) inspirés du jeu *Hungry Cat Nonogram*, sous forme de presets dans les paramètres mais aussi avec la liberté de choisir soi-même ses propres couleurs.
- **Décision** :
    1. Définir une énumération de presets (`ContrastPreset`) incluant : *Noir OLED*, *Gris Ardoise*, *Gris Papier*, *Blanc Pur*, *Sépia Monogramme*, ainsi qu'un mode *Personnalisé*.
    2. Intégrer un sélecteur de couleurs personnalisé (roue HSV + luminosité + saisie directe du code Hexadécimal `#RRGGBB`) pour la couleur d'arrière-plan et la couleur du texte.
    3. Appliquer dynamiquement cette palette (`ContrastPalette`) à tout le thème Material 3 de l'application.
- **Conséquences** :
    - Interface sobre, apaisante et 100 % adaptable à la sensibilité visuelle de l'utilisateur (jour comme nuit).

## ADR-006 : Recherche par occurrence détaillée et défilement automatique (`TextLayoutResult`)
- **Date** : 2026-11-09
- **Contexte** : La recherche d'un mot ne doit pas se contenter de lister les journaux contenant ce mot ; elle doit lister chaque occurrence sous forme de lien avec son contexte et, au clic, emmener directement l'utilisateur à l'endroit exact du texte dans le journal.
- **Décision** :
    1. Lors d'une recherche, analyser chaque journal correspondant pour extraire toutes les positions (`charOffset`) du terme cherché et construire une liste de `SearchOccurrence` contenant la date, un extrait de phrase autour de l'occurrence et l'offset exact du caractère.
    2. Au clic sur une occurrence, naviguer vers la vue du journal avec `targetCharOffset` et `searchQuery`.
    3. Surligner le mot recherché via `AnnotatedString` et utiliser `onTextLayout` (`TextLayoutResult.getBoundingBox(targetCharOffset)`) pour calculer l'ordonnée $Y$ en pixels et déclencher `scrollState.animateScrollTo(y)` automatiquement.
- **Conséquences** :
    - Navigation chirurgicale au cœur même de longs journaux, avec repérage visuel immédiat.

## ADR-007 : Sauvegarde et Migration en Markdown (`.md`) et Archive (`.zip`) via SAF
- **Date** : 2026-11-09
- **Contexte** : Pour changer de téléphone ou lire ses journaux sur ordinateur dans d'autres logiciels, l'utilisateur doit pouvoir tout exporter en `.md` et utiliser une archive `.zip` simple pour tout réimporter sur son nouveau téléphone.
- **Décision** :
    1. Utiliser le **Storage Access Framework (SAF)** d'Android (`CreateDocument` / `OpenDocument`) afin de ne requérir aucune permission système intrusive.
    2. Chaque journal correspond à un fichier standard `YYYY-MM-DD.md` commençant par `# {Titre Date Formatée}` suivi d'une ligne vide puis du texte.
    3. L'export complet génère un fichier `.zip` regroupant tous les fichiers `YYYY-MM-DD.md` via `java.util.zip.ZipOutputStream`.
    4. L'import `.zip` lit chaque entrée `YYYY-MM-DD.md` via `ZipInputStream`, reconstitue l'entité `JournalEntryEntity` et l'insère dans Room (avec stratégie de fusion/remplacement propre).
- **Conséquences** :
    - Portabilité universelle (100 % compatible Obsidian, Notion, VS Code, Typora) et transfert d'un téléphone à un autre en un seul fichier `.zip`.

## ADR-008 : Maintien de l'écran actif pendant l'écriture (`Keep Screen On`)
- **Date** : 2026-11-09
- **Contexte** : Lorsqu'on écrit son journal et qu'on pose le téléphone sur la table quelques minutes pour réfléchir, l'écran ne doit pas s'éteindre ni se verrouiller sans arrêt.
- **Décision** :
    - Utiliser le modificateur natif Compose `Modifier.keepScreenOn()` sur `EditorScreen` piloté par une préférence utilisateur `keepScreenOn: Boolean` (activée à `true` par défaut, et commutable dans les paramètres).
- **Conséquences** :
    - L'écran reste allumé tant que l'application est ouverte sur le journal, tout en permettant à l'utilisateur de désactiver ce comportement dans les paramètres s'il veut économiser sa batterie.