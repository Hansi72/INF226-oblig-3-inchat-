package inf226.inchat;

import com.lambdaworks.crypto.SCrypt;
import inf226.storage.*;
import inf226.util.Maybe;
import inf226.util.Util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.TreeMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;
import java.time.Instant;
import java.sql.SQLException;
import java.sql.Connection;


import inf226.util.immutable.List;

/**
 * This class models the chat logic.
 *
 * It provides an abstract interface to
 * usual chat server actions.
 *
 **/

public class InChat {
    private final Connection connection;
    private final UserStorage userStore;
    private final ChannelStorage channelStore;
    private final EventStorage   eventStore;
    private final AccountStorage accountStore;
    private final SessionStorage sessionStore;
    private final Map<UUID,List<Consumer<Channel.Event>>> eventCallbacks
        = new TreeMap<UUID,List<Consumer<Channel.Event>>>();

    public InChat(UserStorage userStore,
                  ChannelStorage channelStore,
                  AccountStorage accountStore,
                  SessionStorage sessionStore,
                  Connection     connection) {
        this.userStore=userStore;
        this.channelStore=channelStore;
        this.eventStore=channelStore.eventStore;
        this.accountStore=accountStore;
        this.sessionStore=sessionStore;
        this.connection=connection;
    }

    /**
     * An atomic operation in Inchat.
     * An operation has a function run(), which returns its
     * result through a consumer.
     */
    @FunctionalInterface
    private interface Operation<T,E extends Throwable> {
        void run(final Consumer<T> result) throws E,DeletedException;
    }
    /**
     * Execute an operation atomically in SQL.
     * Wrapper method for commit() and rollback().
     */
    private<T> Maybe<T> atomic(Operation<T,SQLException> op) {
        synchronized (connection) {
            try {
                Maybe.Builder<T> result = Maybe.builder();
                op.run(result);
                connection.commit();
                return result.getMaybe();
            }catch (SQLException e) {
                System.err.println(e.toString());
            }catch (DeletedException e) {
                System.err.println(e.toString());
            }
            try {
                connection.rollback();
            } catch (SQLException e) {
                System.err.println(e.toString());
            }
            return Maybe.nothing();
        }
    }

    /**
     * Log in a user to the chat.
     */
    public Maybe<Stored<Session>> login(final String username,
                                        final String password) {
        return atomic(result -> {
            final Stored<Account> account = accountStore.lookup(username);
            final Stored<Session> session =
                sessionStore.save(new Session(account, Instant.now().plusSeconds(60*60*24)));
            // Check that password is not incorrect and not too long.
                 if (account.value.checkPassword(password) && !(password.length() > 1000)) {
                     result.accept(session);
             }
        });
    }
    
    /**
     * Register a new user.
     */
    public Maybe<Stored<Session>> register(final String username,
                                           final String password) {
        return atomic(result -> {
            final Stored<User> user =
                userStore.save(User.create(username));
            final Stored<Account> account =
                accountStore.save(Account.create(user, password));
            final Stored<Session> session =
                sessionStore.save(new Session(account, Instant.now().plusSeconds(60*60*24)));
            result.accept(session); 
        });
    }
    
    /**
     * Restore a previous session.
     */
    public Maybe<Stored<Session>> restoreSession(UUID sessionId) {
        return atomic(result ->
            result.accept(sessionStore.get(sessionId))
        );
    }
    
    /**
     * Log out and invalidate the session.
     */
    public void logout(Stored<Session> session) {
        atomic(result ->
            Util.deleteSingle(session,sessionStore));
    }
    
    /**
     * Create a new channel.
     */
    public Maybe<Stored<Channel>> createChannel(Stored<Account> account,
                                                String name) {
        return atomic(result -> {
            Stored<Channel> channel
                = channelStore.save(new Channel(name,List.empty()));
            joinChannel(account, channel.identity);
            result.accept(channel);
        });
    }
    
    /**
     * Join a channel.
     */
    public Maybe<Stored<Channel>> joinChannel(Stored<Account> account,
                                              UUID channelID) {
        return atomic(result -> {
            Stored<Channel> channel = channelStore.get(channelID);
            Util.updateSingle(account,
                              accountStore,
                              a -> a.value.joinChannel(channel.value.name,channel));
            Stored<Channel.Event> joinEvent
                = channelStore.eventStore.save(
                    Channel.Event.createJoinEvent(channelID,
                        Instant.now(),
                        account.value.user.value.name));
            result.accept(
                Util.updateSingle(channel,
                                  channelStore,
                                  c -> c.value.postEvent(joinEvent)));
        });
    }
    
    /**
     * Post a message to a channel.
     */
    public Maybe<Stored<Channel>> postMessage(Stored<Account> account,
                                              Stored<Channel> channel,
                                              String message) {
        return atomic(result -> {
            Stored<Channel.Event> event
                = channelStore.eventStore.save(
                    Channel.Event.createMessageEvent(channel.identity,Instant.now(),
                        account.value.user.value.name, message));
            result.accept (
                Util.updateSingle(channel,
                                    channelStore,
                                    c -> c.value.postEvent(event)));
        });
    }
    
    /**
     * A blocking call which returns the next state of the channel.
     */
    public Maybe<Stored<Channel>> waitNextChannelVersion(UUID identity, UUID version) {
        try{
            return Maybe.just(channelStore.waitNextVersion(identity, version));
        } catch (DeletedException e) {
            return Maybe.nothing();
        } catch (SQLException e) {
            return Maybe.nothing();
        }
    }
    
    /**
     * Get an event by its identity.
     */
    public Maybe<Stored<Channel.Event>> getEvent(UUID eventID) {
        return atomic(result ->
            result.accept(channelStore.eventStore.get(eventID))
        );
    }
    
    /**
     * Delete an event.
     */
    public Stored<Channel> deleteEvent(Stored<Channel> channel, Stored<Channel.Event> event) {
        return this.<Stored<Channel>>atomic(result -> {
            Util.deleteSingle(event , channelStore.eventStore);
            result.accept(channelStore.noChangeUpdate(channel.identity));
        }).defaultValue(channel);
    }

    /**
     * Edit a message.
     */
    public Stored<Channel> editMessage(Stored<Channel> channel,
                                       Stored<Channel.Event> event,
                                       String newMessage) {
        return this.<Stored<Channel>>atomic(result -> {
            Util.updateSingle(event,
                            channelStore.eventStore,
                            e -> e.value.setMessage(newMessage));
            result.accept(channelStore.noChangeUpdate(channel.identity));
        }).defaultValue(channel);
    }
}


