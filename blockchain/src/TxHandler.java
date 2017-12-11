import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class TxHandler {

    private final UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public UTXOPool getUTXOPool() {
        return utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        final List<UTXO> txUTXOs = tx.getInputs().stream()
                .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
                .collect(Collectors.toList());

        return containsAllOutputs(txUTXOs) &&
                isAllValidSignatures(tx) &&
                isAllPositiveOutputValues(tx) &&
                isAllUniqueUTXOs(tx, txUTXOs) &&
                sumInputsIsGreaterThanOrEqualSumOutputs(tx, txUTXOs);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        final Set<Transaction> validTxs = Stream.of(possibleTxs)
                .filter(this::isValidTx)
                .peek(tx -> {
                    for (Transaction.Input in : tx.getInputs()) {
                        UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                        utxoPool.removeUTXO(utxo);
                    }
                    for (int i = 0; i < tx.numOutputs(); i++) {
                        Transaction.Output out = tx.getOutput(i);
                        UTXO utxo = new UTXO(tx.getHash(), i);
                        utxoPool.addUTXO(utxo, out);
                    }
                })
                .collect(toSet());

        return validTxs.toArray(new Transaction[validTxs.size()]);
    }

    private boolean containsAllOutputs(final List<UTXO> txUTXOs) {
        return txUTXOs.stream().allMatch(utxoPool::contains);
    }

    private boolean isAllValidSignatures(final Transaction tx) {
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!Crypto.verifySignature(utxoPool.getTxOutput(utxo).address, tx.getRawDataToSign(i), input.signature))
                return false;
        }
        return true;
    }

    private boolean isAllPositiveOutputValues(final Transaction tx) {
        return tx.getOutputs().stream()
                .allMatch(output -> output.value >= 0);
    }

    private boolean isAllUniqueUTXOs(final Transaction tx, final List<UTXO> txUTXOs) {
        final Long uniqueUTXOsCount = txUTXOs.stream().distinct().count();
        return tx.numInputs() == uniqueUTXOsCount;
    }

    private boolean sumInputsIsGreaterThanOrEqualSumOutputs(final Transaction tx, final List<UTXO> txUTXOs) {
        final Double sumInputValues = txUTXOs.stream()
                .map(utxo -> utxoPool.getTxOutput(utxo).value)
                .reduce(0.0, Double::sum);

        final Double sumOutputValues = tx.getOutputs().stream()
                .map(output -> output.value)
                .reduce(0.0, Double::sum);

        return sumInputValues >= sumOutputValues;
    }
}
