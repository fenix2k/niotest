package server;

import network.Message;
import network.MessageIO;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

// Класс экземпляра клиента на сервере
public class NioServerClient implements Runnable {
    private final long clientId; // текущий ИД клиента
    private static long clientCounter = 0; // счётчик когда-либо подключенных клиентов
    private final SelectionKey clientKey; // ключ (для получения/отправки данных)
    private final MessageIO messageIO; // класс обеспечивает приём и отправку сообщений Message
    private Queue<Message> inputMessageQueue = new ConcurrentLinkedQueue<>(); // очередь входящих сообщений для обработки
    private static Logger logger = Logger.getLogger(NioServerClient.class.getName()); // логгер

    // Конструктор принимает SelectionKey, присваивает ИД и запоминает сессию клиента
    public NioServerClient(SelectionKey clientKey) throws IOException {
        if(clientKey != null) {
            this.clientKey = clientKey;
            messageIO = new MessageIO(this.clientKey);
            this.clientId = getNewClientId();
            SessionManager.addSession(clientKey, this); // запоминаем сессию клиента
        }
        else throw new IOException("Selection key is null");
    }

    // Получаем новый уникальный ИД клиента
    private long getNewClientId() {
        return ++clientCounter;
    }

    // геттер ИД клиента
    public long getClientId() {
        return clientId;
    }

    @Override
    public void run() {
        logger.fine("[" + this.clientId + "] " + Thread.currentThread().getName() + ": executed");

        Message message; // экземпяр сообщения
        // В цикле обрабатываем все сообщения из очереди вх. сообщений
        while((message = inputMessageQueue.poll()) != null) {
            logger.fine("[" + this.clientId + "] Received message = " + message);
            System.out.println("[" + this.clientId + "] Received message = " + message);

            // обрабатываем сообщение и получаем результат
            int result = this.processingMessage(message);
            if (result == 1) {
                // успешно
            } else if (result == -1) {
                // произошла ошибка. закрываем канал
                this.closeChannel();
            }
        }
    }

    // Метод-адаптер. Читаем пакет из канала.
    // Возвращает результатам типа int
    public int read() {
        logger.fine("[" + this.clientId + "] Try to read message...");

        try {
            // Читаем пакет и получаем массив сообщений
            Queue<Message> messageQueue = messageIO.read();
            if(messageQueue != null) // если не null, то добавляем в очередь сообщений
                this.inputMessageQueue.addAll(messageQueue);
        } catch (IOException e) {
            // Ошибка, возвращаем -1
            logger.log(Level.SEVERE, "[" + this.clientId + "] Read channel error: ", e);
            this.closeChannel();
            return -1;
        }

        // Если очередь сообщений не пуста, то возвращаем 1
        if(this.inputMessageQueue.size() > 0) {
            logger.fine("[" + this.clientId + "] Received messages = " + this.inputMessageQueue.size());
            if(messageIO.hasMessageTail())
                logger.fine("[" + this.clientId + "] Has tail of message!");
            return 1;
        }
        else { // Сообщение не полное. Возвращает 0 и ждём следующих пакетов.
            logger.fine("[" + this.clientId + "] Message is null or not full");
            return 0;
        }
    }

    // Метод-адаптер. Записывает пакет из канал
    // Возвращает результатам типа int
    public int write() {
        logger.fine("[" + this.clientId + "] Try to write message...");
        int result;
        try {
            // Пишем сообщение в канал и получаем результат
            result = messageIO.write();
            if(result == 1) {
                // Если успешно - переходим в режим чтения канала
                logger.fine("[" + this.clientId + "] Changing channel mode to OP_READ");
                this.clientKey.interestOps(SelectionKey.OP_READ);
                return 1;
            }
            return 0;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[" + this.clientId + "] Write channel error: ", e);
            this.closeChannel();
            return -1;
        }
    }

    // Метод отвечающий за обработку входящих сообщений
    private int processingMessage(Message message) {
        if(message != null) {
            // Завершаем сессию, если пришло сообщение "quit"
            if ("quit".equals(message.getMessage().toLowerCase())) {
                return -1;
            }

            // ТУТ ДОЛЖНА БЫТЬ ОБРАБОТКА

            messageIO.addToOutputQueue(message); // кладем готовое сообщениев очередь исходящих сообщений
            System.out.println("[" + this.clientId + "] " + Thread.currentThread().getName() + ": send to client = " + message);

            logger.fine("[" + this.clientId + "] Changing channel mode to OP_WRITE");
            // выставляем флаг о том что необходимо отправить данные
            this.clientKey.interestOps(SelectionKey.OP_WRITE);

            return 1;
        }
        else return -1;
    }

    // Закрывает канал и отменяет ключ
    public void closeChannel() {
        SocketChannel channel = (SocketChannel) this.clientKey.channel();
        try {
            if(channel.isConnected())
                channel.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE,"Channel close error: ", e);
        }
        System.out.println("Client [" + this.clientId + "] was disconnected");
        logger.fine("Client [" + this.clientId + "] was disconnected");
        SessionManager.removeSession(clientKey); // удаляем сессию клиента
        clientKey.cancel();
    }

}
