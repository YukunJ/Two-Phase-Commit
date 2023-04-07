/**
 * TxnDecision.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the Enum class for a transaction's final decision made by
 * Coordinator, either ABORT or COMMIT, remain UNDECIDED until decision is made
 */

import java.io.Serializable;

public enum TxnDecision implements Serializable { COMMIT, ABORT, UNDECIDED }
