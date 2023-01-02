import java.util.logging.Level;
import java.util.logging.Logger;

public class RTPpacket {

  /*
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
   */


  // size of the RTP header:
  static int HEADER_SIZE = 12;

  // Fields that compose the RTP header
  public int Version;
  public int Padding;
  public int Extension;
  public int CC;
  public int Marker;
  public int PayloadType;
  public int SequenceNumber;
  public int TimeStamp;
  public int Ssrc;

  // Bitstream of the RTP header
  public byte[] header;
  // size of the RTP payload
  public int payload_size;
  // Bitstream of the RTP payload
  public byte[] payload;

  // --------------------------
  // Constructor of an RTPpacket object from header fields and payload bitstream
  // --------------------------
  public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length) {
    // fill by default header fields:
    Version = 2;
    Padding = 0;
    Extension = 0;
    CC = 0;
    Marker = 1;
    Ssrc = 0;

    // fill changing header fields:
    SequenceNumber = Framenb;
    TimeStamp = Time;
    PayloadType = PType;

    // build the header bistream:
    // --------------------------
    header = new byte[HEADER_SIZE];
    setRtpHeader();

    // fill the payload bitstream:
    // --------------------------
    payload_size = data_length;
    payload = new byte[data_length];

    // fill payload array of byte from data (given in parameter of the constructor)
    System.arraycopy(data, 0, payload, 0, data_length);

    // ! Do not forget to uncomment method printheader() below, if desired !
  }


  public void setRtpHeader() {
    //TASK fill the header array of byte with RTP header fields

    header[0] = (byte)(Version << 6 | Padding << 5 | Extension << 4 | CC);
    header[1] = (byte)(Marker << 7 | PayloadType & 0x000000FF);
    header[2] = (byte)(SequenceNumber >> 8);
    header[3] = (byte)(SequenceNumber & 0xFF);
    header[4] = (byte)(TimeStamp >> 24);
    header[5] = (byte)(TimeStamp >> 16);
    header[6] = (byte)(TimeStamp >> 8);
    header[7] = (byte)(TimeStamp & 0xFF);
    header[8] = (byte)(Ssrc >> 24);
    header[9] = (byte)(Ssrc >> 16);
    header[10]= (byte)(Ssrc >> 8);
    header[11]= (byte)(Ssrc & 0xFF);


  }


  // --------------------------
  // Constructor of an RTPpacket object from the packet bistream
  // --------------------------
  public RTPpacket(byte[] packet, int packet_size) {
    // fill default fields:
    Version = 2;
    Padding = 0;
    Extension = 0;
    CC = 0;
    Marker = 0;
    Ssrc = 0;

    // check if total packet size is lower than the header size
    if (packet_size >= HEADER_SIZE) {
      // get the header bitsream:
      header = new byte[HEADER_SIZE];
      System.arraycopy(packet, 0, header, 0, HEADER_SIZE);

      // get the payload bitstream:
      payload_size = packet_size - HEADER_SIZE;
      payload = new byte[payload_size];
      System.arraycopy(packet, HEADER_SIZE, payload, 0, packet_size - HEADER_SIZE);

      // interpret the changing fields of the header:
      PayloadType = header[1] & 127;
      SequenceNumber = unsigned_int(header[3]) + 256 * unsigned_int(header[2]);
      TimeStamp =
          unsigned_int(header[7])
              + 256 * unsigned_int(header[6])
              + 65536 * unsigned_int(header[5])
              + 16777216 * unsigned_int(header[4]);
    }
  }

  // --------------------------
  // getpayload: return the payload bistream of the RTPpacket and its size
  // --------------------------
  public int getpayload(byte[] data) {
    System.arraycopy(payload, 0, data, 0, payload_size);
    return (payload_size);
  }

  public byte[] getpayload() {
    byte[] data = new byte[payload_size];
    System.arraycopy(payload, 0, data, 0, payload_size);
    return data;
  }


  // --------------------------
  // getpayload_length: return the length of the payload
  // --------------------------
  public int getpayload_length() {
    return (payload_size);
  }

  // --------------------------
  // getlength: return the total length of the RTP packet
  // --------------------------
  public int getlength() {
    return (payload_size + HEADER_SIZE);
  }

  // --------------------------
  // getpacket: returns the packet bitstream and its length
  // --------------------------
  public int getpacket(byte[] packet) {
    // construct the packet = header + payload
    System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
    System.arraycopy(payload, 0, packet, HEADER_SIZE, payload_size);

    // return total size of the packet
    return (payload_size + HEADER_SIZE);
  }

  public byte[] getpacket() {
    byte[] packet = new byte[payload_size + HEADER_SIZE];
    // construct the packet = header + payload
    System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
    System.arraycopy(payload, 0, packet, HEADER_SIZE, payload_size);

    // return packet
    return packet;
  }




  // --------------------------
  // gettimestamp
  // --------------------------

  public int gettimestamp() {
    return (TimeStamp);
  }

  // --------------------------
  // getsequencenumber
  // --------------------------
  public int getsequencenumber() {
    return (SequenceNumber);
  }

  // --------------------------
  // getpayloadtype
  // --------------------------
  public int getpayloadtype() {
    return (PayloadType);
  }


  /**
   * Print RTP header without SSRC
   */
  public void printheader() {
    printheader(HEADER_SIZE-4, header);
  }

  /**
   * print the payload of a RTP packet
   * @param n Number of bytes to print
   */
  public void printpayload(int n) {
    printheader(n, payload);
  }


  void printheader(int size, byte[] data) {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    String b = "";
    for (int i = 0; i < size; i++) {
      b += String.format("%8s", Integer.toBinaryString(data[i] & 0xFF)).replace(' ', '0');
      b += " ";
    }
    logger.log(Level.FINER, b);
  }


  // return the unsigned value of 8-bit integer nb
  static int unsigned_int(int nb) {
    if (nb >= 0) {
      return (nb);
    } else {
      return (256 + nb);
    }
  }
}
