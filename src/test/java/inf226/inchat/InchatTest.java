 
package inf226.inchat;

import org.junit.jupiter.api.Test;

import inf226.storage.*;

import inf226.util.*;

import java.util.UUID;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DriverManager;

public class InchatTest{
    @Test
    void chatSetup() throws Maybe.NothingException,SQLException {
        UUID testID = UUID.randomUUID();
        System.err.println("Running test:" + testID);
        final String path = "test" + testID +  ".db";
        final String dburl = "jdbc:sqlite:" + path;
        final Connection connection = DriverManager.getConnection(dburl);
        connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
        UserStorage userStore
            = new UserStorage(connection);
        ChannelStorage channelStore
            = new ChannelStorage(connection);
        AccountStorage accountStore
            = new AccountStorage(connection,userStore,channelStore);
        SessionStorage sessionStore
            = new SessionStorage(connection,accountStore);
        connection.setAutoCommit(false);
        InChat inchat = new InChat(userStore,channelStore,accountStore,sessionStore,connection);
        Stored<Session> aliceSession = inchat.register("Alice","badpass1word").get();
        inchat.register("Bob","worse123").get();
        Stored<Session> bobSession = inchat.login("Bob","worse123").get();
        Stored<Channel> channel = inchat.createChannel(aliceSession.value.account,"Awesome").get();
        inchat.postMessage(aliceSession.value.account,channel, "Test message.").get();
        inchat.joinChannel(bobSession.value.account,channel.identity).get();
        connection.close();
    }
}
