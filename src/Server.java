/* ------------------
Server
usage: java Server [RTSP listening port]
---------------------- */

import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Server extends JFrame implements ActionListener, ChangeListener {

  // RTP variables:
  // ----------------
  DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
  DatagramPacket senddp; // UDP packet containing the video frames
  InetAddress ClientIPAddr; // Client IP address
  final static int startGroupSize = 2;
  RtpHandler rtpHandler;
  Rtsp rtsp = null;
  // Channel errors
  private double lossRate = 0.0;
  Random random = new Random(123456); // fixed seed for debugging
  int dropCounter; // Nr. of dropped media packets

  // GUI:
  // ----------------
  JLabel label;
  static JLabel stateLabel;
  private ButtonGroup encryptionButtons = null;

  // Video variables:
  // ----------------
  static int imagenb = 0; // image nb of the image currently transmitted
  VideoReader video; // VideoStream object used to access video frames
  static int DEFAULT_FRAME_PERIOD = 40; // Frame period of the video to stream, in ms
  public VideoMetadata videoMeta = null;

  Timer timer; // timer used to send the images at the video frame rate
  // byte[] buf; // buffer used to store the images to send to the client

  // RTSP variables
  // ----------------
  // rtsp states
  static final int INIT = 0;
  static final int READY = 1;
  static final int PLAYING = 2;
  // rtsp message types
  static final int SETUP = 3;
  static final int PLAY = 4;
  static final int PAUSE = 5;
  static final int TEARDOWN = 6;
  static final int OPTIONS = 7;
  static final int DESCRIBE = 8;

  static int state; // RTSP Server state == INIT or READY or PLAY
  static Socket RTSPsocket; // socket used to send/receive RTSP messages
  // input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;

  static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  public Server() {
    super("Server"); // init Frame
    //Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    // init RTP socket and FEC
    rtpHandler = new RtpHandler(startGroupSize);

    // Handler to close the main window
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            // stop the timer and exit
            timer.stop();
            System.exit(0);
          }
        });

    // GUI:
    label = new JLabel("Send frame #        ", JLabel.CENTER);
    stateLabel = new JLabel("State:         ",JLabel.CENTER);
    getContentPane().add(label, BorderLayout.NORTH);
    getContentPane().add(stateLabel, BorderLayout.SOUTH);
    // Error Slider
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    JSlider dropRate = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
    dropRate.addChangeListener(this);
    dropRate.setMajorTickSpacing(10);
    dropRate.setMinorTickSpacing(5);
    dropRate.setPaintTicks(true);
    dropRate.setPaintLabels(true);
    dropRate.setName("p");
    JSlider groupSize = new JSlider(JSlider.HORIZONTAL, 2, 48, startGroupSize);
    groupSize.addChangeListener(this::stateChanged);
    groupSize.setMajorTickSpacing(4);
    groupSize.setMinorTickSpacing(1);
    groupSize.setPaintLabels(true);
    groupSize.setPaintTicks(true);
    groupSize.setName("k");
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 4;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    mainPanel.add(groupSize, gbc);
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 4;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    mainPanel.add(dropRate, gbc);

    initGuiEncryption(mainPanel);

    getContentPane().add(mainPanel, BorderLayout.CENTER);

    try {
      RTPsocket = new DatagramSocket();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Exception caught: " + e);
    }
  }

  /**
   * Handler for Channel error Slider
   *
   * @param e Change Event
   */
  public void stateChanged(ChangeEvent e) {
    //Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    JSlider source = (JSlider) e.getSource();
    if (!source.getValueIsAdjusting()) {
      if (source.getName().equals("k")) {
        int k = source.getValue();
        rtpHandler.setFecGroupSize(k);
        logger.log(Level.INFO, "New Group size: " + k);
      } else {
        lossRate = source.getValue();
        lossRate = lossRate / 100;
        logger.log(Level.INFO, "New packet error rate: " + lossRate);
      }
    }
  }

  /**
   * Handler for encryption RadioButtons.
   * <p>
   * The ItemEvent is just fired if a Button is selected
   * which previous was not.
   *
   * @param ev ItemEvent
   */
  public void radioButtonSelected(ItemEvent ev) {
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

  // ------------------------------------
  // main
  // ------------------------------------
  public static void main(String[] argv) throws Exception {
    //Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    CustomLoggingHandler.prepareLogger(logger);
    /* set logging level
     * Level.CONFIG: default information (incl. RTSP requests)
     * Level.ALL: debugging information (headers, received packages and so on)
     */
    logger.setLevel(Level.CONFIG);
    //logger.setLevel(Level.ALL);

    // create a Server object
    Server theServer = new Server();
    theServer.setSize(500, 200);
    theServer.setVisible(true);

    // get RTSP socket port from the command line
    int RTSPport = Integer.parseInt(argv[0]);

    // Initiate TCP connection with the client for the RTSP session
    ServerSocket listenSocket = new ServerSocket(RTSPport);
    RTSPsocket = listenSocket.accept();
    listenSocket.close();

    // Get Client IP address
    theServer.ClientIPAddr = RTSPsocket.getInetAddress();

        // Initiate RTSPstate
    state = INIT;
    stateLabel.setText("INIT");

    // Set input and output stream filters:
    RTSPBufferedReader =
        new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
    RTSPBufferedWriter =
        new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));

    theServer.rtsp = new Rtsp(RTSPBufferedReader, RTSPBufferedWriter);

    int request_type;

    // loop to handle RTSP requests
    while (true) {
      // parse the request
      request_type = theServer.rtsp.parse_RTSP_request(); // blocking

      switch (request_type) {
        case SETUP:
          state = READY;
          stateLabel.setText("READY");
          logger.log(Level.INFO, "New RTSP state: READY");

          if (theServer.videoMeta == null) {
            theServer.videoMeta = Server.getVideoMetadata(theServer.rtsp.getVideoFileName() );
          }

          // init Timer
          theServer.timer = new Timer(1000 / theServer.videoMeta.getFramerate(), theServer);
          theServer.timer.setInitialDelay(0);
          theServer.timer.setCoalesce(false); // Coalesce can lead to buffer underflow in client

          theServer.rtsp.send_RTSP_response(SETUP, RTSPsocket.getLocalPort() );
          // init the VideoStream object:
          theServer.video = new VideoReader( theServer.rtsp.getVideoFileName() );
          imagenb = 0;
          break;

        case PLAY:
          if (state == READY) {
            theServer.rtsp.send_RTSP_response(PLAY);
            theServer.timer.start();
            state = PLAYING;
            stateLabel.setText("PLAY");
            logger.log(Level.INFO, "New RTSP state: PLAYING");
          }
          break;

        case PAUSE:
          if (state == PLAYING) {
            theServer.rtsp.send_RTSP_response(PAUSE);
            theServer.timer.stop();  // stop timer
            state = READY;
            stateLabel.setText("READY");
            logger.log(Level.INFO, "New RTSP state: READY");
          }
          break;

        case TEARDOWN:
          state = INIT;
          stateLabel.setText("INIT");
          theServer.rtsp.send_RTSP_response(TEARDOWN);
          theServer.timer.stop();
          theServer.videoMeta = null;
          break;

        case OPTIONS:
          logger.log(Level.INFO, "Options request");
          theServer.rtsp.send_RTSP_response(OPTIONS);
          break;

        case DESCRIBE:
          logger.log(Level.INFO, "DESCRIBE Request");
          theServer.rtsp.send_RTSP_response(DESCRIBE);
          break;

        default:
          logger.log(Level.WARNING, "Wrong request");
      }
    }
  }

  /**
   * Hander for timer
   *
   * @param e ActionEvent
   */
  public void actionPerformed(ActionEvent e) {
    //Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    imagenb++; // image counter
    byte[] packet_bits;

    try {
      byte[] frame = video.readNextImage(); // get next frame
      if (frame != null) {
        logger.log(Level.FINE, "Frame size: " + frame.length);
        packet_bits = rtpHandler.jpegToRtpPacket(frame, videoMeta.getFramerate());

        // send the packet as a DatagramPacket over the UDP socket
        senddp = new DatagramPacket(packet_bits, packet_bits.length, ClientIPAddr, rtsp.getRTP_dest_port() );

        sendPacketWithError(senddp, false); // Send with packet loss

        if (rtpHandler.isFecPacketAvailable()) {
          logger.log(Level.FINE, "FEC-Encoder ready...");
          byte[] fecPacket = rtpHandler.createFecPacket();
          // send to the FEC dest_port
          senddp = new DatagramPacket(fecPacket, fecPacket.length, ClientIPAddr, rtsp.getFEC_dest_port() );
          sendPacketWithError(senddp, true);
        }

        // update GUI
        label.setText("Send frame #" + imagenb);
      } else timer.stop();
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Exception caught: " + ex);
      ex.printStackTrace();
      System.exit(0);
    }
  }

  /**
   * @param senddp Datagram to send
   * @throws Exception Throws all
   */
  private void sendPacketWithError(DatagramPacket senddp, boolean fec) throws Exception {
    //Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    String label;
    if (fec) label = " fec ";
    else label = " media ";
    if (random.nextDouble() > lossRate) {
      logger.log(Level.FINE, "Send frame: " + imagenb + label);
      RTPsocket.send(senddp);
    } else {
      System.err.println("Dropped frame: " + imagenb + label);
      if (!fec) dropCounter++;
    }
    // System.out.println("Drop count media packets: " +  dropCounter);
  }



  private void initGuiEncryption(JPanel panel) {
    GridBagConstraints gbc = new GridBagConstraints();
    JLabel encryptionLabel = new JLabel("Verschlüsselung:");
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 10, 0, 0);
    panel.add(encryptionLabel, gbc);

    encryptionButtons = new ButtonGroup();
    JRadioButton e_none = new JRadioButton("keine");
    e_none.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_none);
    e_none.setSelected(true);
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(e_none, gbc);

    JRadioButton e_srtp = new JRadioButton("SRTP");
    e_srtp.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_srtp);
    gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(e_srtp, gbc);

    JRadioButton e_jpeg = new JRadioButton("JPEG");
    e_jpeg.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_jpeg);
    gbc = new GridBagConstraints();
    gbc.gridx = 3;
    gbc.gridy = 2;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(e_jpeg, gbc);
  }

  /** Get the metadata from a video file.
   *
   *  If no metadata is available, all fields are zero-initialized with
   *  exception of the framerate. Because the framerate is strongly required,
   *  it is set to a default value.
   *
   *  @param filename Name of the video file
   *  @return metadata structure containing the extracted information
   */
   static VideoMetadata getVideoMetadata(String filename) {
    //Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    VideoMetadata meta;

    String[] splittedFilename = filename.split("\\.");
    switch (splittedFilename[splittedFilename.length-1]) {
      case "avi":
        meta = AviMetadataParser.parse(filename);
        break;
      case "mov":
        meta = QuickTimeMetadataParser.parse(filename);
        break;
      default:
        logger.log(Level.WARNING, "File extension not recognized: " + filename);
      case "mjpg":
      case "mjpeg":
        meta = new VideoMetadata(1000 / DEFAULT_FRAME_PERIOD);
        break;
    }

    assert meta != null : "VideoMetadata of file " + filename + " was not initialized correctly";
    return meta;
  }
}
