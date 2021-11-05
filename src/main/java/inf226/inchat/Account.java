package inf226.inchat;
import inf226.util.immutable.List;
import inf226.util.Pair;

import com.lambdaworks.crypto.SCrypt;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import inf226.storage.*;

/**
 * The Account class holds all information private to
 * a specific user.
 **/
public final class Account {
    /*
     * A channel consists of a User object of public account info,
     * and a list of channels which the user can post to.
     */
    public final Stored<User> user;
    public final List<Pair<String,Stored<Channel>>> channels;
    public final byte[] password;
    public final byte[] salt;

    public Account(final Stored<User> user,
                   final List<Pair<String,Stored<Channel>>> channels,
                   final byte[] password, final byte[] salt) {
        this.user = user;
        this.channels = channels;
        this.password = password;
        this.salt = salt;
    }
    
    /**
     * Create a new Account.
     *
     * @param user The public User profile for this user.
     * @param password The login password for this account.
     **/
    public static Account create(final Stored<User> user,
                                 final String password) {
        SecureRandom rand = new SecureRandom();
        byte salt[] = new byte[32];
        rand.nextBytes(salt);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_16);
        SCrypt scrypt = new SCrypt();
        try {
            byte[] hashedPassword = SCrypt.scrypt(passwordBytes, salt, 16384, 16, 1, 256);
            return new Account(user,List.empty(), hashedPassword, salt);
        } catch (GeneralSecurityException err) {
            //todo
        }
            return null; //todo hva er konsekvensene av denne?
    }
    
    /**
     * Join a channel with this account.
     *
     * @return A new account object with the channel added.
     */
    public Account joinChannel(final String alias,
                               final Stored<Channel> channel) {
        Pair<String,Stored<Channel>> entry
            = new Pair<String,Stored<Channel>>(alias,channel);
        return new Account
                (user,
                 List.cons(entry,
                           channels),
                 password, salt);
    }


    /**
     * Check weather if a string is a correct password for
     * this account.
     *
     * @return true if password matches.
     */
    public boolean checkPassword(String password) {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_16);
        try {
            byte[] hashedPassword = SCrypt.scrypt(passwordBytes, salt, 16384, 16, 1, 256);
            return Arrays.equals(this.password, hashedPassword);
        } catch (GeneralSecurityException err) {
            //todo
        }





        return false;
    }



    
}
