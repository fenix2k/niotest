package network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ChannelReader {
    private static final Logger logger = LoggerFactory.getLogger(ChannelReader.class.getName());

    private static final int DEFAULT_MESSAGE_SIZE = 1024; // константа, размер буфера по умолчанию
    private static final int HEADER_LENGTH = PacketBase.HEADER_SIZE; // константа, кол-ва байт для передачи длинны сообщения

    private final SelectionKey clientKey; // ключ
    private final int MESSAGE_SIZE; // размер буфера
    private final SocketChannel clientChannel; // канал клиента


    private PacketBase packetBase; // текущее сообщение
    private boolean hasMessageTail = false; // признак того, что сообщение пришло не полностью

    // Конструктор по умолчанию. Устанавливает максимальный размер сообщения по умолчанию
    public ChannelReader(SelectionKey clientKey) throws IOException {
        this(clientKey, DEFAULT_MESSAGE_SIZE);
    }

    // Конструктор. Устанавлиеваем максимальный размер сообщения из переданного параметра
    public ChannelReader(SelectionKey clientKey, int MESSAGE_SIZE) throws IOException {
        // проверяем валидность параметров
        if(clientKey != null &&
                (MESSAGE_SIZE > 0 && MESSAGE_SIZE <= Integer.MAX_VALUE )) {
            this.clientKey = clientKey;
            this.MESSAGE_SIZE = MESSAGE_SIZE;

            packetBase = new PacketBase(this.MESSAGE_SIZE);
            clientChannel = (SocketChannel) this.clientKey.channel();
        }
        else
            throw new IOException("Invalid params value");
    }

    // Метод возвращает признак наличия других сообщений в буфере
    public boolean hasMessageTail() {
        return hasMessageTail;
    }

    // Метод считывает данные из канала и сохраняет в классе PacketBase.
    // Возвращает очередь вх. сообщений.
    public Queue<PacketBase> read() throws IOException {
        int numRead; // будет хранить кол-во считанных байтов или статус
        int position; // будет хранить текущую позицию буфера

        try {
            // считывам данные из канала и запоминаем кол-во считанных байт
            numRead = this.clientChannel.read(this.packetBase.readBuffer);
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

        Queue<PacketBase> inputPacketQueue = new LinkedBlockingQueue<>();
        this.hasMessageTail = false;
        int hasBytes = 0; // хранит кол-во оставщихся в буфера байт

        // Цикл считывания PacketBase из буфера
        do {
            position = this.packetBase.readBuffer.position(); // запоминаем текущую позицию буфера

            // Проверяем пришел ли заголовок сообщения
            if (position < HEADER_LENGTH) {
                // Заголовок первого сообщения пришел не полностью.
                // Прерываем цикл и ждем следующей порции данных.
                logger.debug("Received packet is too small: header < 12");
                this.hasMessageTail = true;
                if(position != 0) {
                    // проверяем что это не первый проход
                    this.packetBase.readBuffer.position(position); // устанавливаем конечный элемент буфера
                    this.packetBase.readBuffer.limit(this.MESSAGE_SIZE);
                }
                break;
            }

            // Заголовок сообщения полностью дошел. Обрабатываем дальше...
            this.packetBase.readBuffer.position(0); // устанавливаем буфер в начало чтобы прочитать длинну сообщения
            int messageLength = this.packetBase.readBuffer.getInt(); // читаем длинну сообщения

            // Проверка валидности длинны сообщения (0 < messageLength < размер буфера)
            if(messageLength <= 0 || messageLength > (this.MESSAGE_SIZE - PacketBase.LENGTH_SIZE)) {
                // Длинна пакета не верная.  Прерываем цикл обработки буфера.
                logger.debug("Wrong packet size (May be packet is corrupt)");
                // Наверно нужно сделать ресет соединения
                break;
            }

            // Проверям пришло ли сообщение полностью
            if (messageLength > (position - PacketBase.LENGTH_SIZE)) {
                // Тело сообщения пришло не полностью.
                // Прерываем цикл и ждем следующей порции данных.
                logger.debug("Received packet is too small: body < len ({})", messageLength);
                this.packetBase.readBuffer.position(position); // устанавливаем конечный элемент буфера
                this.packetBase.readBuffer.limit(this.MESSAGE_SIZE); // устанавливаем лимит буфера
                this.hasMessageTail = true; // устанавливаем флаг сообщение пришло не полностью
                break;
            }

            // Сообщение полностью дошло
            this.packetBase.readBuffer(messageLength); // копируем сообщение в объект сообщения
            inputPacketQueue.add(this.packetBase.clone()); // записываем в буффер вх. сообщений новое
            //inputPacketQueue.add(PacketFactory.createPacket(this.packetBase)); // записываем в буффер вх. сообщений новое
            this.packetBase.clear(); // очищаем объект

            // Проверям есть ли в буфере ещё сообщения
            hasBytes = (position - PacketBase.LENGTH_SIZE) - messageLength;
            if(hasBytes > 0) {
                // Сообщение ещё есть
                this.packetBase.readBuffer.position(hasBytes); // устанавливаем правильную позицию в буфере
                this.packetBase.readBuffer.limit(hasBytes); // устанавливаем лимит буфера
            }

        } while (!this.hasMessageTail && hasBytes > 0); // если ещё сообщения есть, то читаем снова

        // Проверям что очередь вх. сообщение не пуст
        if(inputPacketQueue.size() > 0)
            return inputPacketQueue; // возвращаем очередь вх. сообщений
        else
            return null;
    }
}
