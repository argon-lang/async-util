package dev.argon.util.async

import zio.Chunk

import scala.scalajs.js.typedarray.*

object TypedArrayUtil {
  def fromByteArray(a: Array[Byte]): Uint8Array = {
    val signed = byteArray2Int8Array(a)
    new Uint8Array(signed.buffer, signed.byteOffset, signed.length)
  }

  def fromByteChunk(chunk: Chunk[Byte]): Uint8Array = fromByteArray(chunk.toArray)
  
  def toByteArray(a: Uint8Array): Array[Byte] =
    int8Array2ByteArray(new Int8Array(a.buffer, a.byteOffset, a.length))
    
  def toByteChunk(a: Uint8Array): Chunk[Byte] = Chunk.fromArray(toByteArray(a))

  def fromShortArray(a: Array[Short]): Uint16Array = {
    val signed = shortArray2Int16Array(a)
    new Uint16Array(signed.buffer, signed.byteOffset, signed.length)
  }

  def fromShortChunk(chunk: Chunk[Short]): Uint16Array = fromShortArray(chunk.toArray)

  def toShortArray(a: Uint16Array): Array[Short] =
    int16Array2ShortArray(new Int16Array(a.buffer, a.byteOffset, a.length))

  def toShortChunk(a: Uint16Array): Chunk[Short] = Chunk.fromArray(toShortArray(a))

  def fromIntArray(a: Array[Int]): Uint32Array = {
    val signed = intArray2Int32Array(a)
    new Uint32Array(signed.buffer, signed.byteOffset, signed.length)
  }

  def fromIntChunk(chunk: Chunk[Int]): Uint32Array = fromIntArray(chunk.toArray)

  def toIntArray(a: Uint32Array): Array[Int] = {
    val signed = new Int32Array(a.buffer, a.byteOffset, a.length)
    int32Array2IntArray(signed)
  }

  def toIntChunk(a: Uint32Array): Chunk[Int] = Chunk.fromArray(toIntArray(a))

  def fromLongArray(a: Array[Long]): BigUint64Array = {
    val signed = new BigInt64Array(a.length)
    for i <- a.indices do
        signed(i) = scala.scalajs.js.BigInt(a(i).toString)
    new BigUint64Array(signed.buffer, signed.byteOffset, signed.length)
  }

  def fromLongChunk(chunk: Chunk[Long]): BigUint64Array = fromLongArray(chunk.toArray)

  def toLongArray(a: BigUint64Array): Array[Long] = {
    val signed = new BigInt64Array(a.buffer, a.byteOffset, a.length)
    val result = new Array[Long](signed.length)
    for i <- result.indices do
      result(i) = signed(i).toString.toLong
    result
  }

  def toLongChunk(a: BigUint64Array): Chunk[Long] = Chunk.fromArray(toLongArray(a))

}
