package inf226.inchat;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

import inf226.storage.*;
import inf226.util.*;



/**
 * The UserStore stores User objects in a SQL database.
 */
public final class UserStorage
    implements Storage<User,SQLException> {
    
    final Connection connection;
    
    public UserStorage(Connection connection) 
      throws SQLException {
        this.connection = connection;
        connection.createStatement()
                .executeUpdate("CREATE TABLE IF NOT EXISTS User (id TEXT PRIMARY KEY, version TEXT, name TEXT, joined TEXT)");
    }
    
    @Override
    public Stored<User> save(User user)
      throws SQLException {
        final Stored<User> stored = new Stored<User>(user);

        String sql = "INSERT INTO User VALUES(?,?,?,?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setObject(1,stored.identity);
        preparedStatement.setObject(2, stored.version);
        preparedStatement.setString(3, user.name.getUserName());
        preparedStatement.setString(4, user.joined.toString());
        preparedStatement.executeUpdate();


        connection.createStatement().executeUpdate(sql);
        return stored;
    }
    
    @Override
    public synchronized Stored<User> update(Stored<User> user,
                                            User new_user)
        throws UpdatedException,
            DeletedException,
            SQLException {
        final Stored<User> current = get(user.identity);
        final Stored<User> updated = current.newVersion(new_user);
        if(current.version.equals(user.version)) {

            final String sql = "UPDATE User SET (?,?,?) WHERE id=?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setObject(1,updated.version);
            preparedStatement.setObject(2, new_user.name);
            preparedStatement.setObject(3, new_user.joined.toString());
            preparedStatement.setObject(4, updated.identity);
            preparedStatement.executeUpdate();

        } else {
            throw new UpdatedException(current);
        }
        return updated;
    }
   
    @Override
    public synchronized void delete(Stored<User> user)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<User> current = get(user.identity);
        if(current.version.equals(user.version)) {
            String sql =  "DELETE FROM User WHERE id ='" + user.identity + "'";
            connection.createStatement().executeUpdate(sql);
        } else {
        throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<User> get(UUID id)
      throws DeletedException,
             SQLException {
        final String sql = "SELECT version,name,joined FROM User WHERE id = '" + id.toString() + "'";
        final Statement statement = connection.createStatement();
        final ResultSet rs = statement.executeQuery(sql);

        if(rs.next()) {
            final UUID version = 
                UUID.fromString(rs.getString("version"));
            final String name = rs.getString("name");
            final Instant joined = Instant.parse(rs.getString("joined"));
            return (new Stored<User>
                        (new User(name,joined),id,version));
        } else {
            throw new DeletedException();
        }
    }
    
    /**
     * Look up a user by their username;
     **/
    public Maybe<Stored<User>> lookup(String name) {
        final String sql = "SELECT id FROM User WHERE name =?";
        try{
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1,name);
            final ResultSet rs = preparedStatement.executeQuery();
            if(rs.next())
                return Maybe.just(
                    get(UUID.fromString(rs.getString("id"))));
        } catch (Exception e) {
        
        }
        return Maybe.nothing();
    }
}


