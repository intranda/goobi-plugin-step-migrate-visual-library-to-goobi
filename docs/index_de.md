---
title: Datenmigration aus Visual Library
identifier: intranda_step_migrate_visual_library_to_goobi
description: Step Plugin für die Migration von Bestandsdaten aus Visual Library nach Goobi
published: true
---

## Einführung
Diese Dokumentation erläutert das Plugin für eine automatische Datenübernahme von Digitalisaten aus einer Visual Library Instanz nach Goobi workflow. Hierbei wird die öffentlich erreichbare METS-Datei aus dem Visual Library System abgefragt. Aus dieser werden anschließend die Metadaten, die Strukturdaten, die Paginierung sowie auch die Bilder und die Volltexte übernommen. Unmittelbar nach der Datenübernahme können die Werke im Goobi viewer veröffentlicht werden.

## Installation
Um das Plugin nutzen zu können, müssen folgende Dateien installiert werden:

```bash
/opt/digiverso/goobi/plugins/step/plugin-step-migrate-visual-library-to-goobi-base.jar
/opt/digiverso/goobi/config/plugin_intranda_step_migrate_visual_library_to_goobi.xml
```

Nach der Installation des Plugins kann dieses innerhalb des Workflows für die jeweiligen Arbeitsschritte ausgewählt und somit automatisch ausgeführt werden. Ein Workflow könnte dabei beispielhaft wie folgt aussehen:

![Beispielhafter Aufbau eines Workflows](screen2_de.png)

Für die Verwendung des Plugins muss dieses in einem Arbeitsschritt ausgewählt sein:

![Konfiguration des Arbeitsschritts für die Nutzung des Plugins](screen3_de.png)


## Überblick und Funktionsweise
Nach dem Start des Plugins initialisiert sich dieses zunächst mit Informationen aus der Konfigurationsdatei. Anschließend ermittelt das Plugin aus einer konfigurierten Eigenschaft, welche OAI-Schnittstelle für die Abfrage einer METS-Datei verwendet werden soll. 

![Anlegen eines Vorgangs mit Angabe des Visual Library Identifiers](screen1_de.png)

Die METS-Datei wird daraufhin heruntergeladen und alle relevanten Metadaten daraus extrahiert und in die entsprechende METS-Datei in Goobi überführt. Das gleiche passiert hierbei ebenso für die Strukturdaten und die Paginierungssequenzen. 

![Importierte Metadaten, Strukturdaten und Bilder im Metadateneditor](screen4_de.png)

Abschließend startet das Plugin mit dem Download der Bilder aus der Dateigruppe `MAX`, um die beste verfügbare Qualität der Derivate herunterzuladen. Sollten für das abgefragte Werke Volltexte im Format `ALTO` vorliegen, werden diese ebenfalls heruntergeladen und im Goobi Vorgang gespeichert.

![Anzeige der importierten Volltexte im ALTO-Editor](screen5_de.png)

Nach dem erfolgreichen Import können die Werke anschließend weiterverarbeitet und auch z.B. im Goobi viewer veröffentlicht werden.

![Veröffentlichtes Werk im Goobi viewer](screen6_de.png)

![Anzeige der übernommenen Metadaten im Goobi viewer](screen7_de.png)

![Anzeige der übernommenen Strukturdaten mit Paginierungsinformationen im Goobi viewer](screen8_de.png)

## Konfiguration
Die Konfiguration des Plugins erfolgt in der Datei `plugin_intranda_step_migrate_visual_library_to_goobi.xml` wie hier aufgezeigt:

{{CONFIG_CONTENT}}

{{CONFIG_DESCRIPTION_PROJECT_STEP}}

Parameter               | Erläuterung
------------------------|------------------------------------
`vl-url`                | Angabe derjenigen Vorgangseigenschaft, in der die URL der OAI-Schnittstelle steht. Erwartet wird in dieser Eigenschaft eine URL wie beispielsweise `https://visuallibrary.net/ihd4/oai/?verb=GetRecord&metadataPrefix=mets&identifier=`
