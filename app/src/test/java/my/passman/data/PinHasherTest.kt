package my.passman.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun hash_usesArgon2idFormat() {
        val encoded = PinHasher.hash("1234")

        assertTrue(encoded.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1$"))
        assertFalse(PinHasher.isLegacy(encoded))
    }

    @Test
    fun hash_isSaltedPerCall() {
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test
    fun verify_acceptsCorrectPin() {
        val encoded = PinHasher.hash("482915")

        assertTrue(PinHasher.verify("482915", encoded))
    }

    @Test
    fun verify_rejectsWrongPin() {
        val encoded = PinHasher.hash("482915")

        assertFalse(PinHasher.verify("482916", encoded))
    }

    @Test
    fun verify_rejectsMalformedHash() {
        assertFalse(PinHasher.verify("1234", "\$bcrypt\$whatever"))
        assertFalse(PinHasher.verify("1234", "\$argon2id\$v=19\$m=oops\$c2FsdA==\$aGFzaA=="))
    }

    @Test
    fun verify_acceptsLegacySha256Hash() {
        // SHA-256("1234"), as stored before the switch to Argon2.
        val legacy = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"

        assertTrue(PinHasher.isLegacy(legacy))
        assertTrue(PinHasher.verify("1234", legacy))
        assertFalse(PinHasher.verify("4321", legacy))
    }
}
