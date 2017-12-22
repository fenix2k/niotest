package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


// Менеджер сессий для хранения информации о подключенных клиентах
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class.getName());

    private static final ConcurrentMap<SelectionKey, NioServerClient> sessions = new ConcurrentHashMap<>(); // MAP для хранения сессий

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

    // Метод удаляет все сессии
    public static void removeAllSessions() {
        for(Map.Entry<SelectionKey, NioServerClient> entry : sessions.entrySet()) {
            sessions.remove(entry.getKey());
            entry.getKey().cancel();
        }
    }

    // Метод возвращает экземпляр клиента NioServerClient по SelectionKey
    public static NioServerClient getClientByKey(SelectionKey key) {
        if(key != null)
            return sessions.get(key);
        else return null;
    }

    public static ArrayList<String> getSessionList() {
        ArrayList<String> sessionList = new ArrayList<>();
        int i = 0;

        for(Map.Entry<SelectionKey, NioServerClient> entry : sessions.entrySet()) {
            NioServerClient client = entry.getValue();
            sessionList.add( ++i
                    + " " + entry.getKey().toString()
                    + " " + client.getClientId());
        }
        
        return sessionList;
    }
}
