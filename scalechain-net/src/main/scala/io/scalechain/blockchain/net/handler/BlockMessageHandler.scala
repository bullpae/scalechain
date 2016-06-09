package io.scalechain.blockchain.net.handler

import io.scalechain.blockchain.chain.Blockchain
import io.scalechain.blockchain.chain.processor.BlockProcessor
import io.scalechain.blockchain.net.message.{InvFactory, GetBlocksFactory}
import io.scalechain.blockchain.proto.{Hash, Block, ProtocolMessage, Addr}
import org.slf4j.LoggerFactory
import io.scalechain.blockchain.script.HashSupported._

/**
  * The message handler for Block message.
  */
object BlockMessageHandler {
  private lazy val logger = LoggerFactory.getLogger(BlockMessageHandler.getClass)

  val chain = Blockchain.get

  /** Handle Block message.
    *
    * @param context The context where handlers handling different messages for a peer can use to store state data.
    * @param block The Block message to handle.
    * @return Some(message) if we need to respond to the peer with the message.
    */
  def handle( context : MessageHandlerContext, block : Block ) : Unit = {
    // TODO : BUGBUG : Need to think about RocksDB transactions.

    val blockHash = block.header.hash


    logger.info(s"[P2P] Received a block. Hash : ${blockHash}")

    if (BlockProcessor.exists(blockHash)) {
      logger.warn(s"[P2P] Duplicate block was received. Hash : ${blockHash}")
    } else {
      // TODO : Add the block as a known inventory of the node that sent it.
      // pfrom->AddInventoryKnown(inv);
      BlockProcessor.validateBlock(block)
      // TODO : Increase DoS score of the peer if the block was invalid.
      // TODO : Make sure we do not hit any issue even though an exception is thrown by validateBlock.

      if (BlockProcessor.hasNonOrphan(block.header.hashPrevBlock)) { // Case 1 : Orphan block
        // Step 1.1 : Store the block to the blockchain. Do block reorganization if necessary.
        val acceptedAsNewBestBlock = BlockProcessor.acceptBlock(blockHash, block)

        // Step 1.2 : Recursively bring any orphan blocks to blockchain, if the new block is the previous block of any orphan block.
        val newlyBestBlockHashes : List[Hash] = BlockProcessor.acceptChildren(block)

        val newBlockHashes = if (acceptedAsNewBestBlock) {
          blockHash :: newlyBestBlockHashes
        } else {
          newlyBestBlockHashes
        }
        // Step 1.3 : Relay the newly added blocks to peers as an inventory if it was the tip of the longest block chain by the time the block was accepted.
        val invMessage = InvFactory.createBlockInventories(newBlockHashes)
        context.communicator.sendToAll(invMessage)
      } else { // Case 2 : Non-Orphan block.
        // Step 2.1 : Put the orphan block
        // BUGBUG : An attacker can fill up my disk with lots of orphan blocks.
        BlockProcessor.putOrphan(block)

        // Step 2.2 : Request the peer to send the root parent of the orphan block.
        val orphanRootHash : Hash = BlockProcessor.getOrphanRoot(blockHash)
        val getBlocksMessage = GetBlocksFactory.create(orphanRootHash)
        context.peer.send(getBlocksMessage)
      }
    }
  }
}
