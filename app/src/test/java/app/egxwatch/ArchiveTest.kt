package app.egxwatch
import app.egxwatch.data.MarketArchive
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.*
import javax.crypto.spec.*
class ArchiveTest {
 private fun decrypt(bytes:ByteArray,password:String):ByteArray {
  val header=bytes.copyOfRange(0,40);val iterations=ByteBuffer.wrap(header,8,4).int
  val key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(),header.copyOfRange(12,28),iterations,256)).encoded
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,header.copyOfRange(28,40)));cipher.updateAAD(header)
  return cipher.doFinal(bytes.copyOfRange(40,bytes.size))
 }
 @Test fun authenticatedRoundTripAndTampering() {
  val password="test password only";val chars=password.toCharArray();val output=ByteArrayOutputStream()
  MarketArchive.write(output,chars) { it.write("private market database".toByteArray()) }
  val bytes=output.toByteArray();assertTrue(chars.all { it=='\u0000' })
  assertEquals("private market database",String(decrypt(bytes,password)))
  assertThrows(Exception::class.java) { decrypt(bytes,"wrong password") }
  bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
  assertThrows(Exception::class.java) { decrypt(bytes,password) }
 }
}
