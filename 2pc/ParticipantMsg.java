/**
 * ParticipantMsg.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the implementation for the ParticipantMsg instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The message from Participant send to Coordinator
 * corresponds to either phase1's vote or phase2' ACK
 */

import java.io.Serializable;

public class ParticipantMsg implements Serializable {
  public final int txn_id;
  public final TxnPhase phase;
  public final TxnVote vote;

  public ParticipantMsg(int txn_id, TxnPhase phase, TxnVote vote) {
    this.txn_id = txn_id;
    this.phase = phase;
    this.vote = vote;
  }
}
