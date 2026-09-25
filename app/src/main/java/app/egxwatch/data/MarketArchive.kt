package app.egxwatch.data

import java.io.*
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.*

/** Portable authenticated archive. Password never persists; plaintext never touches shared storage. */
object MarketArchive {
 private val magic="EGXENC01".toByteArray(Charsets.US_ASCII)
 fun write(output:OutputStream,password:CharArray,body:(OutputStream)->Unit) {
  require(password.size>=12) { "Use at least 12 characters" }
  val random=SecureRandom();val salt=ByteArray(16).also(random::nextBytes);val iv=ByteArray(12).also(random::nextBytes)
  val iterations=210000;val header=ByteArrayOutputStream().apply { write(magic);DataOutputStream(this).writeInt(iterations);write(salt);write(iv) }.toByteArray()
  val spec=PBEKeySpec(password,salt,iterations,256)
  val bytes=try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword();password.fill('\u0000') }
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  try { cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(bytes,"AES"),GCMParameterSpec(128,iv)) } finally { bytes.fill(0) }
  cipher.updateAAD(header);output.write(header)
  CipherOutputStream(output,cipher).use(body)
 }
}
