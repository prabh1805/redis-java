package com.prabh.redis.command;

import com.prabh.redis.protocol.RespWriter;
import com.prabh.redis.store.Keyspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandHandlerTest {

    private ByteArrayOutputStream out;
    private CommandHandler handler;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        handler = new CommandHandler(new RespWriter(out), new Keyspace());
    }

    private String run(String... args) throws Exception {
        List<byte[]> command = new ArrayList<>();
        for (String arg : args) {
            command.add(arg.getBytes());
        }
        out.reset();
        handler.handle(command);
        return out.toString();
    }

    private static String arityError(String command) {
        return "-ERR wrong number of arguments for '" + command + "' command\r\n";
    }

    @Test
    void pingRepliesPong() throws Exception {
        assertEquals("+PONG\r\n", run("PING"));
    }

    @Test
    void pingWithAnArgumentRepliesWithThatArgument() throws Exception {
        assertEquals("$5\r\nhello\r\n", run("PING", "hello"));
    }

    @Test
    void echoRepliesWithItsArgument() throws Exception {
        assertEquals("$5\r\nhello\r\n", run("ECHO", "hello"));
    }

    @Test
    void setThenGetReturnsTheValue() throws Exception {
        assertEquals("+OK\r\n", run("SET", "foo", "bar"));
        assertEquals("$3\r\nbar\r\n", run("GET", "foo"));
    }

    @Test
    void setOverwritesAnExistingValue() throws Exception {
        run("SET", "foo", "old");
        run("SET", "foo", "new");

        assertEquals("$3\r\nnew\r\n", run("GET", "foo"));
    }

    @Test
    void getOnAMissingKeyReturnsNil() throws Exception {
        assertEquals("$-1\r\n", run("GET", "missing"));
    }

    @Test
    void existsCountsThePresentKeys() throws Exception {
        run("SET", "a", "1");
        run("SET", "b", "2");

        assertEquals(":2\r\n", run("EXISTS", "a", "b", "c"));
    }

    @Test
    void existsCountsARepeatedKeyEachTime() throws Exception {
        run("SET", "a", "1");

        assertEquals(":3\r\n", run("EXISTS", "a", "a", "a"));
    }

    @Test
    void existsOnAMissingKeyReturnsZero() throws Exception {
        assertEquals(":0\r\n", run("EXISTS", "missing"));
    }

    @Test
    void delCountsTheRemovedKeysAndRemovesThem() throws Exception {
        run("SET", "a", "1");
        run("SET", "b", "2");

        assertEquals(":2\r\n", run("DEL", "a", "b", "c"));
        assertEquals("$-1\r\n", run("GET", "a"));
        assertEquals("$-1\r\n", run("GET", "b"));
    }

    @Test
    void delCountsARepeatedKeyOnce() throws Exception {
        run("SET", "a", "1");

        assertEquals(":1\r\n", run("DEL", "a", "a", "a"));
    }

    @Test
    void delOnAMissingKeyReturnsZero() throws Exception {
        assertEquals(":0\r\n", run("DEL", "missing"));
    }

    @Test
    void pingWithTooManyArgumentsIsAnArityError() throws Exception {
        assertEquals(arityError("ping"), run("PING", "a", "b"));
    }

    @Test
    void echoWithNoArgumentIsAnArityError() throws Exception {
        assertEquals(arityError("echo"), run("ECHO"));
    }

    @Test
    void echoWithTooManyArgumentsIsAnArityError() throws Exception {
        assertEquals(arityError("echo"), run("ECHO", "a", "b"));
    }

    @Test
    void setWithNoValueIsAnArityError() throws Exception {
        assertEquals(arityError("set"), run("SET", "k"));
    }

    @Test
    void setWithTooManyArgumentsIsAnArityErrorAndStoresNothing() throws Exception {
        assertEquals(arityError("set"), run("SET", "k", "v", "x"));
        assertEquals("$-1\r\n", run("GET", "k"));
    }

    @Test
    void getWithNoKeyIsAnArityError() throws Exception {
        assertEquals(arityError("get"), run("GET"));
    }

    @Test
    void getWithTooManyKeysIsAnArityError() throws Exception {
        assertEquals(arityError("get"), run("GET", "a", "b"));
    }

    @Test
    void existsWithNoKeyIsAnArityError() throws Exception {
        assertEquals(arityError("exists"), run("EXISTS"));
    }

    @Test
    void delWithNoKeyIsAnArityError() throws Exception {
        assertEquals(arityError("del"), run("DEL"));
    }

    @Test
    void emptyCommandProducesNoReply() throws Exception {
        assertEquals("", run());
    }

    @Test
    void unknownCommandIsAnError() throws Exception {
        assertEquals("-ERR unknown command\r\n", run("NOSUCH"));
    }

    @Test
    void commandNamesAreCaseInsensitive() throws Exception {
        assertEquals("+PONG\r\n", run("ping"));
    }
}
