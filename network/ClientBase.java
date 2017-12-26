package network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// Базовый класс экземпляра клиента.
// Реализует чтение и запись в канал клиента, закрытие канала
public class ClientBase {
    protected static final Logger logger = LoggerFactory.getLogger(ClientBase.class.getName());
    protected final int clientId; // текущий ИД клиента
    protected final SelectionKey clientKey; // ключ (для получения/отправки данных)
    protected final ChannelReader channelReader;
    protected final ChannelWriter channelWriter;

    protected Queue<PacketBase> inputPacketBaseQueue = new ConcurrentLinkedQueue<>(); // очередь входящих сообщений для обработки
    protected Queue<PacketBase> outputPacketBaseQueue = new ConcurrentLinkedQueue<>(); // очередь исходящих сообщений для обработки
    public int NET_MAX_PACKET_SIZE = 1024;

    // Конструктор принимает SelectionKey, присваивает ИД и запоминает сессию клиента
    public ClientBase(SelectionKey clientKey, int clientId) throws IOException {
        if(clientKey != null) {
            this.clientKey = clientKey;
            this.clientId = clientId;
            channelReader = new ChannelReader(this.clientKey, NET_MAX_PACKET_SIZE);
            channelWriter = new ChannelWriter(this.clientKey);
        }
        else
            throw new IOException("Selection key is null");
    }

    // геттер ИД клиента
    public long getClientId() {
        return clientId;
    }

    // Метод-адаптер. Читаем пакет из канала.
    // Возвращает результат типа int
    public int read() {
        MDC.put("clientId", String.valueOf(this.clientId));
        logger.debug("Read message...");

        try {
            // Читаем пакет и получаем массив сообщений
            Queue<PacketBase> packetBaseQueue = channelReader.read();
            if(packetBaseQueue != null) { // если не null, то добавляем в очередь сообщений
                this.inputPacketBaseQueue.addAll(packetBaseQueue);
            }
        } catch (IOException e) {
            // Ошибка, возвращаем -1
            logger.debug("Read channel error (May be client disconnected)");
            this.closeChannel();
            return -1;
        }

        // Если очередь сообщений не пуста, то возвращаем 1
        if(this.inputPacketBaseQueue.size() > 0) {
            logger.debug("Received {} messages", this.inputPacketBaseQueue.size());
            if(channelReader.hasMessageTail())
                logger.debug("Buffer has tail of message!");
            MDC.remove("clientId");
            return 1;
        }
        else { // Сообщение не полное. Возвращает 0 и ждём следующих пакетов.
            logger.debug("Packet is null or not full");
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
            result = channelWriter.write();
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
        clientKey.cancel();
    }
}
