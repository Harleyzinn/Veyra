package app.veyra.data
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import android.util.Base64
import org.json.JSONObject

object BackupCrypto {
    private fun encode(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.NO_WRAP)
    private fun decode(s:String)=Base64.decode(s,Base64.NO_WRAP)
    private fun key(password:String,salt:ByteArray)=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(),salt,210000,256)).encoded,"AES")
    fun encrypt(text:String,password:String):String {
        require(password.length>=8){"Use uma senha de pelo menos 8 caracteres"}
        val random=SecureRandom();val salt=ByteArray(16).also{random.nextBytes(it)};val iv=ByteArray(12).also{random.nextBytes(it)}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv))
        return JSONObject().put("encrypted",1).put("salt",encode(salt)).put("iv",encode(iv)).put("data",encode(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))).toString()
    }
    fun decrypt(text:String,password:String):String {
        val o=JSONObject(text);require(o.getInt("encrypted")==1)
        val salt=decode(o.getString("salt"));val iv=decode(o.getString("iv"));require(salt.size==16 && iv.size==12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv))
        return cipher.doFinal(decode(o.getString("data"))).toString(Charsets.UTF_8)
    }
}
