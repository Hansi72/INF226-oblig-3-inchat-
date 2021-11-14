package inf226.inchat;

import com.lambdaworks.crypto.SCrypt;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

//todo do Serializables create a security risk when deserializing?
public final class Password implements Serializable {
    private final byte[] password;

    public Password(final String password, final byte[] salt, String userName) {

        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] tempPassword;
        try {
            byte[] hashedPassword = SCrypt.scrypt(passwordBytes, salt, 16384, 16, 1, 256);
            tempPassword = hashedPassword;
        } catch (GeneralSecurityException secErr) {
            tempPassword = null;  //fixme quickfixed to decline Null passwords implemented in CheckPassword()
        }
        this.password = tempPassword;
    }

    public byte[] getPassword() {
        return password;
    }

}
