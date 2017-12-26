package server;

import network.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

// Класс управляет экземплярами потоков клиентов
// Управляет созданием экземпляра клиента, чтением/записью данных в канал клинета
public class ClientManager {
    private static final Logger logger = LoggerFactory.getLogger(ClientManager.class.getName());
    public static final ConcurrentMap<SelectionKey, Client> sessions = new ConcurrentHashMap<>(); // MAP для хранения сессий

    // создаём менеджер потоков для обработки вх. сообщений в отдельных потоках
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, // обычное кол-во потоков в пуле
            4, // макс. кол-во потоков в пуле
            5000, // время которое живёт ничего не делающий поток
            TimeUnit.MILLISECONDS, // единицы измерения времени
            new LinkedBlockingQueue<Runnable>(), // тип экземпляра
            new ThreadPoolExecutor.CallerRunsPolicy() // политика
    );

    private static int clientCounter = 0; // счётчик когда-либо подключенных клиентов

    // Получаем новый уникальный ИД клиента
    private static int getNewClientId() {
        if((clientCounter + 1) == Integer.MAX_VALUE) clientCounter = 10;
        return ++clientCounter;
    }

    public static void registerNewClient(SelectionKey clientKey) {
        try {
            int id = getNewClientId();
            Client client = new Client(clientKey, id);
            registerNewSession(clientKey, client);
            logger.info("New client connected (ID={})", client.getClientId());
        } catch (Exception e) {
            logger.info("Selection key is canceled {}", clientKey);
        }
    }

    public static void readClientChannel(SelectionKey clientKey) {
        Client client = getClientByKey(clientKey); // определяем сессию клиента по ключу
        int status = client.read();
        if(status == 1) { // читаем данные
            executor.execute(client); // запускаем обработку сообщения в отдельном потоке
        }
        else if(status == -1) {
            closeClientChannel(clientKey);
        }
    }

    public static void writeClientChannel(SelectionKey clientKey) {
        Client client = getClientByKey(clientKey); // определяем сессию клиента по ключу
        int status = client.write(); // отправляем данные
        if(status == -1) {
            closeClientChannel(clientKey);
        }
    }

    public static void closeClientChannel(SelectionKey clientKey) {
        removeSession(clientKey);
    }

    public static void closeAllClientChannels() {
        removeAllSessions();
    }

    // Конструктор. Запоминает сессию клиента
    public static Client registerNewSession(SelectionKey key, Client client) {
        if(key != null && client != null) {
            sessions.put(key, client);
            return client;
        }
        else return null;
    }

    // Метод возвращает экземпляр клиента ClientManager по SelectionKey
    public static Client getClientByKey(SelectionKey key) {
        if(key != null)
            return sessions.get(key);
        else return null;
    }

    // Метод удалёет сессию клиента
    public static void removeSession(SelectionKey key) {
        if(key != null)
            sessions.remove(key);
    }

    // Метод удаляет все сессии
    public static void removeAllSessions() {
        for(Map.Entry<SelectionKey, Client> entry : sessions.entrySet()) {
            sessions.remove(entry.getKey());
            entry.getKey().cancel();
        }
    }

    public static List<Client> getSessionList() {
        ArrayList<Client> result =  new ArrayList<>();

        for(Map.Entry<SelectionKey, Client> pair: sessions.entrySet()) {
            result.add(pair.getValue());
        }

        return result;
    }

}
