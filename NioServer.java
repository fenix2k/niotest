import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

// Класс сервера
public class NioServer implements Runnable {
    private final String IP; // адрес сервера
    private final int PORT; // порт сервера

    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED); // переключатель состояния сервера

    private enum State {STOPPED, STOPPING, RUNNING} // возможные состояния сервера

    public NioServer(int port) {
        this("localhost", port);
    }

    public NioServer(String ip, int port) {
        this.IP = ip;
        this.PORT = port;
        this.run(); // запуск в томже потоке
    }

    public static void main(String[] args) {
        // запуск экземпляра сервера
        new NioServer(8000);
    }

    @Override
    public void run() {
        // проверяем запущен ли сервер. Если нет закускаем.
        if(!state.compareAndSet(State.STOPPED, State.RUNNING)) {
            System.out.println("Server already started.");
            return;
        }
        System.out.println("Server starting...");

        Selector selector = null;   // селектор
        ServerSocketChannel serverChannel = null; // канал сервера

        try {
            selector = Selector.open(); // создаём селектор
            serverChannel = ServerSocketChannel.open(); // создаём канал сервера
            serverChannel.socket().bind(new InetSocketAddress(IP, PORT)); // закускаем сервер и слушаем порт
            serverChannel.configureBlocking(false); // устанавливаем не блокирующий режим
            serverChannel.register(selector, SelectionKey.OP_ACCEPT); // регистрируем канал сервера в селекторе и устанавливаем флаг ожидания запроса на соединение
            System.out.println("Server is started.");

            // пока переключатель состояния в RUNNING продолжаем слушать порт
            while(state.get() == State.RUNNING) {
                selector.select(100); // ждём входящих сообщений от клиентов
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator(); // получеам итератор массива ключей подключения

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next(); // последовательно перебираем каналы (ключи)
                    iterator.remove();

                    if(!key.isValid()) { // если ключ истёк прерываем текущую итерацию
                        System.out.println("Key is not valid");
                        continue;
                    }

                    if (key.isValid() && key.isConnectable()) { // не используется в однопоточной версии
                        ((SocketChannel)key.channel()).finishConnect();
                    }

                    if(key.isValid() && key.isAcceptable()) { // если установлен флаг OP_ACCEPT
                        SocketChannel clientChannel = serverChannel.accept(); // создаём канал с клиентом
                        clientChannel.configureBlocking(false); // устанавливаем не блокирующий режим
                        clientChannel.socket().setTcpNoDelay(true); // отключаем алгоритм оптимизации
                        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ); // регистрируем канал клиента в селекторе и устанавливаем флаг ожидания чтения данных
                        this.registerNewClient(clientKey); // регистрируем сессию клиента для дальнейшей работы
                    }

                    if(key.isValid() && key.isReadable()) { // если установлен флаг OP_READ
                        NioServerClient client = getClientByKey(key); // определяем сессию клиента по ключу
                        client.read(); // читаем данные
                    }

                    if(key.isValid() && key.isWritable()) { // если установлен флаг OP_WRITE
                        NioServerClient client = getClientByKey(key); // определяем сессию клиента по ключу
                        client.write(); // отправляем данные
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally { // в любом случает была ошибка или нет гасим сервер
            try {
                selector.close(); // закрываем селектор
                serverChannel.socket().close(); // закрываем сокет канала сервера
                serverChannel.close(); // закрываем канал сервера
                state.set(State.STOPPED); // устанавливает статус сервера в STOPPED
                System.out.println("Server is stopped.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Получаем объект клиента по ключу селектора
    private NioServerClient getClientByKey(SelectionKey key) {
        return SessionManager.getChannelByKey(key);
    }

    // Регистрируем нового клиента
    private void registerNewClient(SelectionKey key) {
        SessionManager.addSession(key);
    }
}
