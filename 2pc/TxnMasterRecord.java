/**
 * TxnMasterRecord.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the implementation for the TxnMasterRecord instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The Transaction Record for a single transaction
 * it records the current status of the txn and remaining participants
 * should be stored in persistent storage in fear of failure
 */

import java.io.Serializable;
import java.util.HashSet;

public class TxnMasterRecord implements Serializable {
  enum Status implements Serializable { PREPARE, DECISION, END }

  /* Record specific memebers */
  public final int id;
  public final String filename;
  public final byte[] img;
  public final String[] sources;
  public final HashSet<String> participants;
  public HashSet<String> outstanding_participants;
  public Status status;
  public TxnDecision decision;

  private static final String SEP = ":";
  private static final int PARTICIPANT_IDX = 0;
  private static final int FILENAME_IDX = 1;

  @SuppressWarnings("unchecked")
  public TxnMasterRecord(int id, String filename, byte[] img, String[] sources) {
    this.id = id;
    this.filename = filename;
    this.img = img;
    this.sources = sources;
    this.participants = new HashSet<>();
    /* parse out all participant */
    for (String node_file : sources) {
      String[] node_and_file = node_file.split(SEP);
      String participant = node_and_file[PARTICIPANT_IDX];
      this.participants.add(participant);
    }
    this.outstanding_participants = (HashSet<String>) this.participants.clone();
    this.status = Status.PREPARE;
    this.decision = TxnDecision.UNDECIDED;
  }
}
