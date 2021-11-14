package inf226.inchat;
import inf226.util.immutable.List;
import inf226.util.Pair;

import com.lambdaworks.crypto.SCrypt;
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
    public final Password password;
    public final byte[] salt;

    public Account(final Stored<User> user,
                   final List<Pair<String,Stored<Channel>>> channels,
                   final Password password, final byte[] salt) {
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
                                 final String password){
        SecureRandom rand = new SecureRandom();
        byte salt[] = new byte[32];
        rand.nextBytes(salt);

            Password passwordObj = new Password(password, salt, user.value.name.getUserName());
            return new Account(user, List.empty(), passwordObj, salt);

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
     * Check whether if a string is a correct password for
     * this account.
     *
     * @return true if password matches.
     */
    public boolean checkPassword(String password) {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] hashedPassword = SCrypt.scrypt(passwordBytes, salt, 16384, 16, 1, 256);
            //fixme, quickfix to ignore null passwords because of incomplete code in Password class.
            if(password == null){
                return false;
            }
            return Arrays.equals(hashedPassword, this.password.getPassword());
        } catch (GeneralSecurityException err) {
            err.printStackTrace();
        }
        return false;
    }



    
}
