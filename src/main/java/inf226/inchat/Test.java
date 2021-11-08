package inf226.inchat;

import com.lambdaworks.crypto.SCrypt;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;


public class Test {

    public static void main(String[] args) {

        SecureRandom rand = new SecureRandom();

        byte[] hash;
        byte salt[] = new byte[32];
        rand.nextBytes(salt);
        String password = "password";
        byte[] bytepass = password.getBytes();

        try {
            SCrypt scrypt = new SCrypt();
            hash = scrypt.scrypt(bytepass, salt, 16384, 16, 1, 256);
            System.out.println("rand bytes: " + salt);
            System.out.println("hash generated: " + hash);
        } catch (GeneralSecurityException err) {

        }


    }



}

//    key = SCrypt(byte[] passord, byte[] salt, int N, int r, int p, int dkLen);
//            Parameters:
//            passwd - Password.
//                    salt - Salt.
//                    N - CPU cost parameter.
//            r - Memory cost parameter.
//            p - Parallelization parameter.
//                    dkLen - Intended length of the derived key.