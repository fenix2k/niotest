package network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChannelWriter {
    private static final Logger logger = LoggerFactory.getLogger(ChannelWriter.class.getName());

    private final SelectionKey clientKey; // ключ
    private final SocketChannel clientChannel; // канал клиента
    private final ConcurrentLinkedQueue<PacketBase> outputQueue = new ConcurrentLinkedQueue<PacketBase>();

    // Конструктор по умолчанию. Устанавливает максимальный размер сообщения по умолчанию
    public ChannelWriter(SelectionKey clientKey) throws IOException {
        this.clientKey = clientKey;
        this.clientChannel = (SocketChannel) clientKey.channel();
    }

    // Метод добавляет считанное сообщение в очередь отправки
    public void addAllToOutputQueue(Queue<PacketBase> packetBases) {
        if(packetBases != null)
            this.outputQueue.addAll(packetBases);
    }

    // Метод отправляет данные из очереди отправки в канал клиента
    // Возвращает статус отправки (0 - что-то не отправлено , 1 - успешная отправка и переключемся в режим "читать")
    public int write() throws IOException {
        Iterator<PacketBase> iterator = this.outputQueue.iterator(); // формируем итератор исходящей очереди
        int numWrite = 0; // будет хранить кол-во записанных байтов

        while (iterator.hasNext()) { // проходим по элементам исходящей очереди
            PacketBase packetBase = iterator.next(); // запоминаем текущий элемент
            ByteBuffer bb = packetBase.getByteBufferMessage(); // преобразовываем в bytebuffer

            if (bb.position() < bb.limit()) { // проверям корректность буфера
                try {
                    numWrite = this.clientChannel.write(bb); // записываем в канал данные из буфера и получам кол-во записанных байтов
                } catch (IOException e) {
                    IOException exception = new IOException("Client unexpectedly disconnected");
                    exception.addSuppressed(e);
                    throw exception;
                }

                if (numWrite == -1) {
                    // Штатно закрылся канал. Закрываем....
                    throw new IOException("Channel is closed");
                } else if (numWrite == 0) {
                    // заполнились внутренние буфера джавы и операционки.
                    logger.debug("Write buffer is full. Break processing");
                    break;
                } else if (numWrite > 0) {
                    // записали полностью или частично буфер.
                    if (bb.remaining() == 0) {
                        // полностью буфер записали, удаляем из списка.
                        logger.debug("Packet send successful");
                        iterator.remove();
                    } else {
                        // записан текущий буфер частично
                        // а значит - прерываем цикл обхода буферов.
                        logger.debug("Packet send not full");
                        break;
                    }
                }
            }
            // если список буферов пуст, то есть, записали все, то переключаемся в режим "хочу читать!"
            if (this.outputQueue.size() == 0) {
                logger.debug("All messages was send");
                return 1;
            }
        }

        if (this.outputQueue.size() != 0) {
            logger.debug("No all messages was send");
        }
        else {
            logger.debug("No messages to send");
        }

        return 0;
    }
}
