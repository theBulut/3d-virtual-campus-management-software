# Bedienungsanleitung

Verwaltungsoberfläche des 3D Campus Explorers der TU Darmstadt.

Diese Anleitung richtet sich an alle, die mit der Anwendung arbeiten — von der Projektmitarbeit, die Inhalte erfasst, bis zum technischen Betrieb, der das Protokoll liest. Sie ist nach Rollen gegliedert: Suchen Sie das Kapitel zu Ihrer Rolle, dort stehen genau die Aufgaben, die Sie tatsächlich haben.

Die Hinweiskästen mit **Achtung** sind nicht geraten. Sie stehen an den Stellen, an denen Teilnehmende einer Nutzerstudie mit vierzehn Personen tatsächlich hängengeblieben sind.

---

## 1 · Anmelden

Die Anwendung läuft im Browser. Die Adresse nennt Ihnen die Person, die Ihr Konto eingerichtet hat; in der Entwicklungsumgebung ist es <http://localhost:3000>.

Melden Sie sich mit **Benutzername oder E-Mail-Adresse** und Ihrem Passwort an.

### Die erste Anmeldung

Ein frisch angelegtes Konto startet mit einem Initialpasswort, das Ihnen die Projektleitung oder die Administration einmalig mitteilt. Nach der ersten Anmeldung sehen Sie ein gelbes Banner und **nur den Menüpunkt Dashboard** — selbst dann, wenn Ihnen bereits eine Rolle zugewiesen wurde.

Das ist kein Fehler. Solange das Initialpasswort gilt, trägt Ihre Sitzung nur eine einzige Berechtigung: die, Ihr eigenes Profil zu ändern. Gehen Sie auf **Mein Profil → Passwort ändern**. Das neue Passwort braucht mindestens zwölf Zeichen. Nach der Änderung werden Sie abgemeldet; nach der erneuten Anmeldung steht Ihnen Ihre Rolle vollständig zur Verfügung.

### Abmelden

Oben rechts über **Abmelden**. Ihre Sitzung wird damit serverseitig ungültig — ein Schließen des Browserfensters allein genügt nicht.

---

## 2 · Was Sie sehen, und warum

Die Seitenleiste links zeigt **nur die Bereiche, für die Sie Berechtigungen haben**. Oben rechts steht, wie viele das sind. Zwei Personen mit verschiedenen Rollen sehen dieselbe Adresse unterschiedlich.

Wichtig für das Verständnis: Die Oberfläche **verhindert nichts**. Sie bietet nur nicht an, was der Server ohnehin ablehnen würde. Rufen Sie eine Seite direkt über die Adresszeile auf, für die Ihnen die Berechtigung fehlt, landen Sie auf einer Fehlerseite, die Ihnen die fehlende Berechtigung beim Namen nennt — etwa *Benötigt wird: USER_READ*.

### Die sechs Rollen

| Rolle | Wer das ist | Was die Rolle tut |
|---|---|---|
| **Administration** | Systemverantwortliche | Alles: Konten, Rollen, Inhalte, Gebäude, Protokoll |
| **Projektleitung** | Leitung des Campus-Projekts | Inhalte prüfen und freigeben, Konten anlegen, Rollen eingeschränkt vergeben |
| **Projektmitarbeit** | Redaktion | Campus-Punkte erfassen und zur Prüfung einreichen |
| **Personal** | Fachgebiete und Verwaltung | Eigene Beratungsangebote und Sprechzeiten pflegen |
| **Technischer Betrieb** | Systembetreuung | Protokoll und Rechtemodell einsehen, keine Inhalte |
| **Externe Person** | Studierende, Gäste | Veröffentlichte Inhalte ansehen, 3D-Campus betreten |

Ein Konto kann mehrere Rollen tragen; die Berechtigungen addieren sich.

---

## 3 · Projektmitarbeit — Campus-Punkte erfassen

Ein **Campus-Punkt** (POI) ist ein Ort auf dem Campus mit Namen, Beschreibung und Position: ein Hörsaal, ein Rechnerpool, eine Beratungsstelle.

### Status: der Weg eines Inhalts

Jeder Campus-Punkt durchläuft vier Zustände:

```
Entwurf  →  In Prüfung  →  Veröffentlicht  →  Archiviert
   ↑            │
   └────────────┘   zurückgewiesen, mit Begründung
```

Sie erfassen und bearbeiten Inhalte im Status **Entwurf**. Veröffentlichen darf die Projektleitung.

### Einen neuen Punkt anlegen

1. **POIs → POI anlegen**
2. Angaben ausfüllen. Pflichtfelder sind mit `*` gekennzeichnet.
3. **Speichern**
4. **Zur Prüfung einreichen**

> **Achtung — der häufigste Fehler überhaupt.**
> Schritt 4 wird leicht übersehen. In der Studie legten vier von vierzehn Personen einen Punkt korrekt an und **reichten ihn nicht ein** — der Inhalt blieb unbemerkt als Entwurf liegen und erreichte die Prüfung nie.
>
> Der Grund: Der Knopf *Zur Prüfung einreichen* erscheint **erst nach dem Speichern**. Solange der Punkt noch nicht gespeichert ist, gibt es ihn nicht, weil es den Datensatz noch nicht gibt.
>
> Merken Sie sich: **Speichern ist nicht Einreichen.** Prüfen Sie nach dem Speichern den Statushinweis. Steht dort *Entwurf*, ist der Punkt noch bei Ihnen.

### Eine Rückmeldung bearbeiten

Weist die Projektleitung einen Punkt zurück, steht er wieder auf **Entwurf**, und oben im Editor erscheint die Begründung unter *Zurückgewiesen:*.

1. Punkt über **POIs** öffnen und die Begründung lesen
2. Die verlangte Änderung vornehmen
3. **Speichern**
4. **Zur Prüfung einreichen**

> **Achtung — Speichern und Einreichen sind zwei Schritte.**
> Fünf Personen der Studie stolperten hier. Zwei reichten eine Korrektur ein, **ohne vorher zu speichern** — die Änderung war damit nicht Teil des eingereichten Inhalts. Eine weitere korrigierte und reichte nicht ein.
>
> Das Einreichen übernimmt Ihre Änderungen **nicht** automatisch. Erst speichern, dann einreichen.

### Was Sie nicht können, und warum

Sie können **fremde Entwürfe nicht bearbeiten**. Öffnen Sie den Entwurf einer anderen Person, ist *Speichern* deaktiviert und die Oberfläche erklärt den Grund. Das liegt am Eigentum des Datensatzes, nicht an seinem Status.

Sie können **nicht freigeben**. Erstellen und Freigeben sind getrennte Berechtigungen, damit Inhalte vor der Veröffentlichung von jemand anderem gesehen werden.

---

## 4 · Projektleitung — prüfen, freigeben, Konten führen

### Inhalte prüfen

Eingereichte Inhalte sammeln sich in der **Freigabe-Warteschlange**. Öffnen Sie einen Eintrag, vergleichen Sie die Angaben mit Ihrer Fachinformation und entscheiden Sie:

- **Freigeben** — der Inhalt wird veröffentlicht und ist über die öffentliche Schnittstelle und im 3D-Campus sichtbar.
- **Zurückweisen** — der Inhalt geht mit einer Begründung zurück an die Bearbeitung. Die Begründung ist **Pflicht**.

Beide Knöpfe erscheinen nur, solange der Inhalt den Status **In Prüfung** hat. Bei einem Entwurf oder einem bereits veröffentlichten Punkt sind sie nicht da.

> **Achtung — die Prüfaktionen werden gesucht.**
> Dies war in der Studie der am breitesten belegte Problembereich. Mehrere Personen fanden *Zurückweisen* nicht auf Anhieb; eine korrigierte den Inhalt kurzerhand selbst, statt ihn zurückzugeben.
>
> Zwei Dinge helfen:
> **Erstens** — die Knöpfe stehen im Editor des Inhalts, nicht in der Liste. Öffnen Sie den Eintrag.
> **Zweitens** — korrigieren Sie als prüfende Person nicht selbst. Der Sinn der Prüfung ist, dass die erfassende Person von dem Fehler erfährt. Eine stillschweigende Korrektur nimmt ihr diese Rückmeldung.

> **Hinweis zur Vier-Augen-Prüfung.**
> Technisch können Sie einen Inhalt freigeben, den Sie selbst erstellt haben — Sie besitzen beide Berechtigungen. Das System trennt nach **Rollen**, nicht nach **Personen**. Ob Sie eigene Inhalte selbst freigeben, ist damit eine organisatorische Vereinbarung, keine technische Sperre.

### Ein Konto anlegen

1. **Nutzerverwaltung → Konto anlegen**
2. Benutzername, E-Mail, Vor- und Nachname, Einrichtung
3. Rolle wählen
4. **Konto anlegen**

Das **Initialpasswort wird genau einmal angezeigt**. Notieren Sie es sofort und geben Sie es der Person weiter. Danach ist es nicht wieder abrufbar; Sie müssten es zurücksetzen.

Die Rollenauswahl enthält nur die Rollen, die Sie vergeben dürfen — als Projektleitung sind das *Projektmitarbeit* und *Personal*. Die Administration können Sie nicht vergeben, auch sich selbst nicht.

### Rollen ändern

Auf der Detailseite eines Kontos unter **Rollen**: über *Rolle wählen …* hinzufügen, über das × am Chip entfernen.

> **Achtung — es gibt keinen Speichern-Knopf.**
> Rollenänderungen wirken **sofort**. Drei Personen der Studie suchten vergeblich nach einer Bestätigung. Die Rückmeldung kommt als kurze Meldung am Bildschirmrand; die aktuelle Zuordnung sehen Sie an den Chips.
>
> Beim **Tausch** einer Rolle: Fügen Sie zuerst die neue hinzu, entfernen Sie dann die alte. Ein Konto muss immer mindestens eine Rolle behalten — das Entfernen der letzten wird abgewiesen. Zwei Personen der Studie vergaßen, die alte Rolle zu entfernen; das Konto trug danach beide.

### Ihr eigenes Konto

Sie können sich **nicht selbst sperren** und sich **keine Rollen entziehen**. Die entsprechenden Bedienelemente fehlen auf Ihrer eigenen Detailseite. Das verhindert, dass sich jemand versehentlich aussperrt.

---

## 5 · Personal — Beratungsangebote und Sprechzeiten

Ein **Beratungsangebot** ist eine Sprechstunde Ihrer Einrichtung: Titel, Beschreibung, Gebäude, Raum, Kontakt — und dazu die **Sprechzeiten**.

Sie pflegen die Angebote, für die Sie als zuständig eingetragen sind. Fremde Angebote können Sie ansehen, aber nicht ändern.

### Ein Angebot anlegen

**Beratungsangebote → Angebot anlegen**, Felder ausfüllen, **Speichern**.

Sprechzeiten können Sie erst danach eintragen — sie hängen an der Kennung des Angebots, die beim Speichern entsteht. Der Bereich *Sprechzeiten* sagt das, solange das Angebot neu ist.

### Sprechzeiten pflegen

Der Bereich **Sprechzeiten** steht unterhalb der Stammdaten.

**Hinzufügen:** Knopf *Erste Sprechzeit anlegen* beziehungsweise *Sprechzeit hinzufügen* drücken. Es klappt eine leere Zeile auf. Wochentag, Von und Bis ausfüllen, dann **Hinzufügen**. Erst danach erscheint die Sprechzeit in der Liste — sie steht dann auch wirklich in der Datenbank.

**Ändern:** Werte in der Zeile anpassen, dann **Speichern** *in derselben Zeile*.

**Entfernen:** **Entfernen** in der jeweiligen Zeile.

> **Achtung — jede Sprechzeit wird einzeln gespeichert.**
> Der große Knopf **Speichern** über dem Bereich sichert die **Stammdaten** des Angebots — Titel, Raum, Kontakt. Er übernimmt **keine** Sprechzeiten.
>
> Sechs Personen der Studie äußerten sich dazu, zwei trugen Sprechzeiten ein und speicherten sie nicht.
>
> Die Oberfläche hilft Ihnen dabei: Eine geänderte, noch nicht gespeicherte Zeile bekommt links einen **blauen Balken**, und ihr eigener *Speichern*-Knopf wird **blau**. Drücken Sie den großen Speichern-Knopf, während Zeilen offen sind, erscheint zusätzlich ein Hinweis. Solange irgendwo Blau zu sehen ist, ist etwas noch nicht gesichert.

### Einzeltermine

Steht eine Beratung nicht wöchentlich, sondern einmalig an, wählen Sie als Wochentag **Einzeltermin**. Dann erscheinen zwei zusätzliche Felder, *gültig von* und *gültig bis*. Ein Einzeltermin **ohne Datum** wird abgewiesen — ohne Datum wäre es eine Uhrzeit, die nie stattfindet.

### Veröffentlichen

Als Personal können Sie Ihr Angebot **inhaltlich vollständig pflegen, aber nicht veröffentlichen**. Statt des Kästchens *Veröffentlicht* sehen Sie einen Hinweis auf die fehlende Berechtigung. Die Freigabe erteilt die Projektleitung oder die Administration — dieselbe Trennung wie bei den Campus-Punkten.

### Gebäude

Gebäude können Sie **ansehen, aber nicht ändern**. Sie sind gemeinsam genutzte Stammdaten: An einem Gebäude hängen die Campus-Punkte aller Fachgebiete. Den Raum Ihrer Beratung tragen Sie am **Angebot** ein, nicht am Gebäude — und abweichende Räume einzelner Sprechzeiten im Feld der jeweiligen Zeile.

---

## 6 · Administration — Konten, Rollen, Gebäude

Die Administration hat alle Berechtigungen des Systems. Zusätzlich zu den Aufgaben der Projektleitung:

### Gebäude pflegen

**Gebäude → Gebäude anlegen** oder einen bestehenden Eintrag öffnen. Pflichtfelder sind Schlüssel und deutscher Name; der Schlüssel (etwa `S1|03`) ist eindeutig.

Zwei Dinge sind hier folgenreich:

- Der **Schlüssel** verbindet den Datensatz mit dem Modell in der 3D-Szene. Ändern Sie ihn, verschwindet das Gebäude dort.
- Die **Position** ist der Ankerpunkt für alle Campus-Punkte des Gebäudes. Verschieben Sie das Gebäude, verschieben sich die Punkte mit.

Ein Gebäude, an dem noch Campus-Punkte hängen, lässt sich **nicht löschen**; die Anwendung weist das ab.

### Konten sperren

Auf der Detailseite eines Kontos. Eine Sperre wirkt **sofort**, auch bei laufender Sitzung: Die betroffene Person wird beim nächsten Klick abgemeldet und kann sich nicht erneut anmelden.

### Passwort zurücksetzen

Ebenfalls auf der Detailseite. Erzeugt ein neues Initialpasswort, das **einmalig** angezeigt wird. Die Person muss es bei der nächsten Anmeldung ändern.

### Rollen und Rechte einsehen

**Rollen & Rechte** zeigt die vollständige Matrix aus Rollen und Berechtigungen. Die Seite ist eine Nachschlagehilfe — sie zeigt, welche Rolle was darf. Ändern lässt sich das Modell über die Oberfläche nicht; es ist Teil der Anwendung.

---

## 7 · Technischer Betrieb — das Protokoll lesen

Die Rolle **Technischer Betrieb** sieht das Audit-Log und das Rechtemodell, aber **keine Inhalte und keine Nutzerdaten**. Das ist Absicht: Betriebsaufgaben brauchen Einblick in Vorgänge, nicht in deren Inhalt.

### Audit-Log

**Audit-Log** zeigt die Tabelle mit fünf Spalten:

| Spalte | Inhalt |
|---|---|
| **Zeitpunkt** | Wann, in Ihrer Zeitzone |
| **Konto** | Wer — oder *anonym* |
| **Aktion** | Was, als Code, etwa `ROLE_ASSIGNED` |
| **Ressource** | Woran, als Typ und Kennung, etwa `CONSULTATION #2` |
| **Ergebnis** | Erfolgreich oder abgewiesen mit Fehlercode |

**Abgewiesene Versuche stehen ebenfalls im Protokoll**, farblich abgesetzt und mit ihrem Fehlercode. Ein Protokoll, das nur Gelungenes festhält, wäre für die Nachvollziehbarkeit wertlos.

Über das Filterfeld schränken Sie auf eine Aktion ein, etwa `ROLE_ASSIGNED`.

> **Achtung — Umfang der Ansicht.**
> Die Seite lädt die **letzten 50 Einträge**. Es gibt keine Seitenblätterung. Ein älterer Vorgang kann dadurch aus der Ansicht gerutscht sein. Fünf Personen der Studie wünschten sich hier mehr Filter, Sortierung und mitlaufende Spaltenüberschriften.
>
> Die Spalte **Ressource** ist der verlässlichste Einstieg, wenn Sie wissen, welcher Datensatz betroffen war: Typ und Kennung stehen dort zusammen.

### Was das Protokoll nicht zeigt

Welche **Feldwerte** sich geändert haben, bietet die Oberfläche nicht an. Sie beantwortet: wer, wann, woran, mit welchem Ergebnis.

Passwörter und Schlüssel erscheinen **nie** im Protokoll, auch nicht in den Vorher-Nachher-Angaben.

---

## 8 · Externe Person — veröffentlichte Inhalte

Konten dieser Rolle entstehen durch Selbstregistrierung. Sie sehen **keine Verwaltungsoberfläche**, sondern ausschließlich veröffentlichte Inhalte und den 3D-Campus unter `/play`.

Der 3D-Campus ist nicht Gegenstand dieser Anleitung. Für die Verwaltung ist nur eines wichtig: **Was dort erscheint, hängt an den Berechtigungen des angemeldeten Kontos.** Eine externe Person sieht nur Veröffentlichtes; wer Entwürfe sehen darf, sieht sie dort zusätzlich und erkennt sie an der Kennzeichnung. Dieselbe Adresse zeigt also je nach Konto verschiedene Szenen.

---

## 9 · Häufige Stolpersteine auf einen Blick

| Situation | Was zu tun ist |
|---|---|
| Inhalt angelegt, erscheint nicht bei der Prüfung | Nach dem Speichern zusätzlich **Zur Prüfung einreichen**. Status prüfen: steht dort *Entwurf*, fehlt der Schritt. |
| Korrektur eingereicht, alte Fassung kommt an | Erst **Speichern**, dann **Einreichen**. Das Einreichen übernimmt keine ungespeicherten Änderungen. |
| *Freigeben* oder *Zurückweisen* nicht zu finden | Den Eintrag **öffnen** — die Knöpfe stehen im Editor, nicht in der Liste. Und nur bei Status *In Prüfung*. |
| Sprechzeit eingetragen, nach dem Neuladen weg | Jede Zeile hat ihr **eigenes Speichern**. Blauer Balken oder blauer Knopf heißt: nicht gesichert. |
| Rollenänderung ohne Bestätigung | Rollen wirken **sofort**. Kein Speichern-Knopf. Die Chips zeigen den aktuellen Stand. |
| Alte Rolle noch vorhanden | Beim Tausch: erst neue hinzufügen, **dann alte entfernen**. |
| Seite meldet fehlende Berechtigung | Die Meldung nennt die Berechtigung. Fachlich zuständige Rolle einschalten, nicht umgehen. |
| Nach der ersten Anmeldung nur Dashboard | Erst das Passwort ändern. Die volle Rolle greift nach der erneuten Anmeldung. |
| Gesuchter Vorgang nicht im Protokoll | Die Ansicht zeigt die letzten 50 Einträge. Über die Aktion filtern. |

---

## 10 · Wenn etwas nicht funktioniert

**Sie werden plötzlich abgemeldet.** Entweder ist Ihre Sitzung abgelaufen, oder Ihr Konto wurde gesperrt. Melden Sie sich erneut an. Erscheint *Dieses Konto ist gesperrt*, wenden Sie sich an die Administration.

**Ein Feld wird rot markiert.** Unter dem Feld steht, was fehlt oder nicht stimmt. Die Prüfung erfolgt serverseitig; die Meldung kommt also aus der Anwendung und nicht aus dem Browser.

**Sie sind unsicher, ob etwas gespeichert wurde.** Verlassen Sie die Seite und öffnen Sie den Datensatz erneut. Was dann angezeigt wird, steht in der Datenbank.

**Ein Knopf fehlt, den Sie erwarten.** Zwei mögliche Gründe: Ihnen fehlt die Berechtigung, oder der Datensatz ist im falschen Status. Die Oberfläche bietet nur an, was auch durchgeht.

---

*Diese Anleitung beschreibt die Verwaltungsoberfläche. Die Hinweiskästen stützen sich auf eine moderierte Usability-Studie mit vierzehn Personen; sie benennen Stellen, an denen Bedienung erfahrungsgemäß misslingt, und keine Mängel einzelner Personen.*
