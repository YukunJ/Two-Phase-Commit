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

import java.io.File;
import java.util.HashSet;
import java.util.Map;

public class UserNode implements ProjectLib.MessageHandling {
  public final String myId;

  private static final String SERVER = "Server";
  public static ProjectLib PL;

  public final TxnSlaveLog log;
  public UserNode(String id) {
    // TODO: load log ?
    log = new TxnSlaveLog();
    myId = id;
  }

  private void dealProposal(CoordinatorMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_I);
    // TODO: flush log ? Load old vote ?
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

    ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIMsg(msg.txn_id, txn_vote);
    participantMsg.sendMyselfTo(PL, SERVER);
  }

  private void dealDecision(CoordinatorMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_II);
    // TODO: flush log ?
    TxnSlaveRecord record = log.retrieveRecord(msg.txn_id);
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

    // ACK back
    ParticipantMsg participantMsg = ParticipantMsg.GeneratePhaseIIMsg(msg.txn_id);
    participantMsg.sendMyselfTo(PL, SERVER);
  }

  @Override
  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
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

    //    ProjectLib.Message msg = new ProjectLib.Message("Server", "hello".getBytes());
    //    System.out.println(args[1] + ": Sending message to " + msg.addr);
    //    PL.sendMessage(msg);
  }
}
