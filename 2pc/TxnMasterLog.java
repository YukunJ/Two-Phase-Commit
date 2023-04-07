/**
 * TxnMasterLog.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the implementation for the TxnMasterLog instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The Collected log records for all the happening transactions
 * by hand of the Coordinator, i.e. should be a Singleton class
 */

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TxnMasterLog implements Serializable {
  public AtomicInteger highest_txn_id;
  public ConcurrentHashMap<Integer, TxnMasterRecord> all_txns;

  public TxnMasterLog() {
    highest_txn_id = new AtomicInteger(0);
    all_txns = new ConcurrentHashMap<>();
  }

  public TxnMasterRecord retrieveRecord(int txn_id) {
    return all_txns.get(txn_id);
  }

  public TxnMasterRecord createRecord(String filename, byte[] img, String[] sources) {
    int txn_id = highest_txn_id.getAndAdd(1);
    TxnMasterRecord new_record = new TxnMasterRecord(txn_id, filename, img, sources);
    all_txns.put(txn_id, new_record);
    return new_record;
  }
}
