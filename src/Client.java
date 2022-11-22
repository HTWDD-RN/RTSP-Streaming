/* ------------------
Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
---------------------- */

import com.sun.source.util.TaskEvent;

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

public class Client {
  static int videoLength = 2800;

  // GUI
  // ----
  JFrame f = new JFrame("Client");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Teardown");
  JButton optionsButton = new JButton("Options");
  JButton describeButton = new JButton("Describe");
  JPanel mainPanel = new JPanel(); // Container
  JPanel buttonPanel = new JPanel(); // Buttons
  JPanel statsPanel = new JPanel();
  JPanel inputPanel = new JPanel();
  JLabel iconLabel = new JLabel(); // Image
  JLabel statusLabel = new JLabel("Status: "); // Statistics
  JLabel pufferLabel = new JLabel("Puffer: "); // Statistics
  JLabel statsLabel = new JLabel("Statistics: "); // Statistics
  JLabel fecLabel = new JLabel("FEC: "); // Statistics
  ImageIcon icon;
  JTextField textField = new JTextField("mystream", 30);
  JProgressBar progressBuffer = new JProgressBar(0, 100);
  JProgressBar progressPosition = new JProgressBar(0, videoLength);
  JCheckBox checkBoxFec = new JCheckBox("FEC");
  ButtonGroup encryptionButtons = null;

  int iteration = 0;

  // RTP variables:
  // ----------------
  DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
  //DatagramSocket FECsocket; // socket to be used to send and receive UDP packets for FEC
  private RtpHandler rtpHandler = null;
  static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets
  // static int FEC_RCV_PORT = 25002; // port where the client will receive the RTP packets

  static final int MAX_FRAME_SIZE = 65536;
  static final int RCV_RATE = 2; // interval for receiving loop
  int jitterBufferSize = 50; // size of the input buffer => start delay

  Timer timer; // timer used to receive data from the UDP socket
  Timer timerPlay; // timer used to display the frames at correct frame rate

  // RTSP variables
  // ----------------
  // rtsp states
  static final int INIT = 0;
  static final int READY = 1;
  static final int PLAYING = 2;
  static int state; // RTSP state == INIT or READY or PLAYING
  Socket RTSPsocket; // socket used to send/receive RTSP messages
  // input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String rtspServer;
  static int rtspPort;
  static String rtspUrl;
  static String VideoFileName; // video file to request to the server

  static int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
  String RTSPid = "0"; // ID of the RTSP session (given by the RTSP Server)

  static final String CRLF = "\r\n";
  static final String nl = System.getProperty("line.separator");

  // Video constants:
  // ------------------
  // static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
  static final int FRAME_RATE = 40;
  private int framerate = 0;
  private double duration = 0.0; // in s

  public Client() {
    rtpHandler = new RtpHandler(false);

    // build GUI
    // Frame
    f.addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            System.exit(0);
          }
        });

    // Buttons
    buttonPanel.setLayout(new GridLayout(1, 0));
    buttonPanel.add(setupButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);
    buttonPanel.add(optionsButton);
    buttonPanel.add(describeButton);
    setupButton.addActionListener(new setupButtonListener());
    playButton.addActionListener(new playButtonListener());
    pauseButton.addActionListener(new pauseButtonListener());
    tearButton.addActionListener(new tearButtonListener());
    optionsButton.addActionListener(new optionsButtonListener());
    describeButton.addActionListener(new describeButtonListener());

    // Image display label
    iconLabel.setIcon(null);

    // Text
    statsPanel.setLayout(new GridLayout(5, 0));
    statsPanel.add(statusLabel);
    statsPanel.add(pufferLabel);
    statsPanel.add(statsLabel);
    statsPanel.add(fecLabel);
    statsPanel.add(checkBoxFec);

    inputPanel.setLayout(new BorderLayout());
    inputPanel.add(textField, BorderLayout.SOUTH);

    JPanel encryptionPanel = initEncryptionPanel();

    // frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    mainPanel.add(encryptionPanel);
    mainPanel.add(statsPanel);
    mainPanel.add(progressBuffer);
    mainPanel.add(progressPosition);
    mainPanel.add(inputPanel);
    iconLabel.setBounds(0, 0, 640, 480);
    buttonPanel.setBounds(0, 480, 640, 50);
    encryptionPanel.setBounds(10, 530, 640, 30);
    statsPanel.setBounds(10, 560, 620, 150);
    progressBuffer.setBounds(10, 710, 620, 20);
    progressPosition.setBounds(10, 740, 620, 20);
    inputPanel.setBounds(10, 760, 620, 50);
    // inputPanel.setSize(620,50);

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(640, 800));
    f.setVisible(true);

    // init timer
    // --------------------------
    timer = new Timer(RCV_RATE, new timerListener());
    timer.setInitialDelay(0);
    timer.setCoalesce(true); // combines events
  }

  /**
   * Initialization of the GUI
   *
   * @param argv host port file
   * @throws Exception stacktrace at console
   */
  public static void main(String[] argv) throws Exception {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    CustomLoggingHandler.prepareLogger(logger);
    /* set logging level
     * Level.CONFIG: default information (incl. RTSP requests)
     * Level.ALL: debugging information (headers, received packages and so on)
     */
    logger.setLevel(Level.CONFIG);

    // Create a Client object
    Client theClient = new Client();

    // get server RTSP port and IP address from the command line
    // ------------------
    int RTSP_server_port = Integer.parseInt(argv[1]);
    String ServerHost = argv[0];
    InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);
    rtspPort = RTSP_server_port;
    rtspServer = ServerHost;
    rtspUrl = "rtsp://" + ServerHost + ":" + RTSP_server_port + "/";

    // get video filename to request:
    VideoFileName = argv[2];
    theClient.textField.setText(VideoFileName);

    // Establish a TCP connection with the server to exchange RTSP messages
    // ------------------
    theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

    // Set input and output stream filters:
    RTSPBufferedReader =
        new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
    RTSPBufferedWriter =
        new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

    // init RTSP state:
    state = INIT;
    // init RTSP sequence number
    RTSPSeqNb = 1;
  }

  // TASK Complete all button handlers
  /** Handler for the Setup button */
  class setupButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

      logger.log(Level.INFO, "Setup Button pressed !");

      if (state == INIT) {
        if (framerate == 0) { // no information on media available
          send_RTSP_request("DESCRIBE");
          if (parse_server_response() != 200) { // block and wait for response
            logger.log(Level.WARNING, "Invalid Server Response");
          }
        }

        // Init non-blocking RTPsocket that will be used to receive data
        try {
          // TASK construct a new DatagramSocket to receive server RTP packets on port RTP_RCV_PORT
          RTPsocket = new DatagramSocket();



          // for now FEC packets are received via RTP-Port, so keep comment below
          // FECsocket = new DatagramSocket(FEC_RCV_PORT);

          // TASK set Timeout value of the socket to 1 ms
          int timeout=1;
          RTPsocket.setSoTimeout(timeout);
          logger.log(Level.FINE, "Socket receive buffer: " + RTPsocket.getReceiveBufferSize());

          rtpHandler.setFecDecryptionEnabled(checkBoxFec.isSelected());
          // Init the play timer
          int timerDelay = FRAME_RATE; // use default delay
          if (framerate != 0) { // if information available, use that
            timerDelay = 1000/framerate; // delay in ms
          }
          timerPlay = new Timer(timerDelay, new timerPlayListener());
          timerPlay.setCoalesce(true); // combines events

          // timerPlay.setInitialDelay(0);

        } catch (SocketException se) {
          logger.log(Level.SEVERE, "Socket exception: " + se);
          System.exit(0);
        }

        VideoFileName = textField.getText();

        // Send SETUP message to the server
        send_RTSP_request("SETUP");

        // Wait for the response
        logger.log(Level.INFO, "Wait for response...");
        if (parse_server_response() != 200) {
          logger.log(Level.WARNING, "Invalid Server Response");
        } else {
            send_RTSP_request("SETUP");

          // TASK change RTSP state and print new state to console and statusLabel
          // state = ....
          state = 1;
          // statusLabel
          statusLabel.setText("READY");
          logger.log(Level.INFO,"New RTSP state: READY\n");
          // logger.log(Level.INFO, "New RTSP state: \n");
        }
      }  else if(state != INIT){

      }
    }
  }

  /** Handler for Play button */
  class playButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

      logger.log(Level.INFO, "Play Button pressed !");
      if (state == READY) {
        // TASK increase RTSP sequence number
        RTSPSeqNb++;

        // Send PLAY message to the server
        send_RTSP_request("PLAY");

        // Wait for the response
        if (parse_server_response() != 200) {
          logger.log(Level.WARNING, "Invalid Server Response");
        }
        else {

          //TASK change RTSP state and print out new state to console an statusLabel
          // state = ....
          state = 2;
          // statusLabel
          statusLabel.setText("PLAY");
          logger.log(Level.INFO,"New RTSP state: PLAY\n");

          // start the timer
          timer.start();
          timerPlay.start();
        }
      } // else if state != READY then do nothing
    }
  }

  /** Handler for Pause button */
  class pauseButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

      logger.log(Level.INFO, "Pause Button pressed !");
      if (state == PLAYING) {
        // TASK increase RTSP sequence number
        RTSPSeqNb++;

        // Send PAUSE message to the server
        send_RTSP_request("PAUSE");

        // Wait for the response
        if (parse_server_response() != 200) {
          logger.log(Level.WARNING, "Invalid Server Response");
        }
        else {
          // TASK change RTSP state and print out new state to console and statusLabel
          // state = ....
          state = 1;
          // statusLabel
          statusLabel.setText("READY");
          logger.log(Level.INFO,"New RTSP state: READY\n");

          // stop the timer
          timer.stop();
          timerPlay.stop();
          timerPlay.setInitialDelay(0);
        }
      }
      // else if state != PLAYING then do nothing
    }
  }

  /** Handler for Teardown button */
  class tearButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

      logger.log(Level.INFO, "Teardown Button pressed !");
      // TASK increase RTSP sequence number
      RTSPSeqNb++;
      // Send TEARDOWN message to the server
      send_RTSP_request("TEARDOWN");

      // Wait for the response
      if (parse_server_response() != 200) {
        logger.log(Level.WARNING, "Invalid Server Response");
      }
      else {
        state = 0;
        // TASK change RTSP state and print out new state to console and statusLabel
        // state = ....
        state = 0;
        statusLabel.setText("INIT");
        logger.log(Level.INFO,"New RTSP State: INIT\n");

        // stop the timer
        timer.stop();
        timerPlay.stop();

        RTPsocket.close();
        // exit
        // System.exit(0);
      }
    }
  }

  /** Handler for Options button */
  class optionsButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

      logger.log(Level.INFO, "Options Button pressed !");
      RTSPSeqNb++;
      send_RTSP_request("OPTIONS");

      if (parse_server_response() != 200) {
        logger.log(Level.WARNING, "Invalid Server Response");
      }
    }
  }

  /** Handler for Describe button */
  class describeButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

      logger.log(Level.INFO, "Describe Button pressed !");
      RTSPSeqNb++;
      send_RTSP_request("DESCRIBE");

      if (parse_server_response() != 200) {
        logger.log(Level.WARNING, "Invalid Server Response");
      }
    }
  }

  /** Handler for the timer event fetches the RTP-packets and displays the images */
  class timerListener implements ActionListener {
    byte[] buf = new byte[MAX_FRAME_SIZE]; // allocate memory to receive UDP data from server

    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
      DatagramPacket rcvDp = new DatagramPacket(buf, buf.length); // RTP needs UDP socket
      try {
        RTPsocket.receive(rcvDp); // receive the DP from the socket:

        rtpHandler.processRtpPacket(rcvDp.getData(), rcvDp.getLength());
      } catch (InterruptedIOException iioe) {
        // System.out.println("Nothing to read");
      } catch (IOException ioe) {
        logger.log(Level.SEVERE, "Exception caught: " + ioe);
      }
    }
  }

  /** Displays one frame if available */
  class timerPlayListener implements ActionListener {
    boolean videoStart = false;

    public void actionPerformed(ActionEvent e) {
      Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
      ReceptionStatistic rs = rtpHandler.getReceptionStatistic();
      byte[] payload;

      // check buffer size and start if filled
      int puffer = rs.latestSequenceNumber - rs.playbackIndex;
      progressBuffer.setValue(puffer);
      progressPosition.setValue(rs.playbackIndex);
      if (iteration % 5 == 0) {
        setStatistics(rs);
        iteration = 0;
      }
      iteration++;

      // check for beginning of display JPEGs
      if ((puffer < jitterBufferSize) && !videoStart) {
        return;
      } else videoStart = true;
      // check for end of display JPEGs
      if (puffer <= 0) { // buffer empty -> finish
        statusLabel.setText("End of Stream");
        return;
      }

      logger.log(Level.FINE, "----------------- Play timer --------------------");
      payload = rtpHandler.nextPlaybackImage();
      if (payload == null) {
          return;
      }

      try {
        // get an Image object from the payload bitstream
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(payload, 0, payload.length);

        // display the image as an ImageIcon object
        icon = new ImageIcon(image);
        iconLabel.setIcon(icon);

      } catch (RuntimeException rex) {
        rex.printStackTrace();
      }
    }

      //TASK complete the statistics
    private void setStatistics(ReceptionStatistic rs) {
      DecimalFormat df = new DecimalFormat("###.###");
      pufferLabel.setText(
          "Puffer: "
              + ""  //
              + " aktuelle Nr. / Summe empf.: "
              + " / "
              + "");
      statsLabel.setText(
          "<html>Abspielzähler / verlorene Medienpakete // Bilder / verloren: "
              + ""
              + " / "
              + ""
              + "<p/>"
              + "</html>");
      fecLabel.setText(
          "FEC: korrigiert / nicht korrigiert: "
              + ""
              + " / "
              + ""
              + "  Ratio: "
              + "");
    }
  }

  /**
   * Parse Server Response Handles Session, Public, Content-Base - attribute
   *
   * @return the reply code
   */
  private int parse_server_response() {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    int reply_code = 0;
    int cl = 0;

    // logger.log(Level.INFO, "Waiting for Server response...");
    try {
      // parse the whole reply
      ArrayList<String> respLines = new ArrayList<>();

      String line;
      do {
        line = RTSPBufferedReader.readLine();
        logger.log(Level.CONFIG, line);
        if (!line.equals("")) respLines.add(line);
      } while (!line.equals(""));
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
            if (state == INIT) {
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
      if (cl > 0) parse_server_data(cl);

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }
    return (reply_code);
  }

  private void parse_server_data(int cl) throws Exception {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    char[] cbuf = new char[cl];
    logger.log(Level.INFO, "*** Parsing Response Data...");
    int data = RTSPBufferedReader.read(cbuf, 0, cl);
    logger.log(Level.INFO, "Data: " + data);
    logger.log(Level.INFO, new String(cbuf));

    String sbuf[] = new String(cbuf).split(CRLF);
    for (int i = 0; i < sbuf.length; i++) {
      if (sbuf[i].contains("framerate")) {
        String sfr = sbuf[i].split(":")[1];
        framerate = Integer.parseInt(sfr);
        logger.log(Level.INFO, "framerate: " + framerate);
      } else if (sbuf[i].contains("range:npt")) {
        String sdur[] = sbuf[i].split("-");
        if (sdur.length > 1) {
          duration = Double.parseDouble(sdur[1]);
          logger.log(Level.INFO, "duration [s]: " + duration);
          progressPosition.setMaximum((int)duration * framerate);
        } // else: no duration available
      } // else: other attributes are not recognized here
    }

    logger.log(Level.INFO, "Finished Content Reading...");
  }

  /**
   * Send the RTSP Request
   *
   * @param request_type the RTSP-Request, e.g. SETUP or PLAY
   */
  private void send_RTSP_request(String request_type) {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    try {
      // defines the URL
      String rtsp = rtspUrl + VideoFileName;
      if (request_type.equals("SETUP")) rtsp = rtsp + "/trackID=0";

      String rtspReq = "";
      //TASK Complete the RTSP request method line
      // rtspReq = ....

      // TASK write the CSeq line:
      // rtspReq += ....

      // check if request_type is equal to "SETUP" and in this case write the Transport: line
      // advertising to the server the port used to receive the RTP packets RTP_RCV_PORT
      // otherwise, write the Session line from the RTSPid field
      if (request_type.equals("SETUP")) {
        //TASK Complete the Transport Attribute
        rtspReq += "Transport:";
      }

      // SessionIS if available
      if (!RTSPid.equals("0")) {
        rtspReq += "Session: " + RTSPid + CRLF;
      }

      logger.log(Level.CONFIG, rtspReq); // console debug
      // Use the RTSPBufferedWriter to write to the RTSP socket
      RTSPBufferedWriter.write(rtspReq + CRLF);
      RTSPBufferedWriter.flush();
      logger.log(Level.INFO, "*** RTSP-Request " + request_type + " send ***");

    } catch (Exception ex) {
      ex.printStackTrace();
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      System.exit(0);
    }
  }

  private JPanel initEncryptionPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridLayout(1, 0));

    JLabel encryptionLabel = new JLabel("Verschlüsselung:");
    panel.add(encryptionLabel);

    encryptionButtons = new ButtonGroup();
    JRadioButton e_none = new JRadioButton("keine");
    e_none.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_none);
    e_none.setSelected(true);
    panel.add(e_none);

    JRadioButton e_srtp = new JRadioButton("SRTP");
    e_srtp.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_srtp);
    panel.add(e_srtp);

    JRadioButton e_jpeg = new JRadioButton("JPEG");
    e_jpeg.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_jpeg);
    panel.add(e_jpeg);

    JRadioButton a_jpeg = new JRadioButton("JPEG (Angriff)");
    a_jpeg.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(a_jpeg);
    panel.add(a_jpeg);

    return panel;
  }

  private void radioButtonSelected(ItemEvent ev) {
    JRadioButton rb = (JRadioButton)ev.getItem();
    if (rb.isSelected()) {
      String label = rb.getText();
      RtpHandler.EncryptionMode mode = RtpHandler.EncryptionMode.NONE;

      switch (label) {
      case "SRTP":
        mode = RtpHandler.EncryptionMode.SRTP;
        break;
      case "JPEG":
        mode = RtpHandler.EncryptionMode.JPEG;
        break;
      case "JPEG (Angriff)":
        mode = RtpHandler.EncryptionMode.JPEG_ATTACK;
        break;
      default:
        break;
      }

      boolean encryptionSet = rtpHandler.setEncryption(mode);
      if (!encryptionSet) {
        Enumeration<AbstractButton> buttons = encryptionButtons.getElements();
        while (buttons.hasMoreElements()) {
          AbstractButton ab = buttons.nextElement();
          if (ab.getText().equals("keine")) {
            ab.setSelected(true);
          }
        }
      }
    }
  }
}
