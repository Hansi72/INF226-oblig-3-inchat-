package inf226.inchat;


import inf226.storage.*;
import inf226.util.Maybe;
import inf226.util.Util;

import java.util.HashMap;
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
 * <p>
 * It provides an abstract interface to
 * usual chat server actions.
 **/

public class InChat {
    private final Connection connection;
    private final UserStorage userStore;
    private final ChannelStorage channelStore;
    private final EventStorage eventStore;
    private final AccountStorage accountStore;
    private final SessionStorage sessionStore;
    private final Map<UUID, List<Consumer<Channel.Event>>> eventCallbacks
            = new TreeMap<UUID, List<Consumer<Channel.Event>>>();

    public InChat(UserStorage userStore,
                  ChannelStorage channelStore,
                  AccountStorage accountStore,
                  SessionStorage sessionStore,
                  Connection connection) {
        this.userStore = userStore;
        this.channelStore = channelStore;
        this.eventStore = channelStore.eventStore;
        this.accountStore = accountStore;
        this.sessionStore = sessionStore;
        this.connection = connection;
    }


    /**
     * An atomic operation in Inchat.
     * An operation has a function run(), which returns its
     * result through a consumer.
     */
    @FunctionalInterface
    private interface Operation<T, E extends Throwable> {
        void run(final Consumer<T> result) throws E, DeletedException;
    }

    /**
     * Execute an operation atomically in SQL.
     * Wrapper method for commit() and rollback().
     */
    private <T> Maybe<T> atomic(Operation<T, SQLException> op) {
        synchronized (connection) {
            try {
                Maybe.Builder<T> result = Maybe.builder();
                op.run(result);
                connection.commit();
                return result.getMaybe();
            } catch (SQLException e) {
                System.err.println(e.toString());
            } catch (DeletedException e) {
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
                    sessionStore.save(new Session(account, Instant.now().plusSeconds(60 * 60 * 24)));
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
            if (validatePassword(username, password)) {
                final Stored<User> user =
                        userStore.save(User.create(username));
                final Stored<Account> account =
                        accountStore.save(Account.create(user, password));
                final Stored<Session> session =
                        sessionStore.save(new Session(account, Instant.now().plusSeconds(60 * 60 * 24)));
                result.accept(session);
            }
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
                Util.deleteSingle(session, sessionStore));
    }

    /**
     * Create a new channel.
     */
    public Maybe<Stored<Channel>> createChannel(Stored<Account> account,
                                                String name) {
        HashMap<String, String> roles = new HashMap();
        roles.put(account.value.user.value.name.getUserName(), "owner");
        return atomic(result -> {
            Stored<Channel> channel
                    = channelStore.save(new Channel(name, List.empty(), roles));
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
            //quickfix sets user to participant if not already owner, banned or observer.
            String currentRole = channel.value.roles.get(account.value.user.value.name.getUserName());
            if (currentRole != null) {
                if (!(currentRole.equals("owner") || currentRole.equals("banned") || currentRole.equals("observer"))) {
                    channel.value.roles.put(account.value.user.value.name.getUserName(), "participant");
                }
            } else {
                channel.value.roles.put(account.value.user.value.name.getUserName(), "participant");
            }
            Util.updateSingle(account,
                    accountStore,
                    a -> a.value.joinChannel(channel.value.name, channel));
            Stored<Channel.Event> joinEvent
                    = channelStore.eventStore.save(
                    Channel.Event.createJoinEvent(channelID,
                            Instant.now(),
                            account.value.user.value.name.getUserName()));
            result.accept(
                    Util.updateSingle(channel,
                            channelStore,
                            c -> c.value.postEvent(joinEvent)));
        });
    }

    /**
     * Post a message to a channel.
     * Permission handled in handler.
     */
    public Maybe<Stored<Channel>> postMessage(Stored<Account> account,
                                              Stored<Channel> channel,
                                              String message) {
        return atomic(result -> {
            Stored<Channel.Event> event
                    = channelStore.eventStore.save(
                    Channel.Event.createMessageEvent(channel.identity, Instant.now(),
                            account.value.user.value.name.getUserName(), message));
            result.accept(
                    Util.updateSingle(channel,
                            channelStore,
                            c -> c.value.postEvent(event)));
        });
    }

    /**
     * A blocking call which returns the next state of the channel.
     */
    public Maybe<Stored<Channel>> waitNextChannelVersion(UUID identity, UUID version) {
        try {
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
    public Stored<Channel> deleteEvent(Stored<Channel> channel, Stored<Channel.Event> event, Stored<Account> account) {
        String role = getRole(account.value.user.value.name.getUserName(), channel);
        if (role.equals("owner") || role.equals("moderator")) {
        } else if (event.value.sender.equals(account.value.user.value.name.getUserName()) && role.equals("participant")) {
        } else {
            return channel;
        }

        return this.<Stored<Channel>>atomic(result -> {
            Util.deleteSingle(event, channelStore.eventStore);
            result.accept(channelStore.noChangeUpdate(channel.identity));
        }).defaultValue(channel);
    }

    /**
     * Edit a message.
     */
    public Stored<Channel> editMessage(Stored<Channel> channel,
                                       Stored<Channel.Event> event,
                                       String newMessage, Stored<Account> account) {
        String role = getRole(account.value.user.value.name.getUserName(), channel);
        if (role.equals("owner") || role.equals("moderator")) {
        } else if (event.value.sender.equals(account.value.user.value.name.getUserName()) && role.equals("participant")) {
        } else {
            return channel;
        }

        return this.<Stored<Channel>>atomic(result -> {
            Util.updateSingle(event,
                    channelStore.eventStore,
                    e -> e.value.setMessage(newMessage));
            result.accept(channelStore.noChangeUpdate(channel.identity));
        }).defaultValue(channel);
    }

    //fixme add atomic util.updateSingle here?
    public Stored<Channel> setRole(Stored<Account> account, final Stored<Channel> channel, String targetUser, String role) {
        System.out.println("setrole start");
        if (!(getRole(targetUser, channel).equals("owner"))) {
            System.out.println("target is not owner");
            if (getRole(account.value.user.value.name.getUserName(), channel).equals("owner")) {
                System.out.println("setrole put: " + role + " on user " + targetUser);
                return channel;
            }
        }
        return channel;
    }

    //get role for given user on a channel.
    public String getRole(String name, Stored<Channel> channel) {
        if (channel.value.roles.get(name) != null) {
            return channel.value.roles.get(name);
        }
        return "none";
    }

    public boolean canPost(Stored<Account> account, Stored<Channel> channel) {
        String role = getRole(account.value.user.value.name.getUserName(), channel);
        if (role.equals("owner") || role.equals("moderator") || role.equals("participant")) {
            return true;
        } else {
            return false;
        }
    }

    public boolean readPermission(Stored<Account> account, Stored<Channel> channel) {
        String role = getRole(account.value.user.value.name.getUserName(), channel);
        if (role.equals("owner") || role.equals("moderator") || role.equals("participant") || role.equals("observer")) {
            return true;
        } else {
            return false;
        }
    }

    //Validate NIST password restrictions
    private boolean validatePassword(String password, String userName) {
        if (password.length() < 7) {
            return false;
        }
        if (password.length() > 256) {
            return false;
        }
        if (password.toLowerCase().contains(userName.toLowerCase())) {
            return false;
        }
        if (password.toLowerCase().contains("inchat")) {
            return false;
        }
        if (password.toLowerCase().contains("password")) {
            return false;
        }
        if (password.contains(" ")) {
            return false;
        }
        //todo legg til flere NIST restriksjoner (eks: gjentagende tegn, og sjekk opp mot hashmap av 'dårlige' passord)
        return true;
    }

}


