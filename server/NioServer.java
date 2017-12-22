package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class NioServer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(NioServer.class.getName());

    private final String IP; // адрес сервера
    private final int PORT; // порт сервера
    private final int clientThreadCount;
    private final int clientThreadMaxCount;
    private final int clientThreadKeepalive;

    private static final AtomicReference<NioServer.State> state = new AtomicReference<>(NioServer.State.STOPPED); // переключатель состояния сервера

    private enum State {STOPPED, STOPPING, RUNNING} // возможные состояния сервера

    public NioServer(int port) {
        this("localhost", port);
    }

    public NioServer(String ip, int port) {
        this.IP = ip;
        this.PORT = port;
        AppSettings config = AppSettings.getInstance();
        this.clientThreadCount = config.CLIENT_THREAD_COUNT;
        this.clientThreadMaxCount = config.CLIENT_THREAD_MAX_COUNT;
        this.clientThreadKeepalive = config.CLIENT_THREAD_KEEPALIVE;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("NioServer");
        // проверяем запущен ли сервер. Если нет закускаем.
        if (!state.compareAndSet(NioServer.State.STOPPED, NioServer.State.RUNNING)) {
            logger.info("Server already started");
            return;
        }

        Selector selector = null;   // селектор
        ServerSocketChannel serverChannel = null; // канал сервера

        // создаём менеджер потоков для обработки вх. сообщений в отдельных потоках
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                this.clientThreadCount, // обычное кол-во потоков в пуле
                this.clientThreadMaxCount, // макс. кол-во потоков в пуле
                this.clientThreadKeepalive, // время которое живёт ничего не делающий поток
                TimeUnit.MILLISECONDS, // единицы измерения времени
                new LinkedBlockingQueue<Runnable>(), // тип экземпляра
                new ThreadPoolExecutor.CallerRunsPolicy() // политика
        );

        try {
            selector = Selector.open(); // создаём селектор
            serverChannel = ServerSocketChannel.open(); // создаём канал сервера
            serverChannel.socket().bind(new InetSocketAddress(IP, PORT)); // закускаем сервер и слушаем порт
            serverChannel.configureBlocking(false); // устанавливаем не блокирующий режим
            serverChannel.register(selector, SelectionKey.OP_ACCEPT); // регистрируем канал сервера в селекторе и устанавливаем флаг ожидания запроса на соединение

            logger.info("Server is started on {}:{}", this.IP, this.PORT);

            // пока переключатель состояния в RUNNING продолжаем слушать порт
            while (state.get() == NioServer.State.RUNNING) {
                selector.select(100); // ждём входящих сообщений от клиентов
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator(); // получеам итератор массива ключей подключения

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next(); // последовательно перебираем каналы (ключи)
                    iterator.remove(); // удаляем ключ

                    if (!key.isValid()) { // если ключ истёк прерываем текущую итерацию
                        logger.warn("Selection key is not valid");
                        continue;
                    }

                    if (key.isValid() && key.isConnectable()) { // не используется в однопоточной версии
                        ((SocketChannel) key.channel()).finishConnect();
                    }

                    // Если установлен флаг OP_ACCEPT (ждем входящих подключений)
                    if (key.isValid() && key.isAcceptable()) {
                        SocketChannel clientChannel = serverChannel.accept(); // создаём канал с клиентом
                        clientChannel.configureBlocking(false); // устанавливаем не блокирующий режим
                        clientChannel.socket().setTcpNoDelay(true); // отключаем алгоритм оптимизации
                        // регистрируем канал клиента в селекторе и устанавливаем флаг ожидания чтения данных
                        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

                        try {
                            // регистрируем сессию клиента для дальнейшей работы
                            NioServerClient client = new NioServerClient(clientKey);
                            logger.info("New client connected. ID={}", client.getClientId());
                        } catch(IOException ex) {
                            logger.info("Selection key is canceled {}", clientKey);
                        }
                    }

                    // Если установлен флаг OP_READ (читаем вх. сообщения)
                    if (key.isValid() && key.isReadable()) {
                        NioServerClient client = getClientByKey(key); // определяем сессию клиента по ключу
                        if(client.read() == 1) // читаем данные
                            executor.execute(client); // запускаем обработку сообщения в отдельном потоке
                    }

                    // Если установлен флаг OP_WRITE (отправляем сообщения)
                    if (key.isValid() && key.isWritable()) {
                        NioServerClient client = getClientByKey(key); // определяем сессию клиента по ключу
                        client.write(); // отправляем данные
                    }
                }
            }


        } catch (BindException e) {
            logger.error("Port already used: ", e);
        } catch (IOException e) {
            logger.error("Exception: ", e);
        } finally { // в любом случае была ошибка или нет гасим сервер
            try {
                selector.close(); // закрываем селектор
                serverChannel.socket().close(); // закрываем сокет канала сервера
                serverChannel.close(); // закрываем канал сервера
                executor.shutdown();

                state.set(NioServer.State.STOPPED); // устанавливает статус сервера в STOPPED
                logger.info("Server is stopped");
            } catch (IOException e) {
                logger.error("Exception: ", e);
            }
        }
    }

    // Получаем объект клиента по ключу селектора
    private NioServerClient getClientByKey(SelectionKey key) {
        return SessionManager.getClientByKey(key);
    }

    public void setState(State state) {
        NioServer.state.set(state);
    }

    public void shutdown() {
        SessionManager.removeAllSessions();
        this.setState(State.STOPPING);
        return;
    }

    public ArrayList<String> getSessionlist() {
        return SessionManager.getSessionList();
    }
}