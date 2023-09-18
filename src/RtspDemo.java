import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class RtspDemo {
  static final String CRLF = "\r\n";
  String rtspUrl = "";      // rtsp://hostname:port/


  public void setVideoFileName(String videoFileName) {
    VideoFileName = videoFileName;
  }

  public String getVideoFileName() {    return VideoFileName;  }

  String VideoFileName;   // video file requested from the client
  int RTP_RCV_PORT;
  BufferedWriter RTSPBufferedWriter;  // TCP-Stream for RTSP-Requests
  BufferedReader RTSPBufferedReader; // TCP-Stream for RTSP-Responses
  int RTSPSeqNb = 1;  // Init
  String RTSPid = "0"; // ID of the RTSP session (given by the RTSP Server)

  public int getFramerate() {
    return framerate;
  }
  int framerate = 0;
  public double getDuration() {
    return duration;
  }
  double duration = 0.0;
  enum State {INIT, READY, PLAYING}
  State state;
  static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);


  /*  *********************** Server   **************************** */
  static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
  static String VideoDir = "videos/";
  String sdpTransportLine = "";
  public int getRTP_dest_port() {
    return RTP_dest_port;
  }
  private int RTP_dest_port; // destination port for RTP packets  (given by the RTSP Client)
  public int getFEC_dest_port() {
    return FEC_dest_port;
  }
  private int FEC_dest_port; // destination port for RTP-FEC packets  (RTP or RTP+2)
  static final int SETUP = 3;
  static final int PLAY = 4;
  static final int PAUSE = 5;
  static final int TEARDOWN = 6;
  static final int OPTIONS = 7;
  static final int DESCRIBE = 8;
  static int RTSP_ID = 123456; // ID of the RTSP session

  public RtspDemo(BufferedReader RTSPBufferedReader, BufferedWriter RTSPBufferedWriter,
      int rtpRcvPort, String rtspUrl, String videoFileName) {
    this.rtspUrl = rtspUrl;
    this.RTP_RCV_PORT = rtpRcvPort;
    this.VideoFileName = videoFileName;
    this.RTSPBufferedReader = RTSPBufferedReader;
    this.RTSPBufferedWriter = RTSPBufferedWriter;
    this.state = State.INIT;
  }

  public RtspDemo(BufferedReader RTSPBufferedReader, BufferedWriter RTSPBufferedWriter) {
    this.RTSPBufferedReader = RTSPBufferedReader;
    this.RTSPBufferedWriter = RTSPBufferedWriter;
    this.state = State.INIT;
  }

   boolean setup() {
    if (state != State.INIT) {
      logger.log(Level.WARNING, "RTSP state: " + state);
      return false;
    }
    RTSPSeqNb++;
    send_RTSP_request("SETUP");
    // Wait for the response
    logger.log(Level.INFO, "Wait for response...");
    if (parse_server_response() != 200) {
      logger.log(Level.WARNING, "Invalid Server Response");
      return false;
    } else {
      state = State.READY;
      logger.log(Level.INFO, "New RTSP state: READY\n");
      return true;
    }
  }

  abstract boolean play();

  abstract boolean pause();

  abstract boolean teardown();

  abstract void describe();
  abstract void options();

  /**
   * Sends a RTSP request to the server
   * @param request_type String with request type (e.g. SETUP)
   */
  abstract void send_RTSP_request(String request_type);

  /**
   * Parse the server response
   * @return Reply code from server
   */
   int parse_server_response() {
    int reply_code = 0;
    int cl = 0;  // content length

    // logger.log(Level.INFO, "Waiting for Server response...");
    try {
      // parse the whole reply
      ArrayList<String> respLines = new ArrayList<>();

      String line;
      do {
        line = RTSPBufferedReader.readLine();
        logger.log(Level.CONFIG, line);
        if (!line.isEmpty()) {
          respLines.add(line);
        }
      } while (!line.isEmpty());
      ListIterator<String> respIter = respLines.listIterator(0);

      StringTokenizer tokens = new StringTokenizer(respIter.next());
      tokens.nextToken(); // skip over the RTSP version
      reply_code = Integer.parseInt(tokens.nextToken());

      while (respIter.hasNext()) {
        line = respIter.next();
        StringTokenizer headerField = new StringTokenizer(line);

        switch (headerField.nextToken().toLowerCase()) {
          case "cseq:":
            logger.log(Level.FINE, "SNr: " + headerField.nextToken());
            break;

          case "session:":
            if (state == State.INIT) {
              RTSPid = headerField.nextToken().split(";")[0]; // cat semicolon
            }
            break;

          case "content-length:":
            cl = Integer.parseInt(headerField.nextToken());
            break;

          case "public:":
            logger.log(Level.INFO, "Options-Response: " + headerField.nextToken());
            break;

          case "content-type:":
            String ct = headerField.nextToken();
            logger.log(Level.INFO, "Content-Type: " + ct);
            break;

          case "transport:":
            logger.log(Level.INFO, "");
            break;

          default:
            logger.log(Level.INFO, "Unknown: " + line);
        }
      }
      logger.log(Level.INFO, "*** Response received ***\n----------------");

      // Describe will send content
      if (cl > 0) {
        parseSDP(cl);
      }

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }

    if (reply_code != 200) {
      logger.log(Level.WARNING, "Invalid Server Response");
    }
    return (reply_code);
  }

   void parseSDP(int cl) throws Exception {
    char[] cbuf = new char[cl];
    logger.log(Level.INFO, "*** Parsing Response Data...");
    int data = RTSPBufferedReader.read(cbuf, 0, cl);
    logger.log(Level.INFO, "Data: " + data);
    logger.log(Level.INFO, new String(cbuf));

    String[] sbuf = new String(cbuf).split(CRLF);
    for (int i = 0; i < sbuf.length; i++) {
      if (sbuf[i].contains("framerate")) {
        String sfr = sbuf[i].split(":")[1];
        framerate = Integer.parseInt(sfr);
        logger.log(Level.INFO, "framerate: " + framerate);
      } else if (sbuf[i].contains("range:npt")) {
        String[] sdur = sbuf[i].split("-");
        if (sdur.length > 1) {
          duration = Double.parseDouble(sdur[1]);
          logger.log(Level.INFO, "duration [s]: " + duration);
        } // else: no duration available
      } // else: other attributes are not recognized here
    }
    logger.log(Level.INFO, "Finished Content Reading...");
  }

  /* *********************  RTSP for Server   *************************************** */

  /** Creates a OPTIONS response string
   * @return  Options string, starting with: Public: ...
   */
  abstract String getOptions();

  /**
   * Creates a DESCRIBE response string
   * @return String with content
   */
  abstract String getDescribe(VideoMetadata meta, int RTP_dest_port);


  /**
   * Parse RTSP-Request
   *
   * @return RTSP-Request Type (SETUP, PLAY, etc.)
   */
   int parse_RTSP_request() {
    int request_type = -1;
    try {
      logger.log(Level.INFO, "*** wait for RTSP-Request ***");
      // parse request line and extract the request_type:
      String RequestLine = RTSPBufferedReader.readLine();
      // System.out.println("RTSP Server - Received from Client:");
      logger.log(Level.CONFIG, RequestLine);

      StringTokenizer tokens = new StringTokenizer(RequestLine);
      String request_type_string = tokens.nextToken();

      // convert to request_type structure:
      request_type = switch ((request_type_string)) {
        case "SETUP"    -> SETUP;
        case "PLAY"     -> PLAY;
        case "PAUSE"    -> PAUSE;
        case "TEARDOWN" -> TEARDOWN;
        case "OPTIONS"  -> OPTIONS;
        case "DESCRIBE" -> DESCRIBE;
        default -> request_type;
      };

      if (request_type == SETUP
          || request_type == DESCRIBE) {
        // extract VideoFileName from RequestLine
        String dir = tokens.nextToken();
        //String[] tok = dir.split(".+?/(?=[^/]+$)");
        String[] tok = dir.split("/");
        //VideoFileName = VideoDir + tok[1];
        VideoFileName = VideoDir + tok[3];
        logger.log(Level.CONFIG, "File: " + VideoFileName);
      }

      String line;
      line = RTSPBufferedReader.readLine();
      while (!line.isEmpty()) {
        logger.log(Level.FINE, line);
        if (line.contains("CSeq")) {
          tokens = new StringTokenizer(line);
          tokens.nextToken();
          RTSPSeqNb = Integer.parseInt(tokens.nextToken());
        } else if (line.contains("Transport")) {
          sdpTransportLine = line;
          RTP_dest_port = Integer.parseInt( line.split("=")[1].split("-")[0] );
          FEC_dest_port = RTP_dest_port + 0;
          logger.log(Level.FINE, "Client-Port: " + RTP_dest_port);
        }
        // else is any other field, not checking for now

        line = RTSPBufferedReader.readLine();
      }

      logger.log(Level.INFO, "*** Request received ***\n");

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }
    return (request_type);
  }

  /**
   * Send RTSP Response
   *
   * @param method RTSP-Method
   */
   void send_RTSP_response(int method, int... localPort) {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    logger.log(Level.INFO, "*** send RTSP-Response ***");
    try {
      RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
      RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

      // 3th line depends on Request
      switch (method) {
        case OPTIONS:
          RTSPBufferedWriter.write( getOptions() );
          break;
        case DESCRIBE:
          VideoMetadata meta = Server.getVideoMetadata(VideoFileName);
          RTSPBufferedWriter.write( getDescribe(meta, RTP_dest_port ));
          break;
        case SETUP:
          RTSPBufferedWriter.write(sdpTransportLine + ";server_port=");
          RTSPBufferedWriter.write(localPort[0] + "-");
          RTSPBufferedWriter.write((localPort[0]+1) + CRLF);
          // RTSPBufferedWriter.write(";ssrc=0;mode=play" + CRLF);
        default:
          RTSPBufferedWriter.write("Session: " + RTSP_ID + ";timeout=30000" + CRLF);
          break;
      }

      // Send end of response
      if (method != DESCRIBE) RTSPBufferedWriter.write(CRLF);
      RTSPBufferedWriter.flush();
      logger.log(Level.FINE, "*** RTSP-Server - Sent response to Client ***");

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }
  }




}
