# Aufgabenstellung RTSP-Streaming
Die Aufgaben beziehen sich auf den Beleg Videostreaming für das Modul Internettechnologien 2.

## Aufgaben
### 1. RTSP-Protokoll: Client-Methoden
Programmieren Sie die Klasse Rtsp.java entsprechend der in der Projektbeschreibung und den Kommentaren im Quelltext der abstrakten Klasse gegebenen Hinweisen.

### 2. RTSP-Methoden: Server-Methoden
Ergänzen Sie die RTSP-Methoden OPTIONS und DESCRIBE anhand der Beispiele aus [RFC 2326](https://www.ietf.org/rfc/rfc2326.txt) und [RFC 2327](https://www.ietf.org/rfc/rfc2327.txt). 
Die Serverantwort muss im Client nicht ausgewertet werden. Die Anzeige der Antwort in der Konsole des Clients genügt.

Es ist ausreichend, sich bei der DESCRIBE-Methode auf das Beispielvideo zu beziehen und die Antwort auf dem Server statisch zu hinterlegen. 
Ausgewertet werden die u.a. die Parameter framerate und range.

### 3. RTP-Protokoll
Programmieren Sie die Klasse RTPpacket entsprechend der Projektbeschreibung und den Kommentaren im Quelltext der abstrakten Klasse gegebenen Hinweisen.

### 4. Auswertung der Fehlerstatistiken ohne Fehlerkorrektur
Sie können an der GUI des Servers eine Paketfehlerwahrscheinlichkeit einstellen und damit Netzwerkfehler simulieren. Probieren Sie verschiedene Einstellungen aus und betrachten Sie das Ergebnis in der Videoanzeige. 
Ab welcher Paketfehlerwahrscheinlichkeit wird die Videoanzeige spürbar beeinträchtigt?

Mit dem ersten Punkt kann die Qualität der Verbindung eingeschätzt werden und mit dem zweiten Punkt die Leistungsfähigkeit des FEC-Verfahrens.
Machen Sie sich Gedanken über weitere zu überwachende Parameter. Die meisten dieser Daten können aus der Klasse ReceptionStatistic entnommen werden. Verifizieren Sie die korrekte Berechnung dieser Werte.

### 5. Implementierung des FEC-Schutzes
Implementieren Sie einen FEC-Schutz gemäß [RFC 5109](https://www.ietf.org/rfc/rfc5109.txt).
Der Server mit FEC-Schutz soll kompatibel zu Clients ohne FEC-Verfahren sein! Nutzen Sie dazu das Feld Payloadtype des RTP-Headers (PT=127 für FEC-Pakete).

Um nicht die komplette FEC-Funktionalität selbst entwickeln zu müssen, werden Ihnen zwei Klassen bereit gestellt:
1. [FECpacket](src/FECpacket.java): dies ist eine aus RTPpacket abgeleitete Klasse mit der erweiterten Funktionalität für das Handling von FEC-Paketen (vollständig implementiert)
2. [FecHandler](src/Fechandler.java): diese Klasse ist zuständig für die server- und clientseitige FEC-Bearbeitung unter Nutzung von FECpacket (teilweise implementiert)
   * Server: Kombination mehrerer Medienpakete zu einem FEC-Paket
   * Client: Jitterpuffer für empfangene Medien- und FEC-Pakete, Bereitstellung des aktuellen Bildinhaltes in Form einer Liste von RTP-Paketen mit gleichem TimeStamp.

Die Fehlerkorrektur im FecHandler ist noch zu implementieren. Dazu ist die vorhandene Architektur zu analysieren und die abstrakten Methoden sind auszuprogrammieren.
Eine Übersicht über die relevanten Datenstrukturen und ein Beispiel ist hier zu finden [FEC-Diagramme](https://www2.htw-dresden.de/~jvogt/it2/fec-diagramme.html)

#### Architektur der Paketverarbeitung
##### Server
* der Server steuert die Verarbeitung im vorhandenen Timer-Handler
* Nutzdaten erstellen und speichern: `RtpHandler.jpegToRtpPacket()`
* Nutzdaten senden
* Prüfung auf Erreichen der Gruppengröße: `RtpHandler.isFecPacketAvailable()`
* nach Ablauf des Gruppenzählers berechnetes FEC-Paket entgegennehmen und senden: `RtpHandler.createFecPacket()`
* Kanalemulator jeweils für Medien- und FEC-Pakete aufrufen: `sendPacketWithError()`

##### Client
* Der Client nutzt getrennte Timer-Handler für den Empfang der Pakete und für das periodische Anzeigen der Bilder (keine Threads notwendig).
* Pakete empfangen per Timer
* Pakete im Jitterpuffer speichern: `RtpHandler.processRtpPacket()`
* Statistiken aktualisieren
* zur richtigen Zeit (Timeraufruf) das nächste Bild anzeigen: `RtpHandler.nextPlaybackImage()`
    * Timer läuft standardmäßig mit 25Hz oder Abspielgeschwindigkeit des Videos, wenn diese per SDP übermittelt
* Verzögerung des Starts des Abspielens (ca. 2s), um den Jitterpuffer zu füllen

##### RtpHandler
* Server
    * Registrierung eines RTP-Paketes im FecHandler bei dessen Erstellung
    * Abruf fertiggestellter FEC-Pakete über den FeCHandler
* Client
    * Speicherung ankommender RTP-Pakete getrennt nach PayloadType (JPEG-Pakete im RtpHandler, FEC-Pakete im FecHandler)
    * Bei Anfrage des nächsten Bildes Generierung einer Liste aller RTP-Pakete für dieses Bild (gleicher Timestamp)
    * Überprüfung der Korrektur für fehlende RTP-Pakete per `FecHandler.checkCorrection()` und ggf. Korrektur über `FecHandler.correctRTP()`

##### FecHandler
* Generierung einer Liste aller betroffenen RTP-Pakete für jedes FEC-Paket
* Speicherung der Sequenznummer des FEC-Packets und der Liste aller betroffenen RTP-Pakete für jedes RTP-Paket in zwei HashMaps (fecNr, fecList)
* periodisches Löschen alter nicht mehr benötigter Einträge im Jitterpuffer

##### FECpacket
* Ableitung aus vorhandenem RTPpacket
* Sender: Konstruktor zur Generierung eines FEC-Objektes aus Media-RTPs
* Empfänger: Konstruktur zur Generierung eines FEC-Objektes aus einem empfangenen FEC-RTP
* getRtpList: Ermittlung aller in einem FEC involvierten Media-RTPs
* getPacket: Holt komplettes FEC-Paket als Bytearray
* addRtp: fügt ein Media-RTP zum FEC-Objekt hinzu, inklusive aller notwendigen Berechnungen
* getLostRtp: generiert das verlorene Media-RTP aus den vorhandenen mittels addRtp hinzugefügten RTPs

#### Debugging
Es ist relativ unwahrscheinlich, dass das Programm auf Anhieb fehlerfrei funktioniert. Deshalb ist es wichtig, ein Konzept für die Fehlersuche zu entwickeln.
Hier einige Tipps für die Fehlersuche:
* Anzeige von Statusinformationen analog zu printheaders() des RTPpackets()
* Anzeige der ersten Bytes des Payload auf Sender und Empfänger
* prüfen des Senders auf korrekte Pakete
* Einstellung eines festen Seeds des Kanalsimulators für wiederholbare Versuche
* Test ohne bzw. mit Fehlerkorrektur
* Test der Anzahl verlorener / wiederhergestellter Pakete auf Plausibilität


### 6. Analyse der Leistungsfähigkeit des implementieren FEC-Verfahrens
#### Parameterwahl
Finden Sie den optimalen Wert für k bei einer Kanalverlustrate von 10%. Optimal bedeutet in diesem Fall eine subjektiv zufriedenstellende Bildqualität bei geringstmöglicher Redundanz.

#### Bestimmung der theoretisch zu erwartenden Verlustraten
Versuchen Sie, mathematisch die Paketverlustwahrscheinlichkeit (Restfehler) für verschiedene Gruppengrößen (k=2, 3, 5, 10, 20, 48) zu bestimmen.
Tragen Sie die Ergebnisse in einem Diagramm über der Kanalfehlerwahrscheinlichkeit auf. Sie können hierfür Gnuplot, R oder ein anderes Tool nutzen. Tragen Sie in das Diagramm zusätzlich mit Ihrem Videostreaming praktisch gemessene Fehlerhäufigkeiten auf (k=2 und k=48). Diskutieren Sie eventuelle Unterschiede zum theoretisch ermittelten Ergebnis.

Für diese Aufgabe unterstützt Sie die Statistik am Empfänger mit dem Werten:
1. aktuelle Puffergröße
2. letzte empfangene Sequenznummer
3. Anzahl erhaltener / verlorener Medienpakete + prozentuale Angabe verlorener Medienpakete
4. Anzahl korrigierter / unkorrigierbarer Medienpakete + prozentuale Angabe unkorrigierbarer Medienpakete
5. Abspielzähler (Pakete / Bilder)
6. verlorene Bilder

### 7. Kompatibilität des Demoprojektes
Prüfen Sie die Kompatibilität des Clients und Servers mit dem VLC-Player und versuchen Sie eventuelle Probleme zu analysieren. Bei Problemen mit VLC 3 versuchen Sie VLC 2.2.

### 8. Videoformat 
Binden Sie ein kurzes (ca. 0,5 - 2 min) Video Ihrer Wahl ein. Eine Umcodierung zu MJPEG kann zum Beispiel mittels FFMPEG oder VLC-Player erfolgen. Eventuell müssen Sie die Auflösung des Videos verringern, damit die Bilder jeweils in ein UDP-Paket passen.

`ffmpeg -i test.mp4 -vcodec mjpeg -q:v 10 -huffman 0 -r 10 -vf scale=720:-1 -an test.mjpeg`

### 9. Vorschläge
Manchen Sie konkrete Vorschläge zur Verbesserung des Belegs.

## Lernaspekte des Belegs
* Kommunikationsprotokolle
  * Internetprotokolle, RFCs
* Programmierung
  * JAVA-Programmierung
  * Debugging
* Softwarearchitektur
  * Entwurfsmuster, FSM
* Mathematik/Stochastik
  * Fehleranalyse
* Grundlagen der Informatik
  * Datenformate
  * Serialisierung
* Betriebssysteme
  * Shellscripte
  * Plattformunabhängigkeit


## Literatur
* Real Time Streaming Protocol (RTSP)                   [RFC 2326](http://www.ietf.org/rfc/rfc2326.txt)
* SDP: Session Description Protocol                     [RFC2327](http://www.ietf.org/rfc/rfc2327.txt)
* RTP: A Transport Protocol for Real-Time Applications  [RFC 3550](http://www.ietf.org/rfc/rfc3550.txt)
* RTP Payload Format for JPEG-compressed Video          [RFC 2435](http://www.ietf.org/rfc/rfc2435.txt)
* RTP Profile for Audio and Video Conferences with Minimal Control  [RFC 3551](http://www.ietf.org/rfc/rfc3551.txt)
* RTP Payload Format for Generic Forward Error Correction  [RFC 5109](http://www.ietf.org/rfc/rfc5109.txt)
* Reed-Solomon Forward Error Correction (FEC) Schemes   [RFC 5510](http://www.ietf.org/rfc/rfc5510.txt)
* JPEG-Format [Link](https://de.wikipedia.org/wiki/JPEG_File_Interchange_Format)
* Diplomarbeit Karsten Pohl "Demonstration des RTSP-Videostreamings mittels VLC-Player und einer eigenen Implementierung"  [pdf](https://www2.htw-dresden.de/~jvogt/abschlussarbeiten/Pohl-Diplomarbeit.pdf)
* Diplomarbeit Elisa Zschorlich "Vergleich von Video-Streaming-Verfahren unter besonderer Berücksichtigung des Fehlerschutzes und Implementierung eines ausgewählten Verfahrens" [pdf](https://www2.htw-dresden.de/~jvogt/abschlussarbeiten/zschorlich-diplomarbeit.pdf)
