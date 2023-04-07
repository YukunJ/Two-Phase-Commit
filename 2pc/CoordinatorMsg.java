/**
 * CoordinatorMsg.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the implementation for the CoordinatorMsg instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The message from Coordinator send to Participant
 * corresponds to either phase1's proposal or phase2' decision
 */

import java.io.Serializable;

public class CoordinatorMsg implements Serializable {
  public final int txn_id;
  public final TxnPhase phase;
  public final String filename;
  public final String[] resource_requested;
  public final TxnDecision decision;

  public CoordinatorMsg(int txn_id, TxnPhase phase, String filename, String[] resource_requested,
      TxnDecision decision) {
    this.txn_id = txn_id;
    this.phase = phase;
    this.filename = filename;
    this.resource_requested = resource_requested;
    this.decision = decision;
  }

  public static CoordinatorMsg GeneratePhaseIMsg(
      int txn_id, String filename, String[] resource_requested) {
    return new CoordinatorMsg(
        txn_id, TxnPhase.PHASE_I, filename, resource_requested, TxnDecision.UNDECIDED);
  }

  public static CoordinatorMsg GeneratePhaseIIMsg(int txn_id, TxnDecision decision) {
    return new CoordinatorMsg(txn_id, TxnPhase.PHASE_II, null, null, decision);
  }
}
