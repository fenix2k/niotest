package server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NioServer implements Runnable {
    private final String IP; // адрес сервера
    private final int PORT; // порт сервера

    private static final AtomicReference<NioServer.State> state = new AtomicReference<>(NioServer.State.STOPPED); // переключатель состояния сервера

    private enum State {STOPPED, STOPPING, RUNNING} // возможные состояния сервера

    private static Logger logger = Logger.getLogger(NioServer.class.getName()); // логгер

    public NioServer(int port) {
        this("localhost", port);
    }

    public NioServer(String ip, int port) {
        this.IP = ip;
        this.PORT = port;
    }


    @Override
    public void run() {
        // проверяем запущен ли сервер. Если нет закускаем.
        if (!state.compareAndSet(NioServer.State.STOPPED, NioServer.State.RUNNING)) {
            logger.warning("Server already started");
            System.out.println("Server already started");
            return;
        }

        Selector selector = null;   // селектор
        ServerSocketChannel serverChannel = null; // канал сервера

        try {
            selector = Selector.open(); // создаём селектор
            serverChannel = ServerSocketChannel.open(); // создаём канал сервера
            serverChannel.socket().bind(new InetSocketAddress(IP, PORT)); // закускаем сервер и слушаем порт
            serverChannel.configureBlocking(false); // устанавливаем не блокирующий режим
            serverChannel.register(selector, SelectionKey.OP_ACCEPT); // регистрируем канал сервера в селекторе и устанавливаем флаг ожидания запроса на соединение

            System.out.println("Server is started on " + this.IP + ":" + this.PORT);
            logger.fine("Server is started on " + this.IP + ":" + this.PORT);

            // создаём менеджер потоков для обработки вх. сообщений в отдельных потоках
            final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    5, // обычное кол-во потоков в пуле
                    10, // макс. кол-во потоков в пуле
                    1000, // время которое живёт ничего не делающий поток
                    TimeUnit.MILLISECONDS, // единицы измерения времени
                    new LinkedBlockingQueue<Runnable>(), // тип экземпляра
                    new ThreadPoolExecutor.CallerRunsPolicy() // политика
            );

            // пока переключатель состояния в RUNNING продолжаем слушать порт
            while (state.get() == NioServer.State.RUNNING) {
                selector.select(100); // ждём входящих сообщений от клиентов
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator(); // получеам итератор массива ключей подключения

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next(); // последовательно перебираем каналы (ключи)
                    iterator.remove(); // удаляем ключ

                    if (!key.isValid()) { // если ключ истёк прерываем текущую итерацию
                        logger.fine("Key is not valid.");
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
                            System.out.println("New client connected. ID=" + client.getClientId());
                            logger.fine("New client connected. ID=" + client.getClientId());
                        } catch(IOException ex) {
                            System.out.println("Key is canceled " + clientKey);
                            logger.fine("Key is canceled " + clientKey);
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
            logger.log(Level.SEVERE, "Port already used: ", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception: ", e);
        } finally { // в любом случае была ошибка или нет гасим сервер
            try {
                selector.close(); // закрываем селектор
                serverChannel.socket().close(); // закрываем сокет канала сервера
                serverChannel.close(); // закрываем канал сервера
                state.set(NioServer.State.STOPPED); // устанавливает статус сервера в STOPPED
                System.out.println("Server is stopped");
                logger.fine("Server is stopped");
            } catch (IOException e) {
                logger.log(Level.SEVERE,"Exception: ", e);
            }
        }
    }

    // Получаем объект клиента по ключу селектора
    private NioServerClient getClientByKey(SelectionKey key) {
        return SessionManager.getClientByKey(key);
    }

}