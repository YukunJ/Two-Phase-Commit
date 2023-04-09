/**
 * Server.java
 * author: Yukun Jiang
 * Date: April 05, 2023
 *
 * This is the implementation for the Server instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The Server acts as the single only coordinator for the system
 * and initiate, make decisions on various transaction commits
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Server implements ProjectLib.CommitServing, ProjectLib.MessageHandling {
  /**
   * Wrapper class for an outgoing CoordinatorMsg
   * it tracks when this is sent
   * and thus can be tracked if it has timed out and needs to be resent
   */
  static class OutboundMsg {
    public CoordinatorMsg msg;
    public Long sent_time;

    public String dest;

    public OutboundMsg(CoordinatorMsg msg, String dest) {
      this.msg = msg;
      this.dest = dest;
      this.sent_time = System.currentTimeMillis();
    }

    public boolean isExpired() {
      return (System.currentTimeMillis() - this.sent_time) > TIMEOUT;
    }
  }

  /* big data structure for persistent storage */
  private TxnMasterLog log;
  public static ProjectLib PL;
  private static final String SEP = ":";
  private static final int PARTICIPANT_IDX = 0;
  private static final int FILENAME_IDX = 1;

  private static final String MODE = "rws";

  private static final String LOG_NAME = "LOG_COORDINATOR";

  /* how often to check if outbound message has timeout */
  private static final Long INTERVAL = 1000L;
  /* the timeout threshold for a message since one-way latency is at most 3 seconds as specified */
  private static final Long TIMEOUT = 6000L;

  private final ConcurrentLinkedDeque<OutboundMsg> outboundMsgs;

  /* protect the recovery stage upon re-booting */
  private boolean finish_recovery = false;

  private void timeMsg(CoordinatorMsg msg, String dest) {
    outboundMsgs.addLast(new OutboundMsg(msg, dest));
  }

  /*
    Main Loop for checking timeout messages
    if a message is in Phase I prepare and has not received feedback
    Coordinator think it's an implicit Denial and immediately abort

    if a message is in Phase II requiring an ACK from participant
    it must be resent until ACKed
   */
  @SuppressWarnings("unchecked")
  public void inspectTimeout() {
    ArrayList<OutboundMsg> expired_msgs = new ArrayList<>();
    synchronized (outboundMsgs) {
      // prune the timing queue
      for (OutboundMsg msg : outboundMsgs) {
        if (msg.isExpired()) {
          expired_msgs.add(msg);
        }
      }
      outboundMsgs.removeAll(expired_msgs);
    }

    // deal with expired msgs
    for (OutboundMsg msg : expired_msgs) {
      TxnMasterRecord record = log.retrieveRecord(msg.msg.txn_id);
      if (msg.msg.phase == TxnPhase.PHASE_I && record.status == TxnMasterRecord.Status.PREPARE) {
        // deemed as implicit DENIAL
        System.out.println("Server's txn=" + msg.msg.txn_id + " to Node " + msg.dest
            + " in Phase I has expired, deemed as DENIAL");
        record.decision = TxnDecision.ABORT;
        record.status = TxnMasterRecord.Status.DECISION;
        record.outstanding_participants = (HashSet<String>) record.participants.clone();
        flushLog(); // FLUSH LOG
        resumeTxnPhaseII(record);
      }

      if (msg.msg.phase == TxnPhase.PHASE_II && record.status == TxnMasterRecord.Status.DECISION) {
        // must continue resending until ACKed
        System.out.println("Server's txn=" + msg.msg.txn_id + " to Node " + msg.dest
            + " in Phase II has expired, RESEND");
        msg.msg.sendMyselfTo(PL, msg.dest);
        timeMsg(msg.msg, msg.dest);
      }
    }
  }

  /* persistent log */
  private synchronized void flushLog() {
    Long start = System.currentTimeMillis();
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

  /* Upon recovery */
  private synchronized TxnMasterLog loadLog() {
    TxnMasterLog disk_log = null;
    try (FileInputStream f = new FileInputStream(LOG_NAME);
         BufferedInputStream b = new BufferedInputStream(f);
         ObjectInputStream o = new ObjectInputStream(b)) {
      disk_log = (TxnMasterLog) o.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return disk_log;
  }

  /* Continue Phase I of a txn */
  private void resumeTxnPhaseI(TxnMasterRecord record) {
    assert (record.status == TxnMasterRecord.Status.PREPARE);
    ConcurrentHashMap<String, ArrayList<String>> distributed_sources = new ConcurrentHashMap<>();
    /* split the sources into mapping based by per destination UserNode */
    for (String source_file : record.sources) {
      String[] source_and_file = source_file.split(SEP);
      String participant = source_and_file[PARTICIPANT_IDX];
      String file = source_and_file[FILENAME_IDX];
      if (!distributed_sources.containsKey(participant)) {
        distributed_sources.put(participant, new ArrayList<>());
      }
      distributed_sources.get(participant).add(file);
    }
    /* send outstanding messages */
    for (String outstanding_participant : record.outstanding_participants) {
      ArrayList<String> single_sources = distributed_sources.get(outstanding_participant);
      String[] outstanding_sources = single_sources.toArray(new String[0]);
      CoordinatorMsg msg = CoordinatorMsg.GeneratePhaseIMsg(
          record.id, record.filename, record.img, outstanding_sources);
      msg.sendMyselfTo(PL, outstanding_participant);
      timeMsg(msg, outstanding_participant); // Under Timeout monitor
    }
  }

  /* Continue Phase II of a txn */
  private void resumeTxnPhaseII(TxnMasterRecord record) {
    assert (record.decision != TxnDecision.UNDECIDED
        && record.status == TxnMasterRecord.Status.DECISION);

    // trim all the outbound Phase I message for this txn
    ArrayList<OutboundMsg> useless = new ArrayList<>();
    synchronized (outboundMsgs) {
      for (OutboundMsg msg : outboundMsgs) {
        CoordinatorMsg inner_msg = msg.msg;
        if (inner_msg.txn_id == record.id && inner_msg.phase == TxnPhase.PHASE_I) {
          useless.add(msg);
        }
      }
      outboundMsgs.removeAll(useless);
    }

    CoordinatorMsg msg = CoordinatorMsg.GeneratePhaseIIMsg(record.id, record.decision);
    for (String destination : record.outstanding_participants) {
      msg.sendMyselfTo(PL, destination);
      timeMsg(msg, destination);
    }
  }

  @SuppressWarnings("unchecked")
  private void dealVote(String from, ParticipantMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_I);
    TxnMasterRecord record = log.retrieveRecord(msg.txn_id);
    if (record.status == TxnMasterRecord.Status.END) {
      return;
    }
    if (record.status == TxnMasterRecord.Status.DECISION) {
      // already made a decision, inform
      CoordinatorMsg decision_msg = CoordinatorMsg.GeneratePhaseIIMsg(record.id, record.decision);
      decision_msg.sendMyselfTo(PL, from);
      timeMsg(decision_msg, from);
      return;
    }

    if (msg.vote == TxnVote.DENIAL) {
      // this txn is aborted for sure, move to Phase II
      System.out.println("Server Aborts txn " + record.id);
      record.decision = TxnDecision.ABORT;
      record.status = TxnMasterRecord.Status.DECISION;
      record.outstanding_participants = (HashSet<String>) record.participants.clone();
      flushLog(); // FLUSH LOG
      resumeTxnPhaseII(record);
      return;
    }

    record.outstanding_participants.remove(from);
    if (record.outstanding_participants.isEmpty()) {
      // every participant has voted
      if (record.decision == TxnDecision.UNDECIDED) {
        record.decision = TxnDecision.COMMIT;
      }
      record.status = TxnMasterRecord.Status.DECISION;
      record.outstanding_participants = (HashSet<String>) record.participants.clone();

      // commit point to outside world
      // might have a race after flushLog and before outwrite the img to disk, IGNORE for now
      if (record.decision == TxnDecision.COMMIT) {
        System.out.println("Server commits and saves collage " + record.filename);
        try {
          RandomAccessFile f = new RandomAccessFile(record.filename, MODE);
          f.write(record.img);
          f.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      flushLog(); // FLUSH LOG

      // move to Phase II to distribute decision and collect ACK
      resumeTxnPhaseII(record);
    }
  }

  private void dealACK(String from, ParticipantMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_II);
    TxnMasterRecord record = log.retrieveRecord(msg.txn_id);
    assert (record.status != TxnMasterRecord.Status.PREPARE);
    if (record.status == TxnMasterRecord.Status.END) {
      // no more need for ACKs
      return;
    }
    record.outstanding_participants.remove(from);
    // flushLog(); // FLUSH LOG
    if (record.outstanding_participants.isEmpty()) {
      // all ACKs collected, this txn is completed
      System.out.println("Server: txn " + record.id + " is ENDED");
      record.status = TxnMasterRecord.Status.END;
      flushLog(); // FLUSH LOG

      synchronized (outboundMsgs) {
        // prune all outbound messages for this txn
        ArrayList<OutboundMsg> useless = new ArrayList<>();
        for (OutboundMsg outboundMsg : outboundMsgs) {
          if (outboundMsg.msg.txn_id == record.id) {
            useless.add(outboundMsg);
          }
        }
        outboundMsgs.removeAll(useless);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void recover() {
    if (new File(LOG_NAME).exists()) {
      System.out.println("Server comes online with DISK log");
      this.log = loadLog();
      for (TxnMasterRecord record : this.log.all_txns.values()) {
        if (record.status == TxnMasterRecord.Status.PREPARE) {
          // ABORT
          System.out.println("Serer Abort ongoing txn " + record.id);
          record.decision = TxnDecision.ABORT;
          record.status = TxnMasterRecord.Status.DECISION;
          record.outstanding_participants = (HashSet<String>) record.participants.clone();
          flushLog(); // FLUSH LOG
          resumeTxnPhaseII(record);
        } else if (record.status == TxnMasterRecord.Status.DECISION) {
          System.out.println("Serer continue committed txn " + record.id);
          resumeTxnPhaseII(record);
        }
      }

    } else {
      System.out.println("Server comes online with FRESH log");
      this.log = new TxnMasterLog();
    }
    finish_recovery = true;
  }

  public Server() {
    this.outboundMsgs = new ConcurrentLinkedDeque<>();
  }

  @Override
  public boolean deliverMessage(ProjectLib.Message msg) {
    while (!this.finish_recovery) {
    } // guard recovery
    String from = msg.addr;
    ParticipantMsg participantMsg = ParticipantMsg.deserialize(msg);
    System.out.println(
        "Server Got message from " + msg.addr + " about Msg: " + participantMsg.toString());
    if (participantMsg.phase == TxnPhase.PHASE_I) {
      dealVote(from, participantMsg);
    }

    if (participantMsg.phase == TxnPhase.PHASE_II) {
      dealACK(from, participantMsg);
    }
    return true;
  }

  @Override
  public void startCommit(String filename, byte[] img, String[] sources) {
    System.out.println(
        "Server: Got request to commit " + filename + " with sources " + Arrays.toString(sources));
    TxnMasterRecord new_record = log.createRecord(filename, img, sources);
    flushLog(); // FLUSH LOG
    resumeTxnPhaseI(new_record);
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 1)
      throw new Exception("Need 1 arg: <port>");
    Server srv = new Server();
    PL = new ProjectLib(Integer.parseInt(args[0]), srv, srv);
    srv.recover();

    // main loop for inspecting timeout messages
    while (true) {
      Thread.sleep(INTERVAL);
      srv.inspectTimeout();
    }
  }
}
