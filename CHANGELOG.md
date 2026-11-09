# 📋 Changelog — Mon Simple Journal

Toutes les modifications notables apportées à ce projet sont documentées dans ce fichier.
Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

---

## [1.0.0] - 2026-11-09

### 🎉 Version initiale (Première publication Google Play)

#### ✍️ Éditeur Quotidien Sans Distraction
- Ouverture directe de l'application sur l'écran d'écriture du jour (avec icône XML minimaliste et Splash Screen unifié), sans menu intermédiaire.
- Génération automatique du titre avec le nom du jour et la date complète en toutes lettres (format personnalisable dans les paramètres).
- Sauvegarde automatique instantanée tout au long de la journée : quitter et revenir dans la même journée rouvre directement le journal du jour.
- Bouton discret d'horodatage (`— HH:mm —`) pour noter l'heure lorsqu'on reprend l'écriture plusieurs fois dans la journée.
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
- Taille de police ajustable (*Petit, Normal, Grand, Très grand*).

#### 📦 Sauvegarde Universelle `.md` & `.zip` avec Fusion sans Perte
- Export des journaux au format universel **Markdown (`.md`)**, compatibles avec tous les logiciels de prise de notes sur mobile et ordinateur.
- Export et import complet sous forme d'archive **`.zip`** en un clic avec fusion intelligente sans perte pour changer de téléphone facilement.
- Fonctionnement **100 % hors-ligne**, sans publicité, sans compte et sans aucune permission intrusive.
- Traduction complète en 4 langues : Français (`fr`), Anglais (`en`), Espagnol (`es`) et Portugais (`pt`).
