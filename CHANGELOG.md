# 📋 Changelog — Mon Simple Journal

Toutes les modifications notables apportées à ce projet sont documentées dans ce fichier.
Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

---

## [1.0.2] - 2026-11-10

### ✨ Nouveautés & Améliorations
- **Navigation entre les journaux en archive (`▲` / `▼`)** : possibilité de passer directement d'un ancien journal au précédent ou au suivant depuis l'écran de lecture seule avec la même barre flottante que les occurrences.
- **Navigation inter-journaux dans les résultats de recherche** : les flèches `▲` / `▼` permettent désormais non seulement de parcourir les occurrences du journal ouvert, mais aussi de basculer automatiquement vers les autres journaux contenant le mot recherché.
- **Historique de recherche cliquable** : affichage en bas de l'écran de recherche vide des dernières recherches effectuées sous forme de puces cliquables (avec suppression individuelle ou totale).

### 🐛 Correction
- **Changement de jour (« Un journal par jour »)** : correction d'un bug où la réouverture de l'application après minuit (ex. à 00h30 puis à 10h30) mettait à jour le titre avec la nouvelle date mais conservait et dupliquait le texte de la veille dans le journal du nouveau jour à cause d'un renvoi d'état du clavier Android (`BasicTextField` / IME) lors du `ON_RESUME`.
- Isolation stricte du composant `BasicTextField` par date de session (`key(sessionDate)`), bascule immédiate sur une nouvelle entrée dès que l'on quitte et rouvre l'application après minuit (tout en ne coupant jamais une rédaction tant que l'écran reste actif), et auto-nettoyage au démarrage si le journal du jour est un doublon exact de celui de la veille.

---

## [1.0.1] - 2026-11-09

### 🐛 Correction
- Correction d'un crash au démarrage en build **Release** (`java.lang.IllegalAccessError` sur `kotlin.sequences.SequencesKt`) causé par le `packageScope` R8 partiel dans `app/build.gradle.kts`.

---

## [1.0.0] - 2026-11-09

### 🎉 Version initiale (Première publication Google Play)

#### ✍️ Éditeur Quotidien Sans Distraction
- Ouverture directe de l'application sur l'écran d'écriture du jour (avec icône XML minimaliste et Splash Screen unifié), sans menu intermédiaire.
- Génération automatique du titre avec le nom du jour et la date complète en toutes lettres (format personnalisable dans les paramètres).
- Sauvegarde automatique instantanée tout au long de la journée : quitter et revenir dans la même journée rouvre directement le journal du jour.
- Bouton discret d'horodatage (`— HH:mm —`) pour noter l'heure lorsqu'on reprend l'écriture plusieurs fois dans la journée.
- Barre d'outils de mise en forme légère (Titres `H1`/`H2`/`H3`, **Gras**, *Italique*, <ins>Souligné</ins>, Listes à puces `UL` et Listes numérotées `OL` avec continuation automatique) et rendu visuel en direct.
- Continuité intelligente au passage de minuit : si l'écriture commence à 23h55 et dépasse 00h00, la session se poursuit sans coupure dans le même journal.
- Maintien de l'écran actif (`Keep Screen On`) pendant l'écriture pour éviter l'extinction de l'écran lors des temps de réflexion.

#### 📚 Archives, Lecture Seule & Confidentialité
- Historique complet des journaux passés classés par ordre chronologique.
- Consultation des journaux des jours précédents en **mode lecture seule strict** afin de garantir l'intégrité des écrits passés.
- Verrouillage biométrique / code de l'appareil optionnel dans les paramètres.

#### 🔍 Recherche d'Occurrences sans Accents & Saut Direct (`▲` / `▼`)
- Moteur de recherche plein texte hors-ligne insensible à la casse et aux accents (chercher `"ete"` trouve `"été"`).
- Affichage détaillé de chaque occurrence trouvée sous forme de liste de liens avec extrait contextuel.
- Navigation directe à l'endroit exact du mot dans le journal avec défilement automatique (`auto-scroll`), surlignage et barre flottante (`▲` / `▼`) pour passer d'une occurrence à l'autre.

#### 🎨 Thème Dark/Light Automatique, Contrastes & Typographie
- Adaptation automatique au thème clair ou sombre (Dark / Light) du smartphone par défaut.
- Presets de fonds de contraste inspirés de *Hungry Cat Nonogram* (Noir, Gris ardoise, Gris clair, Blanc, Sépia).
- Sélecteur de couleurs sur-mesure (roue chromatique HSV et code Hexadécimal `#RRGGBB`) permettant de choisir librement la couleur du fond et celle du texte.
- Choix de la famille de police système (**Sans-serif**, **Serif**, **Monospace**) et taille de police ajustable (*Petit, Normal, Grand, Très grand*).

#### 📦 Sauvegarde Universelle `.md` & `.zip` avec Fusion sans Perte
- Export des journaux au format universel **Markdown (`.md`)**, compatibles avec tous les logiciels de prise de notes sur mobile et ordinateur.
- Export et import complet sous forme d'archive **`.zip`** en un clic avec fusion intelligente sans perte pour changer de téléphone facilement.
- Fonctionnement **100 % hors-ligne**, sans publicité, sans compte et sans aucune permission intrusive.
- Traduction complète en 4 langues : Français (`fr`), Anglais (`en`), Espagnol (`es`) et Portugais (`pt`).
