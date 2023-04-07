/**
 * TxnSlaveRecord.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the implementation for the TxnSlaveRecord instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The Transaction Record for a single transaction
 * it records the current status of the txn and votes
 * used by the UserNode as a participant
 */

import java.io.Serializable;

public class TxnSlaveRecord implements Serializable {
  public final int txn_id;
  public final String filename;
  public final String[] resources_requested;
  public final TxnVote vote;
  public TxnDecision decision;

  public TxnSlaveRecord(int txn_id, String filename, String[] resources_requested, TxnVote vote) {
    this.txn_id = txn_id;
    this.filename = filename;
    this.resources_requested = resources_requested;
    this.vote = vote;
    this.decision = TxnDecision.UNDECIDED;
  }
}
