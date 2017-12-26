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
import java.util.concurrent.LinkedBlockingQueue;

// Класс используется при чтении и записи данных в канал.
// Принимает данные из канала (в т.ч. фрагментированные), отпраляет.
public class NetworkIO {
    private static final Logger logger = LoggerFactory.getLogger(NetworkIO.class.getName());

    private static final int DEFAULT_MESSAGE_SIZE = 1024; // константа, размер буфера по умолчанию
    private static final int HEADER_LENGTH = Packet.HEADER_SIZE; // константа, кол-ва байт для передачи длинны сообщения

    private final SelectionKey clientKey; // ключ
    private final int MESSAGE_SIZE; // размер буфера
    private final SocketChannel clientChannel; // канал клиента
    private final ConcurrentLinkedQueue<Packet> outputQueue = new ConcurrentLinkedQueue<Packet>();

    private Packet packet; // текущее сообщение
    private boolean hasMessageTail = false; // признак того, что сообщение пришло не полностью

    // Конструктор по умолчанию. Устанавливает максимальный размер сообщения по умолчанию
    public NetworkIO(SelectionKey clientKey) throws IOException {
        this(clientKey, DEFAULT_MESSAGE_SIZE);
    }

    // Конструктор. Устанавлиеваем максимальный размер сообщения из переданного параметра
    public NetworkIO(SelectionKey clientKey, int MESSAGE_SIZE) throws IOException {
        // проверяем валидность параметров
        if(clientKey != null &&
                (MESSAGE_SIZE > 0 && MESSAGE_SIZE <= Integer.MAX_VALUE )) {
            this.clientKey = clientKey;
            this.MESSAGE_SIZE = MESSAGE_SIZE;

            packet = new Packet(this.MESSAGE_SIZE);
            clientChannel = (SocketChannel) this.clientKey.channel();
        }
        else
            throw new IOException("Invalid params value");
    }

    // Метод добавляет считанное сообщение в очередь отправки
    public void addAllToOutputQueue(Queue<Packet> packets) {
        if(packet != null)
            this.outputQueue.addAll(packets);
    }

    // Метод возвращает признак наличия других сообщений в буфере
    public boolean hasMessageTail() {
        return hasMessageTail;
    }

    // Метод считывает данные из канала и сохраняет в классе Packet.
    // Возвращает очередь вх. сообщений.
    public Queue<Packet> read() throws IOException {
        int numRead; // будет хранить кол-во считанных байтов или статус
        int position; // будет хранить текущую позицию буфера

        try {
            // считывам данные из канала и запоминаем кол-во считанных байт
            numRead = this.clientChannel.read(this.packet.readBuffer);
        } catch (IOException e) { // ошибка чтения
            IOException exception = new IOException("Client unexpectedly disconnected");
            exception.addSuppressed(e);
            throw exception;
        }

        if(numRead == -1) { // Штатно закрылся канал. Завершаем сессию
            throw new IOException("Client was closed connection");
        }
        if(numRead == 0) { // Пустое сообщение. Завершаем сессию
            throw new IOException("No data received. Close connection");
        }

        Queue<Packet> packetQueue = new LinkedBlockingQueue<>();
        this.hasMessageTail = false;
        int hasBytes = 0; // хранит кол-во оставщихся в буфера байт

        // Цикл считывания Packet из буфера
        do {
            position = this.packet.readBuffer.position(); // запоминаем текущую позицию буфера

            // Проверяем пришел ли заголовок сообщения
            if (position < HEADER_LENGTH) {
                // Заголовок первого сообщения пришел не полностью.
                // Прерываем цикл и ждем следующей порции данных.
                logger.debug("Received packet is too small: header < 12");
                this.hasMessageTail = true;
                if(position != 0) {
                    // проверяем что это не первый проход
                    this.packet.readBuffer.position(position); // устанавливаем конечный элемент буфера
                    this.packet.readBuffer.limit(this.MESSAGE_SIZE);
                }
                break;
            }

            // Заголовок сообщения полностью дошел. Обрабатываем дальше...
            this.packet.readBuffer.position(0); // устанавливаем буфер в начало чтобы прочитать длинну сообщения
            int messageLength = this.packet.readBuffer.getInt(); // читаем длинну сообщения

            // Проверка валидности длинны сообщения (0 < messageLength < размер буфера)
            if(messageLength <= 0 || messageLength > (this.MESSAGE_SIZE - Packet.LENGTH_SIZE)) {
                // Длинна пакета не верная.  Прерываем цикл обработки буфера.
                logger.debug("Wrong packet size (May be packet is corrupt)");
                // Наверно нужно сделать ресет соединения
                break;
            }

            // Проверям пришло ли сообщение полностью
            if (messageLength > (position - Packet.LENGTH_SIZE)) {
                // Тело сообщения пришло не полностью.
                // Прерываем цикл и ждем следующей порции данных.
                logger.debug("Received packet is too small: body < len ({})", messageLength);
                this.packet.readBuffer.position(position); // устанавливаем конечный элемент буфера
                this.packet.readBuffer.limit(this.MESSAGE_SIZE); // устанавливаем лимит буфера
                this.hasMessageTail = true; // устанавливаем флаг сообщение пришло не полностью
                break;
            }

            // Сообщение полностью дошло
            this.packet.readBuffer(messageLength); // копируем сообщение в объект сообщения
            packetQueue.add(this.packet.clone()); // записываем в буффер вх. сообщений новое
            this.packet.clear(); // очищаем объект

            // Проверям есть ли в буфере ещё сообщения
            hasBytes = (position - Packet.LENGTH_SIZE) - messageLength;
            if(hasBytes > 0) {
                // Сообщение ещё есть
                this.packet.readBuffer.position(hasBytes); // устанавливаем правильную позицию в буфере
                this.packet.readBuffer.limit(hasBytes); // устанавливаем лимит буфера
            }

        } while (!this.hasMessageTail && hasBytes > 0); // если ещё сообщения есть, то читаем снова

        // Проверям что очередь вх. сообщение не пуст
        if(packetQueue.size() > 0)
            return packetQueue; // возвращаем очередь вх. сообщений
        else
            return null;
    }

    // Метод отправляет данные из очереди отправки в канал клиента
    // Возвращает статус отправки (0 - что-то не отправлено , 1 - успешная отправка и переключемся в режим "читать")
    public int write() throws IOException {
        Iterator<Packet> iterator = this.outputQueue.iterator(); // формируем итератор исходящей очереди
        int numWrite = 0; // будет хранить кол-во записанных байтов

        while (iterator.hasNext()) { // проходим по элементам исходящей очереди
            Packet msg = iterator.next(); // запоминаем текущий элемент
            ByteBuffer bb = msg.getByteBufferMessage(); // преобразовываем в bytebuffer

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
