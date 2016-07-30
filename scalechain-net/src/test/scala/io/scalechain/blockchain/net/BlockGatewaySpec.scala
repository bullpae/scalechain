package io.scalechain.blockchain.net

import java.io.File

import io.scalechain.blockchain.chain.{TransactionSampleData, BlockchainTestTrait}
import io.scalechain.blockchain.proto.{Block, BlockHeader, Hash}
import io.scalechain.blockchain.proto.Hash._
import io.scalechain.blockchain.script.HashSupported
import io.scalechain.blockchain.storage.index.KeyValueDatabase
import io.scalechain.blockchain.transaction.ChainTestTrait
import io.scalechain.util.HexUtil
import io.scalechain.util.PeerAddress
import io.scalechain.util.{PeerAddress, HexUtil}
import HexUtil._
import HashSupported._

import org.scalatest._

/**
  * Source code copied from : https://github.com/ACINQ/bitcoin-lib/blob/master/src/test/scala/fr/acinq/bitcoin/Base58Spec.scala
  * License : Apache v2.
  */
class BlockGatewaySpec extends BlockchainTestTrait with ChainTestTrait with ShouldMatchers {
  this: Suite =>

  val testPath = new File("./target/unittests-BlockGatewaySpec/")

  implicit var keyValueDB : KeyValueDatabase = null

  var bgate : BlockGateway = null

  override def beforeEach() {
    // initialize a test.

    super.beforeEach()

    keyValueDB = db
    assert(keyValueDB != null)

    chain.putBlock( env.GenesisBlockHash, env.GenesisBlock )
    bgate = new BlockGateway()
  }

  override def afterEach() {
    super.afterEach()

    keyValueDB = null
    // finalize a test.
    bgate = null
  }

  def putConsensualHeader(header : BlockHeader) = bgate.putConsensualHeader(header)
  def putBlock(block : Block) = bgate.putReceivedBlock(block.header.hash, block)

  "CH(Consensual Header)1 only" should "not be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)
  }

  "Block1 only" should "not be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)
  }

  "CH(Consensual Header)1, Block1" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)
  }

  "Block1, CH(Consensual Header)1" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK01)
    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)
  }

  "CH(Consensual Header)1, Block1, CH2, Block2" should "be attached to the blockchain" in {

    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "CH(Consensual Header)1, Block1, Block2, CH2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "CH(Consensual Header)1, Block2, Block1, CH2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "CH(Consensual Header)1, Block2, CH2, Block1" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "CH(Consensual Header)1, CH2, Block2, Block1" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "CH(Consensual Header)1, CH2, Block1, Block2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }


  "Block1, CH(Consensual Header)1, CH2, Block2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "Block1, CH(Consensual Header)1, Block2, CH2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "Block1, Block2, CH(Consensual Header)1, CH2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  /*
  "Block1, Block2, CH2, CH(Consensual Header)1" should "be attached to the blockchain" in {
    // This case does not happen. BFT algorithm ensures that CH1 always comes before CH2.
  }
  */

  /*
  "Block1, CH2, Block2, CH(Consensual Header)1" should "be attached to the blockchain" in {
    // This case does not happen. BFT algorithm ensures that CH1 always comes before CH2.
  }
  */

  /*
  "Block1, CH2, CH(Consensual Header)1, Block2" should "be attached to the blockchain" in {
    // This case does not happen. BFT algorithm ensures that CH1 always comes before CH2.
  }
  */

  /*
  "CH2(Consensual Header), CH1, ... " should "be attached to the blockchain" in {

  }
  */

  "Block2, CH(Consensual Header)1, Block1, CH2" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK01.header.hash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  "Block2, CH(Consensual Header)1, CH2, Block1" should "be attached to the blockchain" in {
    val data = new TransactionSampleData()
    import data.Block._

    putBlock(BLK02)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK01.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putConsensualHeader(BLK02.header)
    chain.getBestBlockHash() shouldBe Some(env.GenesisBlockHash)

    putBlock(BLK01)
    chain.getBestBlockHash() shouldBe Some(BLK02.header.hash)
  }

  /*
  "Block2, CH2, CH(Consensual Header)1, Block1" should "be attached to the blockchain" in {
    // This case does not happen. BFT algorithm ensures that CH1 always comes before CH2.
  }
  */

  "Block2, Block1, CH(Consensual Header)1, CH2" should "be attached to the blockchain" in {

  }

  /*
  "Block2, Block1, CH2, CH(Consensual Header)1" should "be attached to the blockchain" in {
    // This case does not happen. BFT algorithm ensures that CH1 always comes before CH2.
  }
  */

}

