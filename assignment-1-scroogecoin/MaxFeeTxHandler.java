import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        UTXOPool uniqueUtxos = new UTXOPool();
        double previousTxOutSum = 0;
        double currentTxOutSum = 0;
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input in = tx.getInput(i);
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            if (!utxoPool.contains(utxo)) return false;
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), in.signature))
                return false;
            if (uniqueUtxos.contains(utxo)) return false;
            uniqueUtxos.addUTXO(utxo, output);
            previousTxOutSum += output.value;
        }
        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0) return false;
            currentTxOutSum += out.value;
        }
        return previousTxOutSum >= currentTxOutSum;
    }

    private double calcTxFees(Transaction tx) {
        double sumInputs = 0;
        double sumOutputs = 0;
        for (Transaction.Input in : tx.getInputs()) {
            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
            if (!utxoPool.contains(utxo) || !isValidTx(tx)) continue;
            Transaction.Output txOutput = utxoPool.getTxOutput(utxo);
            sumInputs += txOutput.value;
        }
        for (Transaction.Output out : tx.getOutputs()) {
            sumOutputs += out.value;
        }
        return sumInputs - sumOutputs;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        Set<Transaction> txsSortedByFees = new TreeSet<>((tx1, tx2) -> {
            double tx1Fees = calcTxFees(tx1);
            double tx2Fees = calcTxFees(tx2);
            return Double.valueOf(tx2Fees).compareTo(tx1Fees);
        });

        Collections.addAll(txsSortedByFees, possibleTxs);

        ArrayList<Transaction> acceptedTxs = new ArrayList<>();
        for (Transaction tx : txsSortedByFees) {
            if (isValidTx(tx)) {
                acceptedTxs.add(tx);
                for (Transaction.Input in : tx.getInputs()) {
                    UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                    utxoPool.removeUTXO(utxo);
                }
                for (int i = 0; i < tx.numOutputs(); i++) {
                    Transaction.Output out = tx.getOutput(i);
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    utxoPool.addUTXO(utxo, out);
                }
            }
        }

        Transaction[] validTxArray = new Transaction[acceptedTxs.size()];
        return acceptedTxs.toArray(validTxArray);
    }
}
