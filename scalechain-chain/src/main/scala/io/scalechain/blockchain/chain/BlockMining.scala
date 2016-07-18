package io.scalechain.blockchain.chain

import java.io.File

import com.typesafe.scalalogging.Logger
import io.scalechain.blockchain.ChainException
import io.scalechain.blockchain.chain.mining.BlockTemplate
import io.scalechain.blockchain.proto.codec.TransactionCodec
import io.scalechain.blockchain.proto.{TransactionOutput, OutPoint, Transaction, CoinbaseData}
import io.scalechain.blockchain.script.HashSupported
import io.scalechain.blockchain.storage.{TransactionPoolIndex, BlockStorage}
import io.scalechain.blockchain.storage.index.{RocksDatabase, TransactionDescriptorIndex}
import io.scalechain.blockchain.transaction.{CoinsView, CoinAmount, CoinAddress}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.util.Random
import HashSupported._


class TemporaryTransactionPoolIndex(directoryPath : File) extends TransactionPoolIndex {
  private val logger = Logger( LoggerFactory.getLogger(classOf[TemporaryTransactionPoolIndex]) )

  directoryPath.mkdir()

  // Implemenent the KeyValueDatabase declared in BlockStorage trait.
  val keyValueDB = new RocksDatabase(directoryPath)

  def close() : Unit = {
    this.synchronized {
      keyValueDB.close()
    }
  }
}

class TemporaryCoinsView(coinsView : CoinsView, tempTxPoolIndex : TemporaryTransactionPoolIndex) extends CoinsView {
  /** Return a transaction output specified by a give out point.
    *
    * @param outPoint The outpoint that points to the transaction output.
    * @return The transaction output we found.
    */
  def getTransactionOutput(outPoint : OutPoint) : TransactionOutput = {
    // Find from the temporary transaction pool index first, and then find from the coins view.
    tempTxPoolIndex.getTransactionFromPool(outPoint.transactionHash).map(_.transaction.outputs(outPoint.outputIndex)).getOrElse {
      coinsView.getTransactionOutput(outPoint)
    }
  }
}

/**
  * Created by kangmo on 6/9/16.
  */
class BlockMining(txDescIndex : TransactionDescriptorIndex, transactionPool : TransactionPool, coinsView : CoinsView) {
  private val logger = Logger( LoggerFactory.getLogger(classOf[BlockMining]) )

  /*
    /** Calculate the (encoded) difficulty bits that should be in the block header.
      *
      * @param prevBlockDesc The descriptor of the previous block. This method calculates the difficulty of the next block of the previous block.
      * @return
      */
    def calculateDifficulty(prevBlockDesc : BlockInfo) : Long = {
      if (prevBlockDesc.height == 0) { // The genesis block
        GenesisBlock.BLOCK.header.target
      } else {
        // BUGBUG : Make sure that the difficulty calculation is same to the one in the Bitcoin reference implementation.
        val currentBlockHeight = prevBlockDesc.height + 1
        if (currentBlockHeight % 2016 == 0) {
          // TODO : Calculate the new difficulty bits.
          assert(false)
          -1L
        } else {
          prevBlockDesc.blockHeader.target
        }
      }
    }
  */

  /** Get the template for creating a block containing a list of transactions.
    *
    * @return The block template which has a sorted list of transactions to include into a block.
    */
  def getBlockTemplate(coinbaseData : CoinbaseData, minerAddress : CoinAddress, maxBlockSize : Int) : BlockTemplate = {
    // TODO : P1 - Use difficulty bits
    //val difficultyBits = getDifficulty()
    val difficultyBits = 10

    val validTransactions : List[Transaction] = transactionPool.getTransactionsFromPool().map {
      case (txHash, transaction) => transaction
    }

    val generationTranasction =
      TransactionBuilder.newBuilder(coinsView)
        .addGenerationInput(coinbaseData)
        .addOutput(CoinAmount(50), minerAddress)
        .build()

    // Select transactions by priority and fee. Also, sort them.
    val sortedTransactions = selectTransactions(generationTranasction, validTransactions, maxBlockSize)

    new BlockTemplate(difficultyBits, sortedTransactions)
  }


  /** Select transactions to include into a block.
    *
    *  Order transactions by dependency and by fee(in descending order).
    *  List N transactions based on the priority and fee so that the serialzied size of block
    *  does not exceed the max size. (ex> 1MB)
    *
    *  <Called by>
    *  When a miner tries to create a block, we have to create a block template first.
    *  The block template has the transactions to keep in the block.
    *  In the block template, it has all fields set except the nonce and the timestamp.
    *
    *  The first criteria for ordering transactions in a block is the transaction dependency.
    *
    *  Why is ordering transactions in a block based on dependency is necessary?
    *    When blocks are reorganized, transactions in the block are detached the reverse order of the transactions stored in a block.
    *    Also, they are attached in the same order of the transactions stored in a block.
    *    The order of transactions in a block should be based on the dependency, otherwise, an outpoint in an input of a transaction may point to a non-existent transaction by the time it is attached.    *
    *
    *
    *  How?
    *    1. Create a priority queue that has complete(= all required transactions exist) transactions.
    *    2. The priority is based on the transaction fee, for now. In the future, we need to improve the priority to consider the amount of coin to transfer.
    *    3. Prepare a temporary transaction pool. The pool will be used to look up dependent transactions while trying to attach transactions.
    *    4. Try to attach each transaction in the input list depending on transactions on the temporary transaction pool instead of the transaction pool in Blockchain. (We should not actually attach the transaction, but just 'try to' attach the transaction without changing the "spent" in-point of UTXO.)
    *    5. For all complete transactions that can be attached, move from the input list to the priority queue.
    *    6. If there is any transaction in the priority queue, pick the best transaction with the highest priority into the temporary transaction pool, and Go to step 4. Otherwise, stop iteration.
    *
    * @param transactions The candidate transactions
    * @param maxBlockSize The maximum block size. The serialized block size including the block header and transactions should not exceed the size.
    * @return The transactions to put into a block.
    */
  protected[chain] def selectTransactions(generationTransaction:Transaction, transactions : List[Transaction], maxBlockSize : Int) : List[Transaction] = {
    val candidateTransactions = new ListBuffer[Transaction]()
    candidateTransactions ++= transactions
    val selectedTransactions = new ListBuffer[Transaction]()

    val BLOCK_HEADER_SIZE = 80
    val MAX_TRANSACTION_LENGTH_SIZE = 9 // The max size of variable int encoding.
    var serializedBlockSize = BLOCK_HEADER_SIZE + MAX_TRANSACTION_LENGTH_SIZE

    serializedBlockSize += TransactionCodec.serialize(generationTransaction).length
    selectedTransactions.append(generationTransaction)

    val tempTxPoolFile = new File(s"./target/temp-tx-pool-${Random.nextInt}")
    val tempTranasctionPoolIndex = new TemporaryTransactionPoolIndex( tempTxPoolFile )
    // The TemporaryCoinsView with additional transactions in the temporary transaction pool.
    // TemporaryCoinsView returns coins in the transaction pool of the coinsView, which may not be included in tempTranasctionPoolIndex,
    // But this should be fine, because we are checking if a transaction can be attached without including the transaction pool of the coinsView.
    val tempCoinsView = new TemporaryCoinsView(coinsView, tempTranasctionPoolIndex)
    val txQueue = new TransactionPriorityQueue(tempCoinsView)

    val txMagnet = new TransactionMagnet(txDescIndex, tempTranasctionPoolIndex)

    // For all attachable transactions, attach them, and move to the priority queue.
    try {
      var newlySelectedTransaction : Option[Transaction] = None
      do {
        candidateTransactions foreach { tx : Transaction =>
          val txHash = tx.hash
          // Test if it can be atached.
          val isTxAttachable = try {
            txMagnet.attachTransaction(txHash, tx, checkOnly = true)
            true
          } catch {
            case e : ChainException => {
              false
            }  // The transaction can't be attached.
          }

          if (isTxAttachable) { // move the the transaction queue
            candidateTransactions -= tx
            txQueue.enqueue(tx)
          }
        }

        newlySelectedTransaction = txQueue.dequeue()

        if (newlySelectedTransaction.isDefined) {
          val newTx = newlySelectedTransaction.get
          serializedBlockSize += TransactionCodec.serialize(newTx).length
          if (serializedBlockSize <= maxBlockSize) {
            // Attach the transaction
            txMagnet.attachTransaction(newTx.hash, newTx, checkOnly = false)
            selectedTransactions += newTx
          }
        }
      } while(newlySelectedTransaction.isDefined && (serializedBlockSize <= maxBlockSize) )
      // Caution : serializedBlockSize is greater than the actual block size

      selectedTransactions.toList

    } finally {
      tempTranasctionPoolIndex.close
      FileUtils.deleteDirectory(tempTxPoolFile)
    }
  }
}
