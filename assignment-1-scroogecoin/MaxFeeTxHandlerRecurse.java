import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive solution
*/
public class MaxFeeTxHandlerRecurse {
	
	private UTXOPool ledger;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandlerRecurse(UTXOPool utxoPool) {
    	ledger = new UTXOPool(utxoPool);
    }

    private boolean isValidTx(Transaction tx, UTXOPool currentLedger) {
    	Set<UTXO> claimedAlready = new HashSet<>();
    	double inputSum=0, outputSum=0;
    	// (1) all outputs claimed by {@code tx} are in the current UTXO pool
    	// Input --> (hash, index) --> new UTXO --> contained in pool
    	// (2) the signatures on each input of {@code tx} are valid
    	// Input --> sig
    	// tx --> message
    	// Input --> (hash, index) --> new UTXO --> output --> pk
    	int inputIndex = 0;
    	for (Transaction.Input input : tx.getInputs()) {
    		UTXO toFind = new UTXO(input.prevTxHash, input.outputIndex);
    		// Check (1)
    		if (!currentLedger.contains(toFind)) {
    			return false; // (1) fails
    		}
    		// Check (2)
    		Transaction.Output spentOutput = currentLedger.getTxOutput(toFind);
    		inputSum += spentOutput.value;
    		if (!Crypto.verifySignature(spentOutput.address, tx.getRawDataToSign(inputIndex++), input.signature)) {
    			return false; // (2) fails
    		}
    		// (3) no UTXO is claimed multiple times by {@code tx}
    		if (claimedAlready.contains(toFind)) {
    			return false; // (3) fails
    		}
    		claimedAlready.add(toFind);
    	}
    	// (4) all of {@code tx}s output values are non-negative
    	for (Transaction.Output output : tx.getOutputs()) {
    		outputSum += output.value;
    		if (output.value<0) {
    			return false; // (4) fails
    		}
    	}
    	// (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
    	return inputSum >= outputSum;
    }
    
    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     * Finds a set of transactions with maximum total transaction fees -- i.e. maximize the sum over 
     * all transactions in the set of (sum of input values - sum of output values)).
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	List<Transaction> available = new ArrayList<Transaction>(Arrays.asList(possibleTxs));
    	List<Transaction> chain = new ArrayList<>();
    	UTXOPool currentLedger = new UTXOPool(ledger);
    	handleTxsRecurse(available, chain, currentLedger);
    	return chain.toArray(new Transaction[chain.size()]);
    }
    
    private double handleTxsRecurse(List<Transaction> available, List<Transaction> chain, UTXOPool currentLedger) {
    	List<Transaction> maxChain = new ArrayList<>(chain);
    	double maxFees=0;
    	for (Transaction transaction : available) {
    		if (isValidTx(transaction, currentLedger)) {
    			UTXOPool newLedger = new UTXOPool(currentLedger);
    			chain.add(transaction);
    			double transactionFee = addAndCalcFee(transaction, newLedger);
    			List<Transaction> newAvailable = new ArrayList<Transaction>(available);
    			newAvailable.remove(transaction);
    			List<Transaction> newChain = new ArrayList<Transaction>(chain);
    			double chainFees = transactionFee + handleTxsRecurse(newAvailable, newChain, newLedger);
        		if (chainFees>maxFees) {
        			maxFees = chainFees;
        			maxChain = newChain;
        		}
        		chain.remove(transaction);
    		}
		}
    	chain.clear();
    	chain.addAll(maxChain);
    	return maxFees;
    }
    
    private double addAndCalcFee(Transaction tx, UTXOPool currentLedger) {
    	double inputSum=0, outputSum=0;
		// Remove consumed
		for (Transaction.Input input : tx.getInputs()) {
			UTXO toConsume = new UTXO(input.prevTxHash, input.outputIndex);
    		Transaction.Output spentOutput = currentLedger.getTxOutput(toConsume);
    		inputSum += spentOutput.value;
			currentLedger.removeUTXO(toConsume);
		}
		// Add unspent
		int index=0;
		for (Transaction.Output output : tx.getOutputs()) {
			outputSum += output.value;
			UTXO toAdd = new UTXO(tx.getHash(), index++);
			currentLedger.addUTXO(toAdd, output);
		}
		return inputSum-outputSum;
    }

}
