package inf226.inchat;

import com.lambdaworks.crypto.SCrypt;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

//todo do Serializables create a security risk when deserializing?
public final class Password implements Serializable {
    private final byte[] password;

    public Password(final String password, final byte[] salt, String userName) {
        validate(password, userName); //todo handle error og implementer stopp invalid passord
        System.out.println("");

        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
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
        //todo legg til flere NIST restriksjoner (eks: gjentagende tegn, og sjekk opp mot hashmap av 'dårlige' passord)
        return false;
    }
}
