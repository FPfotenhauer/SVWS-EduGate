# ADR-010: Einheitliches Design-System für SVWS-Apps (Emerald)

- **Status:** accepted
- **Datum:** 2026-07-17
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate ist nicht die einzige SPA im SVWS-Werkzeugkasten: SVWS-Conference und
SVWS-Import sind eigenständige Produkte, sollen aber als zusammengehörige Produktfamilie
wahrgenommen werden. Für dieses Produktpaar wurde bereits ein gemeinsames visuelles System
etabliert (Emerald-Farbpalette, explizite CSS Custom Properties, ein per Klasse und
`localStorage` geschalteter Dark Mode) und in einem eigenen ADR im SVWS-Conference-Repository
dokumentiert. Für das EduGate-Frontend (`frontend/`, Vue 3 + TypeScript, siehe ADR-003) stellt
sich dieselbe Frage: eigenes Erscheinungsbild oder Einreihung in die bestehende Familie?

Anders als SVWS-Conference/SVWS-Import verwendet EduGate laut `FIRSTPROMPT.md` bewusst **kein**
UI-Framework wie PrimeVue – die Views sind einfaches Vue 3 + handgeschriebenes CSS. Die
Übernahme des Referenzsystems kann sich hier also nicht auf PrimeVue-Token-Überschreibungen
stützen, sondern nur auf die framework-unabhängigen Teile (Custom Properties, Dark-Mode-Klasse,
gemeinsamer `localStorage`-Schlüssel).

## Betrachtete Optionen

1. **Eigenständiges Erscheinungsbild für EduGate:** freie Farbwahl, keine Abstimmung mit den
   anderen Apps nötig, aber EduGate wirkt als Fremdkörper neben Conference/Import, obwohl alle
   drei vom selben Dienstleister betrieben und potenziell im selben Browser/Tab-Wechsel genutzt
   werden.
2. **Vollständige Übernahme des Emerald-Systems** (Farbpalette, CSS-Custom-Property-Namen,
   Dark-Mode-Mechanik inkl. geteiltem `localStorage`-Key `dark-mode`), reduziert auf die
   framework-unabhängigen, für EduGate tatsächlich relevanten Teile.
3. **Gemeinsames NPM-Paket/Design-System-Bibliothek** für alle SVWS-Apps: maximale
   Wiederverwendung, aber eigene Paketverwaltung/Registry und Versionierungsaufwand für aktuell
   drei kleine SPAs unverhältnismäßig (vgl. Team-/Wartbarkeitsziel aus ARCHITECTURE.md Kap. 1).

## Entscheidung

Gewählt wurde **Option 2**. Konkret für EduGate:

- **Farbpalette und Basis-Tokens 1:1 aus der „Landing page"-Ebene von SVWS-Conference
  übernommen** (`src/style.css`, `:root`/`:root.dark`), ohne App-Prefix, damit sie bei Bedarf
  direkt mit den anderen Apps abgeglichen werden können: `--accent` (Light `#059669`
  Emerald-600, Dark `#34d399` Emerald-400), `--accent-hover`, `--bg-a`, `--bg-b`, `--surface`,
  `--surface-strong`, `--ink`, `--ink-soft`, `--line`, `--shadow`, `--error`, `--overlay`,
  `--focus-ring`. Diese Ebene ist bereits vollständig app-neutral (in Conference die
  Werte des Landing-/Start-Screens, unabhängig von der Konferenz-Fachlogik) und deckt alle
  UI-Bausteine ab, die EduGate braucht (Formulare, Karten/Tabellen, Buttons, Modals).
- **Keine Übernahme der zweiten, konferenzspezifischen Token-Ebene** (`--conf-*`: eigene
  Chrome-Farben für Topbar/Tabelle/Timer der Konferenz-Fachansicht) – EduGate hat keine
  vergleichbare zweite UI-Ebene, die Landing-Tokens reichen für die gesamte Anwendung.
- **Keine Übernahme domänenspezifischer Tokens** (Notenstufen `--n1`–`--n6`, Fach-Randfarben
  `--nf-*`, LK-Badge `--lk-*`): EduGate verwaltet Schulträger/Instanzen/Schemas, keine Noten-
  oder Kursdaten – diese Tokens haben hier keine fachliche Entsprechung.
- **Kein Übernahme der PrimeVue-Abschnitte** des Referenz-ADR (keine `--p-primary-*`-Overrides,
  keine PrimeVue-Button-Anpassungen) – EduGate hat keine PrimeVue-Abhängigkeit (anders als
  SVWS-Conference und SVWS-Import, die beide PrimeVue/Aura einsetzen). Sollte EduGate später
  PrimeVue einführen, gilt derselbe Override-Mechanismus wie im Referenzsystem beschrieben.
- **Dark Mode über die Klasse `dark` auf `<html>`**, gesteuert durch ein Composable
  `useTheme.ts` (Preference `'light' | 'dark' | 'system'`, `initTheme()` beim Start,
  `setTheme()` für einen Umschalter), 1:1 aus SVWS-Conference übernommen, persistiert unter
  demselben `localStorage`-Schlüssel `dark-mode` wie in SVWS-Conference/SVWS-Import. Das ist
  eine bewusste Kopplung: Nutzt dieselbe Person mehrere SVWS-Apps im selben Browser, überträgt
  sich die Theme-Wahl automatisch (Same-Origin-Grenzen gelten wie üblich – kein Cross-App-Sync
  über Origin-Grenzen hinweg, das ist technisch nicht anders lösbar und wird hier nicht
  versucht).
- **Body-Hintergrund-Gradient** identisch zum Referenzsystem (radiale Emerald-/Teal-Akzente vor
  linearem `--bg-a`/`--bg-b`-Verlauf).
- **WCAG-AA-Kontrast** bleibt Prüfkriterium für alle Token-Kombinationen, wie im
  Referenzsystem.

## Konsequenzen

### Positiv

- Einheitliches Erscheinungsbild über alle SVWS-Apps hinweg, ohne dass EduGate ein eigenes
  Farbsystem entwerfen und pflegen muss.
- Geteilte Theme-Präferenz zwischen SVWS-Apps im selben Browser (gleicher `localStorage`-Key).
- Kein Mehraufwand durch nicht benötigte Teile (PrimeVue-Overrides, Domänen-Tokens) – nur der
  tatsächlich anwendbare Ausschnitt wird übernommen.

### Negativ / Risiken

- Die Token-Liste muss weiterhin manuell zwischen den Repositories synchron gehalten werden
  (kein gemeinsames Paket, siehe verworfene Option 3).
- EduGate übernimmt eine extern (in einem anderen Repo) getroffene Design-Entscheidung; künftige
  Änderungen am Referenzsystem erfordern einen bewussten Abgleich, sonst laufen die Apps visuell
  auseinander.
- Führt EduGate später PrimeVue oder ein anderes UI-Framework ein, muss der Override-Mechanismus
  aus dem Referenzsystem neu bewertet werden (in diesem ADR nur vorgemerkt, nicht spezifiziert).

## Verweise

- Referenz-ADR „ADR-007: Einheitliches Design-System für SVWS-Apps" im SVWS-Conference-
  Repository (Quelle der Farbpalette und der Dark-Mode-Mechanik, hier als Kontext beigezogen,
  nicht Bestandteil dieses Repositories)
- SVWS-Conference-Repository: `src/style.css` (Token-Definitionen, Landing-Ebene),
  `src/composables/useTheme.ts` (Dark-Mode-Composable, 1:1 übernommen)
- SVWS-Import-Repository: `src/composables/useDarkMode.ts` (alternative, einfachere
  Boolean-Variante desselben `localStorage`-Schlüssels `dark-mode` – EduGate folgt der
  funktionsreicheren `useTheme.ts`-Variante aus SVWS-Conference, da beide denselben Schlüssel
  respektieren und damit kompatibel bleiben)
- ADR-003 (Quarkus-Backend/Vue-Frontend-Technologiewahl)
