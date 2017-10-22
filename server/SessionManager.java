package server;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;


// Менеджер сессий для хранения информации о подключенных клиентах
public class SessionManager {
    private static final Map<SelectionKey, NioServerClient> sessions = new HashMap<>();

    public static void addSession(SelectionKey key) {
        sessions.put(key, new NioServerClient(key));
    }

    public static void removeSession(SelectionKey key) {
        sessions.remove(key);
    }

    public static NioServerClient getChannelByKey(SelectionKey key) {
        return sessions.get(key);
    }
}
