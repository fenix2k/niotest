package network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Date;

// Класс экземпляра клиента.
// Наследется от ClientBase (реализация чтения и записи канала)
// Отвечает за обработку пакетов (работает в отдельном потоке)
public class Client extends ClientBase implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Client.class.getName());
    private static enum SessionStates {INIT, AUTH, CONNECTED, CLOSED}
    private final SessionStates connectionState = SessionStates.INIT;
    private final Date connectTime = new Date();

    // Конструктор принимает SelectionKey, присваивает ИД и запоминает сессию клиента
    public Client(SelectionKey clientKey, int clientId) throws IOException {
        super(clientKey, clientId);
    }

    public Date getConnectTime() {
        return connectTime;
    }

    // запускается в отдельном потоке для обработки очереди вх. сообщений
    @Override
    public void run() {
        MDC.put("clientId", String.valueOf(this.clientId));
        Thread.currentThread().setName("pThread-" + this.clientId);

        logger.debug("New processing thread executed");

        PacketBase packetBase; // экземпяр сообщения
        // В цикле обрабатываем все сообщения из очереди вх. сообщений
        while ((packetBase = inputPacketBaseQueue.poll()) != null) {
            // обрабатываем сообщение и получаем результат
            int result = this.processingPacket(packetBase);
            if (result == -1) {
                // Пришла комманда зарыть соединение или пустое сообщение. Закрываем канал.
                this.closeChannel();
            }
        }

        if(this.outputPacketBaseQueue.size() > 0 && this.clientKey.isValid()) {
            channelWriter.addAllToOutputQueue(this.outputPacketBaseQueue);
            // выставляем флаг о том что необходимо отправить данные
            this.clientKey.interestOps(SelectionKey.OP_WRITE);
            logger.debug("Changing channel mode to OP_WRITE");
        }

        MDC.remove("clientId");
    }

    // Метод отвечающий за обработку входящих сообщений
    private int processingPacket(PacketBase packetBase) {
        logger.debug("Process packet: {}", packetBase);

        // Завершаем сессию, если пришло сообщение "quit"
        if ("quit".equals(packetBase.getPacketBodyStr().toLowerCase())) {
            return -1;
        }

        // ТУТ ДОЛЖНА БЫТЬ ОБРАБОТКА

        this.outputPacketBaseQueue.add(packetBase); // кладем готовое сообщениев очередь исходящих сообщений
        logger.debug("Packet added to outgoing queue: {}", packetBase);

        return 1;
    }
}
