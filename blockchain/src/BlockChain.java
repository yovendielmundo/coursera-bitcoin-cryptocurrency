// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.HashMap;
import java.util.Map;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;


    private final Map<ByteArrayWrapper, BlockNode> blockChain;
    private final TransactionPool txPool;
    private BlockNode maxHeightBlockNode;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        this.blockChain = new HashMap<>();

        final UTXOPool genesisUTXOPool = UTXOPoolWithCoinBaseUTXO(new UTXOPool(), genesisBlock);
        final BlockNode genesisBlockNode = new BlockNode(genesisBlock, genesisUTXOPool, 0);

        putBlockNode(genesisBlockNode);
        this.maxHeightBlockNode = genesisBlockNode;

        this.txPool = new TransactionPool();
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightBlockNode.getBlock();
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightBlockNode.getUTXOPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return this.txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (height - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {

        final BlockNode parentBlockNode = getBlockNode(block.getPrevBlockHash());
        if (parentBlockNode == null) {
            return false;
        }

        final int newHeight = parentBlockNode.getHeight() + 1;
        if (newHeight <= this.maxHeightBlockNode.getHeight() - CUT_OFF_AGE) {
            return false;
        }

        final TxHandler txHandler = new TxHandler(parentBlockNode.getUTXOPool());

        final Transaction[] validTxs = txHandler.handleTxs(block.getTransactions().toArray(new Transaction[]{}));

        if (validTxs.length != block.getTransactions().size()) {
            return false;
        }

        final BlockNode newBlockNode = new BlockNode(block, UTXOPoolWithCoinBaseUTXO(txHandler.getUTXOPool(), block), newHeight);

        putBlockNode(newBlockNode);

        if (newHeight > this.maxHeightBlockNode.getHeight()) {
            maxHeightBlockNode = newBlockNode;
        }

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        this.txPool.addTransaction(tx);
    }

    private void putBlockNode(final BlockNode blockNode) {
        this.blockChain.put(new ByteArrayWrapper(blockNode.getBlock().getHash()), blockNode);
    }

    private BlockNode getBlockNode(final byte[] hash) {
        if (hash == null) {
            return null;
        }
        return this.blockChain.get(new ByteArrayWrapper(hash));
    }

    private static UTXOPool UTXOPoolWithCoinBaseUTXO(final UTXOPool utxoPool, final Block block) {
        final Transaction coinBaseTx = block.getCoinbase();
        utxoPool.addUTXO(new UTXO(coinBaseTx.getHash(), 0), coinBaseTx.getOutput(0));
        return utxoPool;
    }

    private class BlockNode {
        private final Block block;
        private final int height;
        private final UTXOPool utxoPool;

        BlockNode(Block block, UTXOPool utxoPool, int height) {
            this.block = block;
            this.utxoPool = utxoPool;
            this.height = height;
        }

        Block getBlock() {
            return block;
        }

        int getHeight() { return height; }

        UTXOPool getUTXOPool() { return utxoPool; }
    }
}