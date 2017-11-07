package server;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


// Менеджер сессий для хранения информации о подключенных клиентах
public class SessionManager {
    private static Logger logger = Logger.getLogger(SessionManager.class.getName()); // логгер

    private static final Map<SelectionKey, NioServerClient> sessions = new HashMap<>(); // MAP для хранения сессий

    // Конструктор. Запоминает сессию клиента
    public static NioServerClient addSession(SelectionKey key, NioServerClient client) {
        if(key != null && client != null) {
            sessions.put(key, client);
            return client;
        }
        else return null;
    }

    // Метод удалёет сессию клиента
    public static void removeSession(SelectionKey key) {
        if(key != null)
            sessions.remove(key);
    }

    // Метод возвращает экземпляр клиента NioServerClient по SelectionKey
    public static NioServerClient getClientByKey(SelectionKey key) {
        if(key != null)
            return sessions.get(key);
        else return null;
    }
}
