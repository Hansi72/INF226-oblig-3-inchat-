package inf226.inchat;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import inf226.storage.*;

import inf226.util.immutable.List;
import inf226.util.*;

/**
 * This class stores accounts in the database.
 */
public final class AccountStorage
    implements Storage<Account,SQLException> {
    
    final Connection connection;
    final Storage<User,SQLException> userStore;
    final Storage<Channel,SQLException> channelStore;
   
    /**
     * Create a new account storage.
     *
     * @param  connection   The connection to the SQL database.
     * @param  userStore    The storage for User data.
     * @param  channelStore The storage for channels.
     */
    public AccountStorage(Connection connection,
                          Storage<User,SQLException> userStore,
                          Storage<Channel,SQLException> channelStore) 
      throws SQLException {
        this.connection = connection;
        this.userStore = userStore;
        this.channelStore = channelStore;
        
        connection.createStatement()
                .executeUpdate("CREATE TABLE IF NOT EXISTS Account (id TEXT PRIMARY KEY, version TEXT, user TEXT, password VARBINARY(8000), salt VARBINARY(8000), FOREIGN KEY(user) REFERENCES User(id) ON DELETE CASCADE)");
        connection.createStatement()
                .executeUpdate("CREATE TABLE IF NOT EXISTS AccountChannel (account TEXT, channel TEXT, alias TEXT, ordinal INTEGER, PRIMARY KEY(account,channel), FOREIGN KEY(account) REFERENCES Account(id) ON DELETE CASCADE, FOREIGN KEY(channel) REFERENCES Channel(id) ON DELETE CASCADE)");
    }
    
    @Override
    public Stored<Account> save(Account account)
      throws SQLException {
        
        final Stored<Account> stored = new Stored<Account>(account);

        String sql = "INSERT INTO Account VALUES(?,?,?,?,?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setObject(1,stored.identity);
        preparedStatement.setObject(2, stored.version);
        preparedStatement.setObject(3, account.user.identity);
        preparedStatement.setBytes(4,account.password);
        preparedStatement.setBytes(5,account.salt);

        preparedStatement.executeUpdate();

        
        // Write the list of channels
        final Maybe.Builder<SQLException> exception = Maybe.builder();
        final Mutable<Integer> ordinal = new Mutable<Integer>(0);
        account.channels.forEach(element -> {
            String alias = element.first;
            Stored<Channel> channel = element.second;
            final String msql
              = "INSERT INTO AccountChannel VALUES('" + stored.identity + "','"
                                                      + channel.identity + "','"
                                                      + alias + "','"
                                                      + ordinal.get().toString() + "')";
            try { connection.createStatement().executeUpdate(msql); }
            catch (SQLException e) { exception.accept(e) ; }
            ordinal.accept(ordinal.get() + 1);
        });

        Util.throwMaybe(exception.getMaybe());
        return stored;
    }
    
    @Override
    public synchronized Stored<Account> update(Stored<Account> account,
                                            Account new_account)
        throws UpdatedException,
            DeletedException,
            SQLException {
    final Stored<Account> current = get(account.identity);
    final Stored<Account> updated = current.newVersion(new_account);
    if(current.version.equals(account.version)) {
        String sql = "UPDATE Account SET" +
            " (version,user) =('" 
                            + updated.version  + "','"
                            + new_account.user.identity
                            + "') WHERE id='"+ updated.identity + "'";
        connection.createStatement().executeUpdate(sql);
        
        
        // Rewrite the list of channels
        connection.createStatement().executeUpdate("DELETE FROM AccountChannel WHERE account='" + account.identity + "'");
        
        final Maybe.Builder<SQLException> exception = Maybe.builder();
        final Mutable<Integer> ordinal = new Mutable<Integer>(0);
        new_account.channels.forEach(element -> {
            String alias = element.first;
            Stored<Channel> channel = element.second;
            final String msql
                = "INSERT INTO AccountChannel VALUES('" + account.identity + "','"
                                                        + channel.identity + "','"
                                                        + alias + "','"
                                                        + ordinal.get().toString() + "')";
            try { connection.createStatement().executeUpdate(msql); }
            catch (SQLException e) { exception.accept(e) ; }
            ordinal.accept(ordinal.get() + 1);
        });

        Util.throwMaybe(exception.getMaybe());
    } else {
        throw new UpdatedException(current);
    }
    return updated;
    }
   
    @Override
    public synchronized void delete(Stored<Account> account)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<Account> current = get(account.identity);
        if(current.version.equals(account.version)) {
        String sql =  "DELETE FROM Account WHERE id ='" + account.identity + "'";
        connection.createStatement().executeUpdate(sql);
        } else {
        throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Account> get(UUID id)
      throws DeletedException,
             SQLException {

        final String accountsql = "SELECT version,user,password,salt FROM Account WHERE id = '" + id.toString() + "'";
        final String channelsql = "SELECT channel,alias,ordinal FROM AccountChannel WHERE account = '" + id.toString() + "' ORDER BY ordinal DESC";

        final Statement accountStatement = connection.createStatement();
        final Statement channelStatement = connection.createStatement();

        final ResultSet accountResult = accountStatement.executeQuery(accountsql);
        final ResultSet channelResult = channelStatement.executeQuery(channelsql);

        if(accountResult.next()) {
            final UUID version = UUID.fromString(accountResult.getString("version"));
            final UUID userid =
            UUID.fromString(accountResult.getString("user"));
            final byte[] password =
            accountResult.getBytes("password");
            final byte[] salt =
                    accountResult.getBytes("salt");
            final Stored<User> user = userStore.get(userid);
            // Get all the channels associated with this account
            final List.Builder<Pair<String,Stored<Channel>>> channels = List.builder();
            while(channelResult.next()) {
                final UUID channelId = 
                    UUID.fromString(channelResult.getString("channel"));
                final String alias = channelResult.getString("alias");
                channels.accept(
                    new Pair<String,Stored<Channel>>(
                        alias,channelStore.get(channelId)));
            }
            return (new Stored<Account>(new Account(user,channels.getList(),password, salt),id,version));
        } else {
            throw new DeletedException();
        }
    }
    
    /**
     * Look up an account based on their username.
     */
    public Stored<Account> lookup(String username)
      throws DeletedException,
             SQLException {
        final String sql = "SELECT Account.id from Account INNER JOIN User ON user=User.id where User.name='" + username + "'";
        
        System.err.println(sql);
        final Statement statement = connection.createStatement();
        
        final ResultSet rs = statement.executeQuery(sql);
        if(rs.next()) {
            final UUID identity = 
                    UUID.fromString(rs.getString("id"));
            return get(identity);
        }
        throw new DeletedException();
    }
    
} 
 
