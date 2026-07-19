# ADR-016: Deployment- und Auslieferungsmodell

- **Status:** proposed
- **Datum:** 2026-07-19
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate wird von Betreibern bzw. Dienstleistern in Rechenzentrumsumgebungen betrieben. Diese
Betreiber sollen später keine IDE installieren, keinen Quellcode auschecken und keine Maven- oder
npm-Kommandos ausführen müssen. Die aktuelle `docker-compose.yml` ist ausdrücklich eine lokale
Entwicklungsumgebung: Sie baut Images aus dem Source-Tree, startet das Frontend über den
Vite-Dev-Server und verwendet Keycloak `start-dev`.

Für den produktiven Betrieb braucht EduGate ein klares Auslieferungs- und Deploymentmodell:

- reproduzierbare Release-Artefakte,
- keine Build-Pflicht beim Betreiber,
- konfigurierbare Secrets und Umgebungswerte,
- klare Trennung von Entwicklungs-, Test- und Produktionssetup,
- Update- und Migrationspfade,
- Anschluss an Netzzonen aus ADR-007,
- Betrieb ohne externe Cloud-Abhängigkeit.

## Betrachtete Optionen

1. **Source-Deployment beim Betreiber:** Betreiber klont das Repository und baut Backend/Frontend
   selbst. Das ist für Entwicklung praktisch, aber für Betrieb unprofessionell und fehleranfällig.
2. **Manuelle ZIP-/JAR-Auslieferung:** Quarkus-Runner, statisches Frontend und Skripte werden
   gepackt. Das reduziert Build-Aufwand, lässt aber Prozessmanagement, Updates, Netztrennung,
   Secrets und Abhängigkeiten zu stark beim Betreiber.
3. **Versionierte Container-Images mit Docker Compose als Referenzdeployment:** EduGate liefert
   OCI-Images und eine produktionsnahe Compose-Vorlage. Betreiber konfigurieren Umgebung und
   Secrets, ziehen Images aus einer Registry und starten den Stack ohne IDE.
4. **Kubernetes/Helm als einziges Deploymentmodell:** für große Rechenzentren attraktiv, aber für
   kleinere Betreiber zu schwergewichtig als einzige Einstiegshürde.

## Entscheidung

Vorläufig gewählt wird **Option 3** mit einer späteren Erweiterungsoption zu Kubernetes/Helm.

SVWS-EduGate wird als versionierter Container-Stack ausgeliefert. Docker Compose ist das
Referenzdeployment für kleinere und mittlere Betreiber sowie für Abnahmesysteme. Für größere
Rechenzentren kann später ein Helm Chart oder ein anderes Kubernetes-Deployment ergänzt werden,
ohne das Compose-Referenzmodell aufzugeben.

### Release-Artefakte

Ein Release besteht perspektivisch mindestens aus:

- OCI-Image für die Control Plane,
- OCI-Image für das API-Gateway,
- OCI-Image oder statisches Artefakt für die Admin-UI,
- Datenbankmigrationen als Bestandteil der Backend-Artefakte,
- produktionsnahe Compose-Vorlage,
- `.env.example` bzw. Konfigurationsreferenz ohne echte Secrets,
- Betreiber-Dokumentation für Installation, Update, Backup und Troubleshooting.

Die Admin-UI darf im Produktivbetrieb nicht über den Vite-Dev-Server laufen. Sie wird entweder als
statisches Frontend in einem Webserver/Reverse Proxy ausgeliefert oder kontrolliert durch die
Control Plane bereitgestellt. Diese Detailentscheidung kann später präzisiert werden.

### Konfiguration und Secrets

Betreiber konfigurieren EduGate über Umgebungsvariablen, Secret-Dateien oder die Secret-Mechanik
der jeweiligen Plattform. Produktive Secrets dürfen nicht in Compose-Dateien oder Git-Repository
stehen.

Mindestens zu konfigurieren sind:

- PostgreSQL-Verbindung und Passwörter für Anwendungsrollen,
- Keycloak/OIDC-Endpunkte und Client-Konfiguration,
- Master-Key bzw. Schlüsselmaterial für verschlüsselte SVWS-Zugangsdaten,
- TLS-/Reverse-Proxy-Parameter,
- Netzwerk-/Zonenparameter,
- optional Truststore-/Zertifikatsparameter für SVWS-Instanzen.

Die lokale Dev-Truststore-Mechanik bleibt Entwicklungswerkzeug und ist nicht der Standard für
Produktion.

### Datenbank und Migrationen

Produktiv soll PostgreSQL als persistente Betriebsdatenbank betrieben werden. Ob PostgreSQL im
Compose-Referenzstack mitgeliefert oder als bestehender RZ-Dienst angebunden wird, bleibt eine
Betriebsvariante. In beiden Fällen müssen Migrationen reproduzierbar und kontrolliert laufen.

Leitregeln:

- Migrationen laufen nicht manuell aus einer IDE.
- Vor produktiven Updates müssen Backup und Migrationshinweise dokumentiert sein.
- Rollback-Fähigkeit wird pro Release bewertet; Datenbank-Downgrades sind nicht automatisch
  garantiert.
- Keycloak-Konfiguration und Realm-Änderungen gehören ebenfalls zum Deployment-/Updatekonzept.

### Netzzonen und Exponierung

Das Deployment muss ADR-007 abbilden:

- Control Plane, Admin-UI, Keycloak und PostgreSQL bleiben in der Verwaltungszone.
- Das Gateway liegt in der Gateway-Zone.
- Eine spätere externe Zone enthält Reverse Proxy/WAF und leitet nur zum Gateway.
- SVWS-Instanzen liegen in der SVWS-Zone.
- EduGate spricht nicht direkt mit MariaDB der SVWS-Instanzen, sondern über den SVWS-Server.

Die Compose-Datei darf diese Zonen als logische Netze nachbilden; produktive Deployments müssen
sie durch echte Netzwerk- und Firewall-Regeln absichern.

### Update-Modell

Ein Betreiber-Update soll perspektivisch so aussehen:

1. Release Notes lesen.
2. Backup von PostgreSQL/Keycloak und relevanter Konfiguration erstellen.
3. Image-Tags in der Deployment-Konfiguration aktualisieren.
4. Migrationen kontrolliert ausführen lassen.
5. Healthchecks prüfen.
6. Gateway- und Control-Plane-Funktionstests durchführen.

Später können signierte Images, SBOMs und feste Release-Kanäle ergänzt werden. Für öffentliche
Verwaltung und BSI-nahe Umgebungen sollte diese Lieferkette früh mitgedacht werden.

## Konsequenzen

### Positiv

- Betreiber brauchen keine IDE und keinen lokalen Source-Build.
- Dev-Setup und Produktionsbetrieb werden sauber getrennt.
- Docker Compose bietet einen verständlichen Referenzpfad; Kubernetes bleibt als Ausbaustufe
  möglich.
- Netzzonen, Secrets und Updates können dokumentiert und getestet werden.

### Negativ / Risiken

- Es braucht CI/CD, Registry, Image-Versionierung und Release-Prozess.
- Compose ist ein Referenzmodell, ersetzt aber in großen RZ-Umgebungen keine echte
  Betriebsplattform.
- Keycloak-, PostgreSQL- und Secret-Backups müssen im Betriebskonzept präzisiert werden.
- Ohne konsequente Dokumentation könnten Betreiber trotzdem Dev-Compose in Produktion kopieren;
  das muss ausdrücklich verhindert werden.

## Verweise

- ADR-001 (Trennung von Control Plane und Data Plane)
- ADR-003 (Quarkus Backend)
- ADR-004 (API-Gateway)
- ADR-005 (Keycloak)
- ADR-006 (Secret-Handling)
- ADR-007 (Netzzonenkonzept)
- `docker-compose.yml`
- `.env.example`
