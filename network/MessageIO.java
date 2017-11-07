package network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageIO {
    private static final int DEFAULT_MESSAGE_SIZE = 1024; // константа, размер буфера по умолчанию
    private static final int HEADER_LENGTH = Message.HEADER_SIZE; // константа, кол-ва байт для передачи длинны сообщения
    private static Logger logger = Logger.getLogger(MessageIO.class.getName());

    private final SelectionKey clientKey; // ключ
    private final int MESSAGE_SIZE; // размер буфера
    private final SocketChannel clientChannel; // канал клиента
    private final ConcurrentLinkedQueue<Message> outputQueue = new ConcurrentLinkedQueue<Message>();

    private Message message; // текущее сообщение
    private boolean hasMessageTail = false; // признак того, что сообщение пришло не полностью

    // Конструктор по умолчанию. Устанавливает максимальный размер сообщения по умолчанию
    public MessageIO(SelectionKey clientKey) throws IOException {
        this(clientKey, DEFAULT_MESSAGE_SIZE);
    }

    // Конструктор. Устанавлиеваем максимальный размер сообщения из переданного параметра
    public MessageIO(SelectionKey clientKey, int MESSAGE_SIZE) throws IOException {
        // проверяем валидность параметров
        if(clientKey != null &&
                (MESSAGE_SIZE > 0 && MESSAGE_SIZE <= Integer.MAX_VALUE )) {
            this.clientKey = clientKey;
            this.MESSAGE_SIZE = MESSAGE_SIZE;

            message = new Message(this.MESSAGE_SIZE);
            clientChannel = (SocketChannel) this.clientKey.channel();
        }
        else throw new IOException("Invalid params value");
    }

    // Метод добавляет считанное сообщение в очередь отправки
    public void addToOutputQueue(Message message) {
        if(message != null)
            this.outputQueue.add(message);
    }

    // Метод возвращает признак наличия других сообщений в буфере
    public boolean hasMessageTail() {
        return hasMessageTail;
    }

    // Метод считывает данные из канала и сохраняет в классе Message.
    // Возвращает очередь вх. сообщений.
    public Queue<Message> read() throws IOException {
        int numRead; // будет хранить кол-во считанных байтов или статус
        int position; // будет хранить текущую позицию буфера

        try {
            // считывам данные из канала и запоминаем кол-во считанных байт
            numRead = this.clientChannel.read(this.message.readBuffer);
        } catch (IOException e) { // ошибка чтения
            logger.log(Level.SEVERE,"Client unexpectedly disconnected", e);
            IOException exception = new IOException("Client unexpectedly disconnected");
            exception.addSuppressed(e);
            throw exception;
        }

        if(numRead == -1) { // Штатно закрылся канал. Завершаем сессию
            logger.fine("Client closed connection");
            throw new IOException("Client closed connection");
        }
        else if(numRead == 0) { // Пустое сообщение. Завершаем сессию
            logger.fine("No data received. Close connection");
            throw new IOException("No data received. Close connection");
        }

        Queue<Message> messageQueue = new LinkedBlockingQueue<>();
        this.hasMessageTail = false;
        int hasBytes = 0; // хранит кол-во оставщихся в буфера байт

        // Цикл считывания Message из буфера
        do {
            position = this.message.readBuffer.position(); // запоминаем текущую позицию буфера

            // Проверяем пришел ли заголовок сообщения
            if (position < HEADER_LENGTH) {
                // Заголовок первого сообщения пришел не полностью.
                // Прерываем цикл и ждем следующей порции данных.
                logger.fine("Received packet is too small: header < 12");
                this.hasMessageTail = true;
                if(position != 0) {
                    // проверяем что это не первый проход
                    this.message.readBuffer.position(position); // устанавливаем конечный элемент буфера
                    this.message.readBuffer.limit(this.MESSAGE_SIZE);
                }
                break;
            }

            // Заголовок сообщения полностью дошел. Обрабатываем дальше...
            this.message.readBuffer.position(0); // устанавливаем буфер в начало чтобы прочитать длинну сообщения
            int messageLength = this.message.readBuffer.getInt(); // читаем длинну сообщения

            // Проверка валидности длинны сообщения (0 < messageLength < размер буфера)
            if(messageLength <= 0 || messageLength > (this.MESSAGE_SIZE - Message.LENGTH_SIZE)) {
                // Длинна пакета не верная.  Прерываем цикл обработки буфера.
                System.out.println("Wrong packet size. May be packet is corrupt");
                logger.fine("Wrong packet size. May be packet is corrupt");
                break;
            }

            // Проверям пришло ли сообщение полностью
            if (messageLength > (position - Message.LENGTH_SIZE)) {
                // Тело сообщения пришло не полностью.
                // Прерываем цикл и ждем следующей порции данных.
                logger.fine("Received packet is too small: body < len (" + messageLength + ")");
                this.message.readBuffer.position(position); // устанавливаем конечный элемент буфера
                this.message.readBuffer.limit(this.MESSAGE_SIZE); // устанавливаем лимит буфера
                this.hasMessageTail = true; // устанавливаем флаг сообщение пришло не полностью
                break;
            }

            // Сообщение полностью дошло
            this.message.readBuffer(messageLength); // копируем сообщение в объект сообщения
            messageQueue.add(this.message.clone()); // записываем в буффер вх. сообщений новое
            this.message.clear(); // очищаем объект

            // Проверям есть ли в буфере ещё сообщения
            hasBytes = (position - Message.LENGTH_SIZE) - messageLength;
            if(hasBytes > 0) {
                // Сообщение ещё есть
                this.message.readBuffer.position(hasBytes); // устанавливаем правильную позицию в буфере
                this.message.readBuffer.limit(hasBytes); // устанавливаем лимит буфера
            }

        } while (!this.hasMessageTail && hasBytes > 0); // если ещё сообщения есть, то читаем снова

        // Проверям что очередь вх. сообщение не пуст
        if(messageQueue.size() > 0)
            return messageQueue; // возвращаем очередь вх. сообщений
        else return null;
    }

    // Метод отправляет данные из очереди отправки в канал клиента
    // Возвращает статус отправки (0 - что-то не отправлено , 1 - успешная отправка и переключемся в режим "читать")
    public int write() throws IOException {
        Iterator<Message> iterator = this.outputQueue.iterator(); // формируем итератор исходящей очереди
        int numWrite = 0; // будет хранить кол-во записанных байтов

        while (iterator.hasNext()) { // проходим по элементам исходящей очереди
            Message msg = iterator.next(); // запоминаем текущий элемент
            ByteBuffer bb = msg.getByteBufferMessage(); // преобразовываем в bytebuffer

            if (bb.position() < bb.limit()) { // проверям корректность буфера
                try {
                    numWrite = this.clientChannel.write(bb); // записываем в канал данные из буфера и получам кол-во записанных байтов
                } catch (IOException e) {
                    logger.log(Level.SEVERE,"Client unexpectedly disconnected", e);
                    IOException exception = new IOException("Client unexpectedly disconnected");
                    exception.addSuppressed(e);
                    throw exception;
                }

                if (numWrite == -1) {
                    // Штатно закрылся канал. Закрываем....
                    logger.fine("Channel is closed");
                    throw new IOException("Channel is closed");
                } else if (numWrite == 0) {
                    // заполнились внутренние буфера джавы и операционки.
                    logger.fine("Write buffer is full...");
                    break;
                } else if (numWrite > 0) {
                    // записали полностью или частично буфер.
                    if (bb.remaining() == 0) {
                        // полностью буфер записали, удаляем из списка.
                        logger.fine("Message send successful");
                        iterator.remove();
                    } else {
                        // записан текущий буфер частично
                        // а значит - прерываем цикл обхода буферов.
                        logger.fine("Message send not full");
                        break;
                    }
                }
            }
            // если список буферов пуст, то есть, записали все, то переключаемся в режим "хочу читать!"
            if (this.outputQueue.size() == 0) {
                return 1;
            }
        }
        return 0;
    }
}
