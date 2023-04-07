/**
 * TxnVote.java
 * author: Yukun Jiang
 * Date: April 06, 2023
 *
 * This is the Enum class for a transaction's phase 1 voting
 * either approval or denial
 */

import java.io.Serializable;

public enum TxnVote implements Serializable { APPROVAL, DENIAL, NOT_VOTE }
