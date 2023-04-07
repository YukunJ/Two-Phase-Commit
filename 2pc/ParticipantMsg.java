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

import java.io.*;

public class ParticipantMsg implements Serializable {
  public final int txn_id;
  public final TxnPhase phase;
  public final TxnVote vote;

  public ParticipantMsg(int txn_id, TxnPhase phase, TxnVote vote) {
    this.txn_id = txn_id;
    this.phase = phase;
    this.vote = vote;
  }

  public static ParticipantMsg GeneratePhaseIMsg(int txn_id, TxnVote vote) {
    return new ParticipantMsg(txn_id, TxnPhase.PHASE_I, vote);
  }

  public static ParticipantMsg GeneratePhaseIIMsg(int txn_id) {
    return new ParticipantMsg(txn_id, TxnPhase.PHASE_II, TxnVote.NOT_VOTE);
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

  public static ParticipantMsg deserialize(ProjectLib.Message msg) {
    ParticipantMsg participantMsg = null;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(msg.body);
         ObjectInputStream ois = new ObjectInputStream(bis)) {
      participantMsg = (ParticipantMsg) ois.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return participantMsg;
  }

  public String toString() {
    if (phase == TxnPhase.PHASE_I) {
      return "txn_id: " + txn_id + " phase: " + phase.toString() + " vote:" + vote.toString();
    } else {
      return "txn_id: " + txn_id + " phase II ACK decision";
    }
  }
}
