package network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

public class MessageIO {
    private static final int DEFAULT_BUFFER_SIZE = 512; // константа, размер буфера по умолчанию
    private static final int HEADER_LENGTH = 12; // константа, кол-ва байт для передачи длинны сообщения
    private static final int DEFAULT_MESSAGE_SIZE = 512 - HEADER_LENGTH; // константа, размер сообщения по умолчанию

    private final SelectionKey clientKey; // ключ
    private final int MESSAGE_SIZE; // размер сообщения
    private final int BUFFER_SIZE; // размер буфера
    private final SocketChannel clientChannel; // канал клиента

    public final LinkedList<Message> outgoingBuffer = new LinkedList<>(); // очередь отправки

    private ByteBuffer writeBuffer; // буфер отправки

    private Message message; // текущее сообщение


    public MessageIO(SelectionKey clientKey) {
        this(clientKey, DEFAULT_MESSAGE_SIZE, DEFAULT_BUFFER_SIZE);
    }

    public MessageIO(SelectionKey clientKey, int MESSAGE_SIZE, int BUFFER_SIZE) {
        this.clientKey = clientKey;
        this.MESSAGE_SIZE = MESSAGE_SIZE;
        this.BUFFER_SIZE = BUFFER_SIZE;

        writeBuffer = ByteBuffer.allocate(this.BUFFER_SIZE);
        message = new Message(this.BUFFER_SIZE);
        clientChannel = (SocketChannel) this.clientKey.channel();
    }

    public Message read() throws IOException {
        int numRead; // будет хранить кол-во считанных байтов или статус
        int position; // будт хранить текущую позицию буфера

        try {
            numRead = this.clientChannel.read(this.message.readBuffer); // считывам данные из канала и запоминаем кол-во считанных байт
            position = this.message.readBuffer.position();
        } catch (IOException e) { // ощибка чтения
            this.closeChannel();
            System.out.println("Client unexpectedly disconnected");
            return null;
        }

        if(numRead == -1) {
            // Штатно закрылся канал. Завершаем сессию
            this.closeChannel();
            this.message.clear();
            System.out.println("Client closed connection");
            return null;
        }
        if(numRead == 0) {
            // Пустое сообщение. Завершаем сессию
            this.closeChannel();
            this.message.clear();
            System.out.println("No data received");
            return null;
        }
        if(position < HEADER_LENGTH) {
            // размер сообщения пришел не полностью. Ждём дальше...
            System.out.println("Packet too small: packet size < 12");
            return null;
        }

        // длинна сообщения полностью дошла
        this.message.readBuffer.position(0); // устанавливаем буфер в начало чтобы заного прочитать сообщение
        int messageLength = this.message.readBuffer.getInt(); // читаем длинну сообщения

        if(messageLength > (position - 4)) {
            // сообщение пришло не полностью. Ждём остальное...
            this.message.readBuffer.position(position); // устанавливаем конечный элемент буфера
            System.out.println("Packet too small: message not full. len=" + messageLength + " num=" + numRead);
            return null;
        }

        // Сообщение полностью дошло
        message.readBuffer(messageLength); // копируем сообщение в объект сообщения

        // тут косяк!
        //message.readBuffer.clear(); // после успешной отправки затираем сообщение

        return message;
    }

    public void write() {
        Iterator<Message> iterator = this.outgoingBuffer.iterator(); // формируем итератор исходящей очереди
        int numWrite = 0; // будет хранить кол-во записанных байтов

        while (iterator.hasNext()) { // проходим по элементам исходящей очереди
            Message msg = iterator.next(); // запоминаем текущий элемент
            ByteBuffer bb = msg.getByteBufferMessage();

            if (bb.position() < bb.limit()) { // проверям корректность буфера
                try {
                    numWrite = this.clientChannel.write(bb); // записываем в канал данные из буфера и получам кол-во записанных байтов
                    System.out.println("network.Message was send");
                } catch (IOException e) {
                    System.out.println("Client was disconnected");
                    this.closeChannel();
                }

                if (numWrite == -1) {
                    // Штатно закрылся канал. Закрываем....
                    this.closeChannel();
                    break;
                } else if (numWrite == 0) {
                    // заполнились внутренние буфера джавы и операционки.
                    break;
                } else if (numWrite > 0) {
                    // записали полностью или частично буфер.
                    if (bb.remaining() == 0) {
                        // полностью буфер записали, удаляем из списка.
                        iterator.remove();
                    } else {
                        // записан текущий буфер частично
                        // а значит - прерываем цикл обхода буферов.
                        break;
                    }
                }
            }
            // если список буферов пуст, то есть, записали все, то переключаемся в режим "хочу читать!"
            if (this.outgoingBuffer.size() == 0) {
                this.clientKey.interestOps(SelectionKey.OP_READ);
            }
        }

    }

    // закрывает канал и отменяет ключ
    public void closeChannel() {
        clientKey.cancel();
        try {
            if(this.clientChannel.isConnected())
                this.clientChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
