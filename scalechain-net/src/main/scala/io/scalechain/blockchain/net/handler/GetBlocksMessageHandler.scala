package io.scalechain.blockchain.net.handler

import com.typesafe.scalalogging.Logger
import io.scalechain.blockchain.chain.{BlockLocatorHashes, Blockchain, BlockLocator}
import io.scalechain.blockchain.net.MessageSummarizer
import io.scalechain.blockchain.net.message.InvFactory
import io.scalechain.blockchain.proto.{GetBlocks, ProtocolMessage, GetData}
import org.slf4j.LoggerFactory

/**
  * The message handler for GetBlocks message.
  */
object GetBlocksMessageHandler {
  val MAX_HASH_PER_REQUEST = 500
  private lazy val logger = Logger( LoggerFactory.getLogger(GetBlocksMessageHandler.getClass) )

  /** Handle GetBlocks message.
    *
    * @param context The context where handlers handling different messages for a peer can use to store state data.
    * @param getBlocks The GetBlocks message to handle.
    * @return Some(message) if we need to respond to the peer with the message.
    */
  def handle(context: MessageHandlerContext, getBlocks: GetBlocks): Unit = {
    // TODO : Investigate : Need to understand : GetDistanceBack returns the depth(in terms of the sender's blockchain) of the block that is in our main chain. It returns 0 if the tip of sender's branch is in our main chain. We will send up to 500 more blocks from the tip height of the sender's chain.

    // Step 1 : Get the list of block hashes to send.
    val locator = new BlockLocator(Blockchain.get)

    // During block reorganization, transactions/blocks are attached/detached.
    // We need to synchronize with block reorganization, as getblocks message depends on a 'consistent' view of the best blockchain.
    // getblocks message should not see any inconsistent state of the best blockchain while block reorganization is in-progress.
    val blockHashes =
    Blockchain.get.synchronized {

      // Step 2 : Skip the common block, start building the list of block hashes from the next block of the common block.
      //          Stop constructing the block hashes if we hit the count limit, 500. GetBlocks sends up to 500 block hashes.
      locator.getHashes(BlockLocatorHashes(getBlocks.blockLocatorHashes), getBlocks.hashStop, maxHashCount = MAX_HASH_PER_REQUEST)
    }

    // TODO : BUGBUG : Bitcoin Core compatibility - Need to drop the last hash if it matches getBlocks.hashStop.
    val filteredBlockHashes = blockHashes
    // Step 3 : Remove the hashStop if it is the last element of the list. GetBlocks does not send the hashStop block as an Inv.
/*
      if (blockHashes.lastOption.isDefined && blockHashes.lastOption.get == getBlocks.hashStop) {
        blockHashes.dropRight(1)
      } else {
        blockHashes
      }
*/

    // Step 4 : Pack the block hashes into an Inv message, and reply it to the requester.
    if (filteredBlockHashes.isEmpty) {
      // Do nothing. Nothing to send.
      logger.trace(s"Nothing to send in response to getblocks message.")
    } else {
      val invMessage = InvFactory.createBlockInventories(filteredBlockHashes)
      context.peer.send(invMessage)
      logger.trace(s"Sending inventories in response to getblocks message. ${MessageSummarizer.summarize(invMessage)}")
    }
  }
}