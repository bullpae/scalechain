package io.scalechain.wallet

import java.io.File

import io.scalechain.blockchain.storage.Storage
import io.scalechain.blockchain.transaction.{ChainTestTrait, ChainEnvironment}
import org.apache.commons.io.FileUtils
import org.scalatest._

import scala.util.Random

class WalletStoreSpec extends FlatSpec with WalletTestTrait with BeforeAndAfterEach with ChainTestTrait with ShouldMatchers
  with WalletStoreAccountTestTrait
  with WalletStoreOutPointTestTrait
  with WalletStoreTransactionHashTestTrait
  with WalletStoreWalletOutputTestTrait // Need to fix the protocol codec exception.
  with WalletStoreWalletTransactionTestTrait {

  this: Suite =>

  var store : WalletStore = null

  if (!Storage.isInitialized)
    Storage.initialize()

  val testPath = new File(s"./target/unittests-WalletStoreSpec-${Random.nextLong}")

  override def beforeEach() {
    super.beforeEach()

    store = new WalletStore()
  }

  override def afterEach() {
    super.afterEach()

    store = null
  }
}
