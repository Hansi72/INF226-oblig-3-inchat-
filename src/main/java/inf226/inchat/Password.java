package inf226.inchat;

import com.lambdaworks.crypto.SCrypt;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

//todo do Serializables create a security risk?
public final class Password implements Serializable {
    private final byte[] password;

    public Password(final String password, final byte[] salt, String userName) {
        validate(password, userName); //todo handle error
        SCrypt scrypt = new SCrypt();

        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_16);
        byte[] tempPassword;
        try {
            byte[] hashedPassword = SCrypt.scrypt(passwordBytes, salt, 16384, 16, 1, 256);
            tempPassword = hashedPassword;
        } catch (GeneralSecurityException secErr) {
            //fixme throw error to the handler instead. (quickfixed to ignore Null passwords implemented in CheckPassword())
            tempPassword = null;
        }
        this.password = tempPassword;

    }

    public byte[] getPassword(){
        return password;
    }

    //Validate NIST password restrictions
    private boolean validate(String password, String userName){
        assert(password.length() > 7);
        assert(password.length() < 256);
        assert(!password.toLowerCase().contains(userName.toLowerCase()));
        assert(!password.toLowerCase().contains("inchat"));
        assert(!password.toLowerCase().contains("password"));
        //todo legg til NIST restriksjoner her når du er sletn i hodet
        return false;
    }
}
