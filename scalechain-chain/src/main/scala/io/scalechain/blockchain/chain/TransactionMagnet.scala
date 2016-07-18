package io.scalechain.blockchain.chain

import com.typesafe.scalalogging.Logger
import io.scalechain.blockchain.storage.index.TransactionDescriptorIndex
import io.scalechain.blockchain.transaction.ChainBlock
import io.scalechain.blockchain.{ErrorCode, ChainException}
import io.scalechain.blockchain.proto._
import io.scalechain.blockchain.storage.{TransactionPoolIndex, TransactionLocator, BlockStorage}
import io.scalechain.blockchain.script.HashSupported._
import org.slf4j.LoggerFactory

/**
  * The transaction maganet which is able to attach or detach transactions.
  *
  * @param txDescIndex The storage for block.
  * @param txPoolIndex The storage for transaction pool. If not given, set to storage.
  *                      During mining, txPoolStorage is a separate transaction pool for testing dependency of each transaction.
  *                      Otherwise, txPoolStorage is the 'storage' parameter.
  */
class TransactionMagnet(txDescIndex : TransactionDescriptorIndex, txPoolIndex: TransactionPoolIndex) {
  private val logger = Logger( LoggerFactory.getLogger(classOf[TransactionMagnet]) )

  protected [chain] var chainEventListener : Option[ChainEventListener] = None

  /** Set an event listener of the blockchain.
    *
    * @param listener The listener that wants to be notified for new blocks, invalidated blocks, and transactions comes into and goes out from the transaction pool.
    */
  def setEventListener( listener : ChainEventListener ): Unit = {
    chainEventListener = Some(listener)
  }

  /**
    * Get the list of in-points that are spending the outputs of a transaction
    *
    * @param txHash The hash of the transaction.
    * @return The list of in-points that are spending the outputs of the transaction
    */
  protected[chain] def getOutputsSpentBy(txHash : Hash) : List[Option[InPoint]] = {
    txDescIndex.getTransactionDescriptor(txHash).map(_.outputsSpentBy).getOrElse {
      txPoolIndex.getTransactionFromPool(txHash).map(_.outputsSpentBy).getOrElse {
        null
      }
    }
  }

  /**
    * Put the list of in-points that are spending the outputs of a transaction
    *
    * @param txHash The hash of the transaction.
    * @param outputsSpentBy The list of in-points that are spending the outputs of the transaction
    */
  protected[chain] def putOutputsSpentBy(txHash : Hash, outputsSpentBy : List[Option[InPoint]]) = {
    val txDescOption = txDescIndex.getTransactionDescriptor(txHash)
    val txPoolEntryOption = txPoolIndex.getTransactionFromPool(txHash)
    if ( txDescOption.isDefined) {
      txDescIndex.putTransactionDescriptor(
        txHash,
        txDescOption.get.copy(
          outputsSpentBy = outputsSpentBy
        )
      )
      assert(txPoolEntryOption.isEmpty)
    } else {
      assert( txPoolEntryOption.isDefined )
      txPoolIndex.putTransactionToPool(
        txHash,
        txPoolEntryOption.get.copy(
          outputsSpentBy = outputsSpentBy
        )
      )
      assert(txDescOption.isEmpty)
    }
  }

  /**
    * Mark an output spent by the given in-point.
    *
    * @param outPoint The out-point that points to the output to mark.
    * @param inPoint The in-point that points to a transaction input that spends to output.
    * @param checkOnly If true, do not update the spending in-point, just check if the output is a valid UTXO.
    */
  protected[chain] def markOutputSpent(outPoint : OutPoint, inPoint : InPoint, checkOnly : Boolean): Unit = {
    val outputsSpentBy : List[Option[InPoint]] = getOutputsSpentBy(outPoint.transactionHash)
    if (outputsSpentBy == null) {
      val message = s"An output pointed by an out-point(${outPoint}) spent by the in-point(${inPoint}) points to a transaction that does not exist yet."
      if (!checkOnly)
        logger.warn(message)
      throw new ChainException(ErrorCode.ParentTransactionNotFound, message)
    }

    // TODO : BUGBUG : indexing into a list is slow. Optimize the code.
    if ( outPoint.outputIndex < 0 || outputsSpentBy.length <= outPoint.outputIndex ) {
      // TODO : Add DoS score. The outpoint in a transaction input was invalid.
      val message = s"An output pointed by an out-point(${outPoint}) spent by the in-point(${inPoint}) has invalid transaction output index."
      if (!checkOnly)
        logger.warn(message)
      throw new ChainException(ErrorCode.InvalidTransactionOutPoint, message)
    }

    val spendingInPointOption = outputsSpentBy(outPoint.outputIndex)
    if( spendingInPointOption.isDefined ) { // The transaction output was already spent.
      if ( spendingInPointOption.get == inPoint ) {
        // Already marked as spent by the given in-point.
        // This can happen when a transaction is already attached while it was put into the transaction pool,
        // But tried to attach again while accepting a block that has the (already attached) transaction.
      } else {
        val message = s"An output pointed by an out-point(${outPoint}) has already been spent by ${spendingInPointOption.get}. The in-point(${inPoint}) tried to spend it again."
        if (!checkOnly)
          logger.warn(message);
        throw new ChainException(ErrorCode.TransactionOutputAlreadySpent, message)
      }
    } else {
      if (checkOnly) {
        // Do not update, just check if the output can be marked as spent.
      } else {
        putOutputsSpentBy(
          outPoint.transactionHash,
          outputsSpentBy.updated(outPoint.outputIndex, Some(inPoint))
        )
      }
    }
  }

  /**
    * Mark an output unspent. The output should have been marked as spent by the given in-point.
    *
    * @param outPoint The out-point that points to the output to mark.
    * @param inPoint The in-point that points to a transaction input that should have spent the output.
    */
  protected[chain] def markOutputUnspent(outPoint : OutPoint, inPoint : InPoint): Unit = {
    val outputsSpentBy : List[Option[InPoint]] = getOutputsSpentBy(outPoint.transactionHash)
    if (outputsSpentBy == null) {
      val message = s"An output pointed by an out-point(${outPoint}) spent by the in-point(${inPoint}) points to a transaction that does not exist."
      logger.warn(message)
      throw new ChainException(ErrorCode.ParentTransactionNotFound, message)
    }

    // TODO : BUGBUG : indexing into a list is slow. Optimize the code.
    if ( outPoint.outputIndex < 0 || outputsSpentBy.length <= outPoint.outputIndex ) {
      // TODO : Add DoS score. The outpoint in a transaction input was invalid.
      val message = s"An output pointed by an out-point(${outPoint}) has invalid transaction output index. The output should have been spent by ${inPoint}"
      logger.warn(message)
      throw new ChainException(ErrorCode.InvalidTransactionOutPoint, message)
    }

    val spendingInPointOption = outputsSpentBy(outPoint.outputIndex)
    // The output pointed by the out-point should have been spent by the transaction input poined by the given in-point.
    assert( spendingInPointOption.isDefined )

    if( spendingInPointOption.get != inPoint ) { // The transaction output was NOT spent by the transaction input poined by the given in-point.
    val message = s"An output pointed by an out-point(${outPoint}) was not spent by the expected transaction input pointed by the in-point(${inPoint}), but spent by ${spendingInPointOption.get}."
      logger.warn(message)
      throw new ChainException(ErrorCode.TransactionOutputSpentByUnexpectedInput, message)
    }

    putOutputsSpentBy(
      outPoint.transactionHash,
      outputsSpentBy.updated(outPoint.outputIndex, None)
    )
  }

  /**
    * Detach the transaction input from the best blockchain.
    * The output spent by the transaction input is marked as unspent.
    *
    * @param inPoint The in-point that points to the input to attach.
    * @param transactionInput The transaction input to attach.
    */
  protected[chain] def detachTransactionInput(inPoint : InPoint, transactionInput : TransactionInput) : Unit = {
    // Make sure that the transaction input is not a coinbase input. detachBlock already checked if the input was NOT coinbase.
    assert(!transactionInput.isCoinBaseInput())

    markOutputUnspent(transactionInput.getOutPoint(), inPoint)
  }

  /**
    * Detach each of transction inputs. Mark outputs spent by the transaction inputs unspent.
    *
    * @param transactionHash The hash of the tranasction that has the inputs.
    * @param transaction The transaction that has the inputs.
    */
  protected[chain] def detachTransactionInputs(transactionHash : Hash, transaction : Transaction) : Unit = {
    var inputIndex = -1
    transaction.inputs foreach { transactionInput : TransactionInput =>
      inputIndex += 1

      // Make sure that the transaction input is not a coinbase input. detachBlock already checked if the input was NOT coinbase.
      assert(!transactionInput.isCoinBaseInput())

      detachTransactionInput(InPoint(transactionHash, inputIndex), transactionInput)
    }
  }

  /**
    * Detach the transaction from the best blockchain.
    *
    * For outputs, all outputs spent by the transaction is marked as unspent.
    *
    * @param transaction The transaction to detach.
    */
  def detachTransaction(transaction : Transaction) : Unit = {
    val transactionHash = transaction.hash

    // Step 1 : Detach each transaction input
    if (transaction.inputs(0).isCoinBaseInput()) {
      // Nothing to do for the coinbase inputs.
    } else {
      detachTransactionInputs(transactionHash, transaction)
    }

    // Remove the transaction descriptor otherwise other transactions can spend the UTXO from the detached transaction.
    // The transaction might not be stored in a block on the best blockchain yet. Remove the transaction from the pool too.
    txDescIndex.delTransactionDescriptor(transactionHash)
    txPoolIndex.delTransactionFromPool(transactionHash)

    chainEventListener.map(_.onRemoveTransaction(transaction))
  }

  /**
    * The UTXO pointed by the transaction input is marked as spent by the in-point.
    *
    * @param inPoint The in-point that points to the input to attach.
    * @param transactionInput The transaction input to attach.
    * @param checkOnly If true, do not attach the transaction input, but just check if the transaction input can be attached.
    *
    */
  protected[chain] def attachTransactionInput(inPoint : InPoint, transactionInput : TransactionInput, checkOnly : Boolean) : Unit = {
    // Make sure that the transaction input is not a coinbase input. attachBlock already checked if the input was NOT coinbase.
    assert(!transactionInput.isCoinBaseInput())

    // TODO : Step 1. read CTxIndex from disk if not read yet.
    // TODO : Step 2. read the transaction that the outpoint points from disk if not read yet.
    // TODO : Step 3. Increase DoS score if an invalid output index was found in a transaction input.
    // TODO : Step 4. check coinbase maturity for outpoints spent by a transaction.
    // TODO : Step 5. Skip ECDSA signature verification when connecting blocks (fBlock=true) during initial download
    // TODO : Step 6. check value range of each input and sum of inputs.
    // TODO : Step 7. for the transaction output pointed by the input, mark this transaction as the spending transaction of the output. check double spends.
    markOutputSpent(transactionInput.getOutPoint(), inPoint, checkOnly)
  }

  /** Attach the transaction inputs to the outputs spent by them.
    * Mark outputs spent by the transaction inputs.
    *
    * @param transactionHash The hash of the tranasction that has the inputs.
    * @param transaction The transaction that has the inputs.
    * @param checkOnly If true, do not attach the transaction inputs, but just check if the transaction inputs can be attached.
    *
    */
  protected[chain] def attachTransactionInputs(transactionHash : Hash, transaction : Transaction, checkOnly : Boolean) : Unit = {
    var inputIndex = -1
    transaction.inputs foreach { transactionInput : TransactionInput =>
      // Make sure that the transaction input is not a coinbase input. attachBlock already checked if the input was NOT coinbase.
      assert(!transactionInput.isCoinBaseInput())
      inputIndex += 1

      attachTransactionInput(InPoint(transactionHash, inputIndex), transactionInput, checkOnly)
    }
  }

  /**
    * Attach the transaction into the best blockchain.
    *
    * For UTXOs, all outputs spent by the transaction is marked as spent by this transaction.
    *
    * @param transactionHash The hash of the transaction to attach.
    * @param transaction The transaction to attach.
    * @param checkOnly If true, do not attach the transaction inputs, but just check if the transaction inputs can be attached.
    * @param txLocatorOption Some(locator) if the transaction is stored in a block on the best blockchain; None if the transaction should be stored in a mempool.
    */
  protected[chain] def attachTransaction(transactionHash : Hash, transaction : Transaction, checkOnly : Boolean, txLocatorOption : Option[FileRecordLocator] = None, chainBlock : Option[ChainBlock] = None, transactionIndex : Option[Int] = None) : Unit = {
    // Step 1 : Attach each transaction input
    if (transaction.inputs(0).isCoinBaseInput()) {
      // Nothing to do for the coinbase inputs.
    } else {
      attachTransactionInputs(transactionHash, transaction, checkOnly)
    }

    if (checkOnly) {
      // Do nothing. We just want to check if we can attach the transaction.
    } else {
      // Need to set the transaction locator of the transaction descriptor according to the location of the attached block.
      if (txLocatorOption.isDefined) {
        // If the txLocator is defined, the block height should also be defined.
        assert( chainBlock.isDefined )
        txDescIndex.putTransactionDescriptor(transactionHash,
          TransactionDescriptor(
            transactionLocator = txLocatorOption.get,
            blockHeight = chainBlock.get.height,
            List.fill(transaction.outputs.length)(None)
          )
        )
      } else {
        txPoolIndex.putTransactionToPool(
          transactionHash,
          TransactionPoolEntry(
            transaction,
            List.fill(transaction.outputs.length)(None) )
        )
      }

      chainEventListener.map(_.onNewTransaction(transaction, chainBlock, transactionIndex))
    }

    // TODO : Step 2 : check if the sum of input values is greater than or equal to the sum of outputs.
    // TODO : Step 3 : make sure if the fee is not negative.
    // TODO : Step 4 : check the minimum transaction fee for each transaction.
  }
}
