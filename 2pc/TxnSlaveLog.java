/**
 * TxnSlaveLog.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the implementation for the TxnSlaveLog instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The collection of TxnSlaveRecord used by the participant nodes
 * the log should be stored to persistent storage in fear of faiure
 */

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class TxnSlaveLog {
  public ConcurrentHashMap<Integer, TxnSlaveRecord> all_txns;
  public ConcurrentHashMap<String, Integer> locked_resources;

  public TxnSlaveLog() {
    all_txns = new ConcurrentHashMap<>();
    locked_resources = new ConcurrentHashMap<>();
  }

  public TxnSlaveRecord retrieveRecord(int txn_id) {
    return all_txns.get(txn_id);
  }

  public TxnSlaveRecord createRecord(
      int txn_id, String filename, String[] resources_requested, TxnVote vote) {
    TxnSlaveRecord new_record = new TxnSlaveRecord(txn_id, filename, resources_requested, vote);
    all_txns.put(txn_id, new_record);
    return new_record;
  }
}
