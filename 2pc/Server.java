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

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements ProjectLib.CommitServing, ProjectLib.MessageHandling {
  private final TxnMasterLog log;
  public static ProjectLib PL;
  private static final String SEP = ":";
  private static final int PARTICIPANT_IDX = 0;
  private static final int FILENAME_IDX = 1;

  private static final String MODE = "rws";

  /* Continue Phase I of a txn */
  private void resumeTxnPhaseI(TxnMasterRecord record) {
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
    }
  }

  /* Continue Phase II of a txn */
  private void resumeTxnPhaseII(TxnMasterRecord record) {
    CoordinatorMsg msg = CoordinatorMsg.GeneratePhaseIIMsg(record.id, record.decision);
    for (String destination : record.outstanding_participants) {
      msg.sendMyselfTo(PL, destination);
    }
  }

  @SuppressWarnings("unchecked")
  private void dealVote(String from, ParticipantMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_I);
    // TODO: flush log ?
    TxnMasterRecord record = log.retrieveRecord(msg.txn_id);
    if (msg.vote == TxnVote.DENIAL) {
      // this txn is aborted for sure, but wait for all replies
      record.decision = TxnDecision.ABORT;
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

      // move to Phase II to distribute decision and collect ACK
      resumeTxnPhaseII(record);
    }
  }

  private void dealACK(String from, ParticipantMsg msg) {
    assert (msg.phase == TxnPhase.PHASE_II);
    // TODO: flush log ?
    TxnMasterRecord record = log.retrieveRecord(msg.txn_id);
    assert (record.status != TxnMasterRecord.Status.PREPARE);
    record.outstanding_participants.remove(from);
    if (record.outstanding_participants.isEmpty()) {
      // all ACKs collected, this txn is completed
      record.status = TxnMasterRecord.Status.END;
    }
  }

  public Server() {
    // TODO: Load Log from disk?
    log = new TxnMasterLog();
  }

  @Override
  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
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
    // TODO: Flush Log ?
    TxnMasterRecord new_record = log.createRecord(filename, img, sources);
    resumeTxnPhaseI(new_record);
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 1)
      throw new Exception("Need 1 arg: <port>");
    Server srv = new Server();
    PL = new ProjectLib(Integer.parseInt(args[0]), srv, srv);

    // main loop
    while (true) {
      //      ProjectLib.Message msg = PL.getMessage();
      //      System.out.println("Server: Got message from " + msg.addr);
      //      System.out.println("Server: Echoing message to " + msg.addr);
      //      PL.sendMessage(msg);
    }
  }
}
