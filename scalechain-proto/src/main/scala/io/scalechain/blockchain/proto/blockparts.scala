package io.scalechain.blockchain.proto

import io.scalechain.util
import io.scalechain.util.{Utils, ByteArray, HexUtil, BigIntUtil}
import HexUtil.scalaHex
import ByteArray._
import BigIntUtil._
import spray.json.{JsValue, JsString, RootJsonFormat}

object Hash {
  val ALL_ZERO = Hash("0" * 64)

  def isLessThan(hash1 : Hash, hash2 : Hash): Boolean = {
    val value1 = Utils.bytesToBigInteger(hash1.value)
    val value2 = Utils.bytesToBigInteger(hash2.value)

    if ( value1.compareTo( value2 ) < 0 ) {
      true
    } else {
      false
    }
  }
}
/** A hash case class that can represent transaction hash or block hash.
  * Used by an inventory vector, InvVector.
  *
  * @param value
  */
case class Hash(value : ByteArray) extends ProtocolMessage {
  def isAllZero() = {
    var i = 0
    val valueLength = value.length
    val array = value.array
    while (i < valueLength && array(i) == 0) {
      i += 1
    }
    i == valueLength
  }

  def toHex() : String = value.toString

  override def toString() = s"Hash($value)"
}
object HashFormat {
  implicit object hashFormat extends RootJsonFormat[Hash] {
    // Instead of { value : "cafebebe" }, we need to serialize the hash to "cafebebe"
    def write(hash : Hash) = JsString( ByteArray.byteArrayToString(hash.value) )

    // Not used.
    def read(value:JsValue) = {
      assert(false)
      null
    }
  }
}


object BlockHeader {
  /** Get the encoded difficulty bits to put into the block header from the minimum block hash.
    *
    * For encoding/decoding the difficulty bits in the block header, see the following link.
    *
    * https://en.bitcoin.it/wiki/Difficulty
    *
    * @param minBlockHash The minimum block hash.
    * @return The encoded difficulty. ( 4 byte integer )
    */
  def encodeDifficulty(minBlockHash : Hash) : Long = {
    // TODO : Implement
    assert(false)
    -1L
  }

  /** Get the minimum block hash from the encoded difficulty bits.
    *
    * For encoding/decoding the difficulty bits in the block header, see the following link.
    *
    * @param target
    * @return
    */
  def decodeDifficulty(target : Long) : Hash = {
    // TODO : Implement
    assert(false)
    null
  }
}

case class BlockHeader(version : Int, hashPrevBlock : Hash, hashMerkleRoot : Hash, timestamp : Long, target : Long, nonce : Long)  extends ProtocolMessage {
  override def toString() : String = {
    s"BlockHeader(version=$version, hashPrevBlock=$hashPrevBlock, hashMerkleRoot=$hashMerkleRoot, timestamp=${timestamp}L, target=${target}L, nonce=${nonce}L)"
  }
}

case class CoinbaseData(data: ByteArray) extends ProtocolMessage {
  override def toString() : String = {
    s"CoinbaseData($data)"
  }
}

trait TransactionInput extends ProtocolMessage {
  val outputTransactionHash : Hash
  val outputIndex : Long

  def getOutPoint() = OutPoint(
    outputTransactionHash,
    outputIndex.toInt
  )

  def isCoinBaseInput() = {
    outputTransactionHash.isAllZero()
  }
}

case class NormalTransactionInput(override val outputTransactionHash : Hash,
                                  override val outputIndex : Long,
                                  val unlockingScript : UnlockingScript,
                                  val sequenceNumber : Long) extends TransactionInput {
  override def toString(): String = {
    s"NormalTransactionInput(outputTransactionHash=$outputTransactionHash, outputIndex=${outputIndex}L, unlockingScript=$unlockingScript, sequenceNumber=${sequenceNumber}L)"
  }
}

case class GenerationTransactionInput(override val outputTransactionHash : Hash,
                                      // BUGBUG : Change to Int
                                      override val outputIndex : Long,
                                      val coinbaseData : CoinbaseData,
                                      val sequenceNumber : Long) extends TransactionInput {
  override def toString(): String = {
    s"GenerationTransactionInput(outputTransactionHash=$outputTransactionHash, outputIndex=${outputIndex}L, coinbaseData=$coinbaseData, sequenceNumber= ${sequenceNumber}L)"
  }
}

class Script(val data:ByteArray) extends ProtocolMessage
{
  def length = data.length
  def apply(i:Int) = data.apply(i)
}

trait LockingScriptPrinter {
  def toString(lockingScript:LockingScript) : String
}
object LockingScript {
  var printer : LockingScriptPrinter = null
}
case class LockingScript(override val data:ByteArray) extends Script(data) {
  override def toString(): String = {
    if (LockingScript.printer != null)
      LockingScript.printer.toString(this)
    else
      s"LockingScript($data)"
  }
}

trait UnlockingScriptPrinter {
  def toString(unlockingScript:UnlockingScript) : String
}
object UnlockingScript {
  var printer : UnlockingScriptPrinter = null
}
case class UnlockingScript(override val data:ByteArray) extends Script(data) {
  override def toString(): String = {
    if (UnlockingScript.printer != null)
      UnlockingScript.printer.toString(this)
    else
      s"UnlockingScript($data)"
  }
}

case class TransactionOutput(value : Long, lockingScript : LockingScript) extends ProtocolMessage {
  override def toString(): String = {
    s"TransactionOutput(value=${value}L, lockingScript=$lockingScript)"
  }
}

trait TransactionPrinter {
  def toString(transaction:Transaction) : String
}
object Transaction {
  var printer : TransactionPrinter = null
}

/** Tx ; tx describes a bitcoin transaction, in reply to getdata.
  */
case class Transaction(val version : Int,
                       val inputs : List[TransactionInput],
                       val outputs : List[TransactionOutput],
                       val lockTime : Long) extends ProtocolMessage {

  override def toString() : String = {
    if (Transaction.printer != null)
      Transaction.printer.toString(this)
    else
      s"Transaction(version=$version, inputs=List(${inputs.mkString(",")}), outputs=List(${outputs.mkString(",")}), lockTime=${lockTime}L)"
  }
}

object Block {
  // Need to move these to configurations.
  val MAX_SIZE = 1024 * 1024
}

/** The block message is sent in response to a getdata message
  * which requests transaction information from a block hash.
  */
case class Block(val header:BlockHeader,
                 val transactions : List[Transaction]) extends ProtocolMessage {


  override def toString() : String = {
    s"Block(header=$header, transactions=List(${transactions.mkString(",")}))"
  }
}

/** IPv6 address. Network byte order.
  * The original client only supported IPv4 and only read the last 4 bytes to get the IPv4 address.
  * However, the IPv4 address is written into the message as a 16 byte IPv4-mapped IPv6 address
  */
case class IPv6Address(address:ByteArray) extends ProtocolMessage {
  override def toString() : String = {
    s"IPv6Address($address)"
  }
  def inetAddress = com.google.common.net.InetAddresses.fromLittleEndianByteArray(address.array.reverse)
}

// TODO : Add a comment
case class NetworkAddress(services:BigInt, ipv6:IPv6Address, port:Int) extends ProtocolMessage {
  override def toString() : String = {
    s"NetworkAddress(${bint(services)}, $ipv6, $port)"
  }
}
// TODO : Add a comment
case class NetworkAddressWithTimestamp(timestamp:Long, address:NetworkAddress) extends ProtocolMessage {
  override def toString() : String = {
    s"NetworkAddressWithTimestamp(${timestamp}L, $address)"
  }
}

