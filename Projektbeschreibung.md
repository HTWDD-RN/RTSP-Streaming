# Projektrahmen
Im Praktikum werden Sie einen Client und Server für Videostreaming unter Nutzung des Real-Time-Streaming-Protokolls [RTSP](http://www.ietf.org/rfc/rfc2326.txt) implementieren. Die eigentlichen Videodaten werden mittels Real-Time-Protokoll [RTP](http://www.ietf.org/rfc/rfc3550.txt) übertragen. Ein großer Teil der Funktionalität ist bereits als Quelltext vorhanden, so das RTSP-Protokoll für den Server, das RTP-Handling im Client sowie die Videoanzeige.
Ihre Aufgabe besteht im Wesentlichen aus der Ergänzung der Quellcodes in den Punkten:
* RTSP-Protokoll vervollständigen
* RTP-Protokoll, Header im Server setzen
* FEC in Client, Paketkorrektur implementieren

## 1. Java-Klassen
Das Projekt besteht aus mehreren Java-Klassen, die je nach Funktionsumfang für Client, Server oder auch beide Anwendungen eingesetzt werden.
Die bereitgestellten Kassen mit der Endung `Demo` sind abstrakte Klassen, die noch ausprogrammiert werden müssen und den Klassennamen ohne `Demo` erhalten.

### Server-seitige Klassen
* [Server](src/Server.java): Funktionalität des Servers zur Antwort auf die RTSP-Clientanfragen und Streaming des Videos
* [AviMetadataParser](AviMetadataParser.java): Extrahiert Metadaten aus AVI-Dateien
* [QuickTimeMetadataParser](QuickTimeMetadataParser.java): Extrahiert Metadaten aus Quicktime-Movie-Dateien
* [VideoReader](src/VideoReader.java): Einlesen einer MJPEG-Datei auf der Serverseite

### Client-seitige Klassen
* [Client](src/Client.java): Funktionalität des Clients mit Benutzerschnittstelle zum Senden der RTSP-Kommandos und Anzeige des Videos
* [ReceptionStatistic](src/ReceptionStatistic.java): Bereitstellung von Empfangsstatistiken

### Klassen für Server und Client
* [RtspDemo](src/RtspDemo.java): Implementierung des RTSP-Protokolls für Client und Server
* [RtpHandler](src/RtpHandler.java): Verarbeitung von RTP-Paketen
* [RTPpacketDemo](src/RTPpacketDemo.java): Funktionalität zur Unterstützung von RTP-Paketen
* [FecHandlerDemo](src/FecHandlerDemo.java): Unterstützung der Fehlerkorrektur mittels FEC
* [FECpacket](src/FECpacket.java): Erweiterung der RTP-Klasse mit FEC-Funktionalität
* [VideoMetadata](VideoMetadata.java): Video-Metadaten wie Framerate und Abspieldauer
* [JpegFrame](src/JpegFrame): Codierung/Decodierung von JPEG-Bildern gemäß RFC-2435
* [SrtpHandler](src/SrtpHandler.java): Verschlüsselung von RTP-Paketen
* [JpegEncryptionHandler](src/JpegEncryptionHandler.java): Verschlüsselung von JPEG-Bildern (Quantisierungstabellen)
* [CustomLoggingHandler](CustomLoggingHandler.java): Anpassung der Logger-Ausgaben für minimalen Overhead


## 2. Programmstart
Der Start des Servers erfolgt mittels `java Server RTSP-Port`. Der Standard-RTSP-Port ist 554, da Sie aber im Praktikum einen Port > 1024 nutzen müssen, bietet sich der alternative Port 8554 an. 
Sie können den Server auch mit den optionalen Parametern `java Server RTSP-Port lossRate groupSize` starten um die FEC-Optionen schon beim Progammstart zu konfigurieren.

Der Start des Clients erfolgt mittels `java Client server_name server_port video_file`. Am Client können RTSP-Kommandos angefordert werden. 
Eine RTSP-Kommunikation läuft in der Regel folgendermaßen ab:  
1. Client fragt vorhandene Servermethoden mittels OPTIONS ab.
2. Client sendet DESCRIBE: Analyse der vorhandenen Streams und Parameter einer gewünschten Präsentation
3. Client sendet SETUP: Erzeugung der Session und der Transportparameter anhand der vorab ermittelten Parameter
4. Client sendet PLAY 
5. Client sendet u.U. PAUSE
6. Client sendet TEARDOWN: Terminierung der Session.
Der Server antwortet auf alle Clientrequests. Die Antwortcodes sind ähnlich zu HTTP. Der Code 200 bedeutet z.B. Erfolg. Die RTSP-Antwortcodes finden Sie in [RTSP](http://www.ietf.org/rfc/rfc2326.txt).

## 3. Client
Als ersten Schritt sollte das RTSP-Protokoll in der entsprechenden Klasse vervollständigt werden. Für die RTSP-Kommunikation mit dem Server wird der bereits geöffnete Socket verwendet. In jeden Request muss ein CSeq-Header eingefügt werden. Der Wert von CSeq erhöht sich bei jedem Senderequest.

### Setup
* Erzeugen eines Sockets für den Empfang der RTP-Pakete und setzen des Timeouts (5 ms)
* Senden des SETUP-Requests an den Server, Ergänzung des Transportheaders mit dem geöffneten RTP-Port.
* Einlesen der RTSP-Antwort vom Server und parsen des Sessionheaders für die Session-ID

### Play
* Senden des PLAY-Requests mit Sessionheader und Session-ID (kein Transportheader)
* Einlesen der RTSP-Antwort

### Pause
* Senden des PAUSE-Requests mit Sessionheader und Session-ID
* Einlesen der RTSP-Antwort

### Teardown
* Senden des TEARDOWN-Requests mit Sessionheader und Session-ID
* Einlesen der RTSP-Antwort

### Beispiel
Bitte beachten Sie, dass der im Praktikum verwendete RTSP-Parser im Client und Server nur eine Untermenge an möglichen Attributen unterstützt. Im Zweifelsfall schauen Sie bitte in die jeweilige Implementierung. 
Sie können sich an dem folgenden Beispiel orientieren (C-Client, S-Server). Je nach Konfiguration Ihres Rechners müssen Sie unter Umständen mit FQDN arbeiten (z.B. idefix.informatik.htw-dresden.de).
```
C: OPTIONS * RTSP/1.0
 : CSeq: 1

S: RTSP/1.0 200 OK
 : CSeq: 1
 : Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE

 
C: DESCRIBE rtsp://idefix/htw.mjpeg RTSP/1.0
 : CSeq: 2

S: RTSP/1.0 200 OK
 : CSeq: 2
 : Content-Base: rtsp://idefix/htw.mjpeg
 : Content-Type: application/sdp
 : Content-Length: 460
 :
 : v=0 
 : m=video 0 RTP/AVP 96
 : [...]

   
C: SETUP rtsp://idefix/htw.mjpeg/trackID=0 RTSP/1.0
 : CSeq: 3
 : Transport: RTP/AVP;unicast;client_port=25000-25001

S: RTSP/1.0 200 OK
 : CSeq: 3
 : Transport: RTP/AVP;unicast;client_port=25000-250001;server_port=3333-3334
 : Session: 123456;timeout=30000

 
C: PLAY rtsp://idefix/htw.mjpeg RTSP/1.0
 : CSeq: 4
 : Session: 123456

S: RTSP/1.0 200 OK
 : CSeq: 4
 : Session: 123456

 
C: PAUSE rtsp://idefix/htw.mjpeg RTSP/1.0
 : CSeq: 5
 : Session: 123456

S: RTSP/1.0 200 OK
 : CSeq: 5
 : Session: 123456

 
C: TEARDOWN rtsp://idefix/htw.mjpeg RTSP/1.0
 : CSeq: 6
 : Session: 123456
```

### Zustände des Clients
Im RTSP-Protokoll hat jede Session einen bestimmten Zustand. Sie müssen den Zustand des Clients entsprechend aktualisieren.

![RTP-Zustände](images/rtp-state.gif)

## 4. Server
Auf Serverseite muss das Einbetten der Videodaten in die RTP-Pakete erfolgen. Die beinhaltet das Erzeugen des Paketes, Setzen der Headerfelder und setzen der Payload. Für Informationen zur Bitmanipulation in Java siehe **Vorlesungsfolien zu RTP**.
Im Beleg wird nur eine Quelle genutzt (CC=0), das Feld CSRC existiert also nicht. Die Länge des RTP-Headers beträgt demzufolge 12 Byte.

<!-- ![RTP-Header](images/rtp-header.png)   -->

```
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P|X|  CC   |M|     PT      |       sequence number         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                           timestamp                           |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |           synchronization source (SSRC) identifier            |
   +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
   |            contributing source (CSRC) identifiers             |
   |                             ....                              |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## 5. Literatur

[1]: Kurose, Ross "Computernetzwerke", Pearson  
[2]: www.ietf.org/rfc/rfc2326.txt (RTSP)

