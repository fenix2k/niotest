package server;

import network.Message;
import network.MessageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

// Класс экземпляра клиента на сервере
public class NioServerClient implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(NioServerClient.class.getName());
    private static AtomicLong clientCounter = new AtomicLong(0); // счётчик когда-либо подключенных клиентов

    private final long clientId; // текущий ИД клиента
    private final SelectionKey clientKey; // ключ (для получения/отправки данных)
    private final MessageIO messageIO; // класс обеспечивает приём и отправку сообщений Message
    private Queue<Message> inputMessageQueue = new ConcurrentLinkedQueue<>(); // очередь входящих сообщений для обработки

    // Конструктор принимает SelectionKey, присваивает ИД и запоминает сессию клиента
    public NioServerClient(SelectionKey clientKey) throws IOException {
        if(clientKey != null) {
            this.clientKey = clientKey;
            this.clientId = getNewClientId();
            messageIO = new MessageIO(this.clientKey, AppSettings.getInstance().NET_MAX_PACKET_SIZE);
            SessionManager.addSession(clientKey, this); // запоминаем сессию клиента
        }
        else
            throw new IOException("Selection key is null");
    }

    // Получаем новый уникальный ИД клиента
    private long getNewClientId() {
        return clientCounter.incrementAndGet();
    }

    // геттер ИД клиента
    public long getClientId() {
        return clientId;
    }

    // запускается в отдельном потоке для обработки очереди вх. сообщений
    @Override
    public void run() {
        MDC.put("clientId", String.valueOf(this.clientId));
        Thread.currentThread().setName("pThread-" + this.clientId);
        logger.debug("New processing thread executed");

        Message message; // экземпяр сообщения
        // В цикле обрабатываем все сообщения из очереди вх. сообщений
        while((message = inputMessageQueue.poll()) != null) {
            // обрабатываем сообщение и получаем результат
            int result = this.processingMessage(message);
            if (result == 1) {
                // успешно
            } else if (result == -1) {
                // Пришла комманда зарыть соединение или пустое сообщение. закрываем канал
                this.closeChannel();
            }
        }
        MDC.remove("clientId");
    }

    // Метод-адаптер. Читаем пакет из канала.
    // Возвращает результатам типа int
    public int read() {
        MDC.put("clientId", String.valueOf(this.clientId));
        logger.debug("Read message...");

        try {
            // Читаем пакет и получаем массив сообщений
            Queue<Message> messageQueue = messageIO.read();
            if(messageQueue != null) { // если не null, то добавляем в очередь сообщений
                this.inputMessageQueue.addAll(messageQueue);
            }
        } catch (IOException e) {
            // Ошибка, возвращаем -1
            logger.debug("Read channel error (May be client disconnected)");
            this.closeChannel();
            return -1;
        }

        // Если очередь сообщений не пуста, то возвращаем 1
        if(this.inputMessageQueue.size() > 0) {
            logger.debug("Received {} messages", this.inputMessageQueue.size());
            if(messageIO.hasMessageTail())
                logger.debug("Buffer has tail of message!");
            MDC.remove("clientId");
            return 1;
        }
        else { // Сообщение не полное. Возвращает 0 и ждём следующих пакетов.
            logger.debug("Message is null or not full");
            MDC.remove("clientId");
            return 0;
        }
    }

    // Метод-адаптер. Записывает пакет из канал
    // Возвращает результатам типа int
    public int write() {
        MDC.put("clientId", String.valueOf(this.clientId));
        logger.debug("Write message...");
        int result;
        try {
            // Пишем сообщение в канал и получаем результат
            result = messageIO.write();
            if(result == 1) {
                // Если успешно - переходим в режим чтения канала
                logger.debug("Changing channel mode to OP_READ");
                this.clientKey.interestOps(SelectionKey.OP_READ);
                MDC.remove("clientId");
                return 1;
            }
            return 0;
        } catch (IOException e) {
            logger.debug("Write to channel error (May be client disconnected)");
            this.closeChannel();
            MDC.remove("clientId");
            return -1;
        }
    }

    // Метод отвечающий за обработку входящих сообщений
    private int processingMessage(Message message) {
        if(message != null) {
            logger.debug("Process message: {}", message);

            // Завершаем сессию, если пришло сообщение "quit"
            if ("quit".equals(message.getMessage().toLowerCase())) {
                return -1;
            }

            // ТУТ ДОЛЖНА БЫТЬ ОБРАБОТКА

            messageIO.addToOutputQueue(message); // кладем готовое сообщениев очередь исходящих сообщений
            logger.debug("Message added to outgoing queue: {}", message);


            // выставляем флаг о том что необходимо отправить данные
            this.clientKey.interestOps(SelectionKey.OP_WRITE);
            logger.debug("Changing channel mode to OP_WRITE");

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
            logger.debug("Close channel error: ", e);
        }
        logger.debug("Client was disconnected");
        SessionManager.removeSession(clientKey); // удаляем сессию клиента
        clientKey.cancel();
    }

}
