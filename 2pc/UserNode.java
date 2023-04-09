/**
 * UserNode.java
 * author: Yukun Jiang
 * Date: April 05, 2023
 *
 * This is the implementation for the UserNode instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The UserNode acts as the participant for the system
 * and responds to transaction requests from the Server coordinator
 */

import java.io.*;
import java.util.HashSet;
import java.util.Map;

public class UserNode implements ProjectLib.MessageHandling {
  public final String myId;

  private static final String SERVER = "Server";

  private static final String LOG_NAME = "LOG_PARTICIPANT";
  public static ProjectLib PL;

  public TxnSlaveLog log;

  private boolean finish_recovery = false;

  /*
    Persistent logging
   */
  private synchronized void flushLog() {
    try (FileOutputStream f = new FileOutputStream(LOG_NAME, false);
         BufferedOutputStream b = new BufferedOutputStream(f);
         ObjectOutputStream o = new ObjectOutputStream(b)) {
      o.writeObject(log);
      o.flush();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      PL.fsync();
    }
  }

  /*
    Upon recovery
   */
  private synchronized TxnSlaveLog loadLog() {
    TxnSlaveLog disk_log = null;
    try (FileInputStream f = new FileInputStream(LOG_NAME);
         BufferedInputStream b = new BufferedInputStream(f);
         ObjectInputStream o = new ObjectInputStream(b)) {
      disk_log = (TxnSlaveLog) o.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return disk_log;
  }

  public void recover() {
    if (new File(LOG_NAME).exists()) {
      System.out.println("Node " + myId + " comes online with DISK log");
      log = loadLog();
    } else {
      System.out.println("Node " + myId + " comes online with FRESH log");
      log = new TxnSlaveLog();
    }
    finish_recovery = true;
  }

  public UserNode(String id) {
    myId = id;
  }

  private void dealProposal(CoordinatorMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_I);
    TxnSlaveRecord old_record = log.retrieveRecord(msg.txn_id);
    if (old_record != null) {
      // already make up decision, just reply again
      assert (old_record.vote != TxnVote.NOT_VOTE);
      ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIMsg(msg.txn_id, old_record.vote);
      participantMsg.sendMyselfTo(PL, SERVER);
      return;
    }

    boolean vote = PL.askUser(msg.img, msg.resource_requested);
    if (vote) {
      // check if file really exists and not locked
      for (String f : msg.resource_requested) {
        boolean not_exist = !new File(f).exists();
        boolean locked = log.locked_resources.containsKey(f);
        if (not_exist || locked) {
          vote = false;
          break;
        }
      }
      // all valid, vote passed, lock resources
      if (vote) {
        for (String f : msg.resource_requested) {
          log.locked_resources.put(f, msg.txn_id);
        }
      }
    }
    TxnVote txn_vote = (vote) ? TxnVote.APPROVAL : TxnVote.DENIAL;
    // decision is made and on book now
    log.createRecord(msg.txn_id, msg.filename, msg.resource_requested, txn_vote);
    flushLog(); // LOG FLUSH

    ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIMsg(msg.txn_id, txn_vote);
    participantMsg.sendMyselfTo(PL, SERVER);
  }

  private void dealDecision(CoordinatorMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_II);
    assert (msg.decision != TxnDecision.UNDECIDED);
    TxnSlaveRecord record = log.retrieveRecord(msg.txn_id);
    if (record == null) {
      assert (msg.decision == TxnDecision.ABORT);
      // I must have timed out before and Server implicitly think I am giving Denial back
      TxnSlaveRecord r =
          log.createRecord(msg.txn_id, msg.filename, msg.resource_requested, TxnVote.DENIAL);
      r.decision = msg.decision;
      flushLog();

      // ACK BACK
      ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIIMsg(msg.txn_id);
      participantMsg.sendMyselfTo(PL, SERVER);
      return;
    }

    if (record.decision == msg.decision) {
      // I have already received this decision message, just ACK back again
      ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIIMsg(msg.txn_id);
      participantMsg.sendMyselfTo(PL, SERVER);
      return;
    }

    record.decision = msg.decision;
    HashSet<String> locked_resources = new HashSet<>();
    for (Map.Entry<String, Integer> entry : log.locked_resources.entrySet()) {
      if (entry.getValue() == msg.txn_id) {
        locked_resources.add(entry.getKey());
      }
    }

    if (record.decision == TxnDecision.COMMIT) {
      // delete committed resources if any
      for (String f : locked_resources) {
        boolean success = new File(f).delete();
        System.out.println(myId + " tries to delete local image " + f + " result is " + success);
      }
    }

    // release locked resources if any
    for (String f : locked_resources) {
      log.locked_resources.remove(f);
    }

    flushLog(); // FLUSH LOG

    // ACK back
    ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIIMsg(msg.txn_id);
    participantMsg.sendMyselfTo(PL, SERVER);
  }

  @Override
  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
    while (!this.finish_recovery) {
    }; // guard recovery
    CoordinatorMsg coordinatorMsg = CoordinatorMsg.deserialize(msg);
    System.out.println(
        myId + ": Got message from " + msg.addr + " about Msg: " + coordinatorMsg.toString());
    /* Proposal */
    if (coordinatorMsg.phase == TxnPhase.PHASE_I) {
      dealProposal(coordinatorMsg);
    }

    if (coordinatorMsg.phase == TxnPhase.PHASE_II) {
      dealDecision(coordinatorMsg);
    }
    return true;
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 2)
      throw new Exception("Need 2 args: <port> <id>");
    UserNode UN = new UserNode(args[1]);
    PL = new ProjectLib(Integer.parseInt(args[0]), args[1], UN);
    UN.recover();

    while (true) {
    }
  }
}
