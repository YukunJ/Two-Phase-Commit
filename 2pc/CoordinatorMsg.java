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

import java.io.*;
import java.util.Arrays;

public class CoordinatorMsg implements Serializable {
  public final int txn_id;
  public final TxnPhase phase;
  public final String filename;
  public final byte[] img;
  public final String[] resource_requested;
  public final TxnDecision decision;

  public CoordinatorMsg(int txn_id, TxnPhase phase, String filename, byte[] img,
      String[] resource_requested, TxnDecision decision) {
    this.txn_id = txn_id;
    this.phase = phase;
    this.filename = filename;
    this.img = img;
    this.resource_requested = resource_requested;
    this.decision = decision;
  }

  public static CoordinatorMsg GeneratePhaseIMsg(
      int txn_id, String filename, byte[] img, String[] resource_requested) {
    return new CoordinatorMsg(
        txn_id, TxnPhase.PHASE_I, filename, img, resource_requested, TxnDecision.UNDECIDED);
  }

  public static CoordinatorMsg GeneratePhaseIIMsg(int txn_id, TxnDecision decision) {
    return new CoordinatorMsg(txn_id, TxnPhase.PHASE_II, null, null, null, decision);
  }

  public void sendMyselfTo(ProjectLib PL, String destination) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(this);
      oos.flush();
      byte[] payload = bos.toByteArray();
      ProjectLib.Message msg = new ProjectLib.Message(destination, payload);
      PL.sendMessage(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static CoordinatorMsg deserialize(ProjectLib.Message msg) {
    CoordinatorMsg coordinatorMsg = null;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(msg.body);
         ObjectInputStream ois = new ObjectInputStream(bis)) {
      coordinatorMsg = (CoordinatorMsg) ois.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return coordinatorMsg;
  }

  public String toString() {
    if (phase == TxnPhase.PHASE_I) {
      return "txn: " + txn_id + " phase:" + phase.toString() + " filename:" + filename
          + " resource_request:" + Arrays.toString(resource_requested);
    } else {
      return "txn: " + txn_id + " phase:" + phase.toString() + " decision:" + decision;
    }
  }
}
