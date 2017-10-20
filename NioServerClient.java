import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

// Класс экземпляра клиента на сервере
public class NioServerClient {
    private static final int DEFAULT_BUFFER_SIZE = 512; // константа, размер буфера по умолчанию
    private static final int BYTES_MESSAGE_LENGTH = 4; // константа, кол-ва байт для передачи длинны сообщения
    private static final int DEFAULT_MESSAGE_SIZE = 512 - BYTES_MESSAGE_LENGTH; // константа, размер сообщения по умолчанию

    private final SelectionKey clientKey; // ключ
    private final int MESSAGE_SIZE; // размер сообщения
    private final int BUFFER_SIZE; // размер буфера
    private final SocketChannel clientChannel; // канал клиента
    private final LinkedList<ByteBuffer> outgoingBuffers = new LinkedList<ByteBuffer>(); // очередь отправки

    //private ByteBuffer readBuffer; // буфер получения
    private ByteBuffer writeBuffer; // буфер отправки

    private Message message; // текущий сообщение


    public NioServerClient(SelectionKey clientKey) {
        this(clientKey, DEFAULT_MESSAGE_SIZE, DEFAULT_BUFFER_SIZE);
    }

    public NioServerClient(SelectionKey clientKey, int MESSAGE_SIZE, int BUFFER_SIZE) {
        this.clientKey = clientKey;
        this.MESSAGE_SIZE = MESSAGE_SIZE;
        this.BUFFER_SIZE = BUFFER_SIZE;
        //readBuffer = ByteBuffer.allocate(this.BUFFER_SIZE);
        writeBuffer = ByteBuffer.allocate(this.BUFFER_SIZE);
        message = new Message(this.BUFFER_SIZE);
        clientChannel = (SocketChannel) this.clientKey.channel();
    }

    public void read() throws IOException {
        int numRead; // будет хранить кол-во считанных байтов или статус
        int position; // будт хранить текущую позицию буфера

        try {
            numRead = this.clientChannel.read(this.message.readBuffer); // считывам данные из канала и запоминаем кол-во считанных байт
            position = this.message.readBuffer.position();
        } catch (IOException e) { // ощибка чтения
            this.closeChannel();
            System.out.println("Client unexpectedly disconnected");
            return;
        }

        if(numRead == -1) {
            // Штатно закрылся канал
            this.closeChannel();
            this.message.clear();
            System.out.println("Client closed connection");
            return;
        }
        if(numRead == 0) {
            // Хрень какаято
            this.closeChannel();
            this.message.clear();
            System.out.println("No data received");
            return;
        }
        if(position < BYTES_MESSAGE_LENGTH) {
            // размер сообщения пришел не полностью
            System.out.println("Packet too small: packet size < 4");
            return;
        }

        this.message.readBuffer.position(0); // устанавливаем буфер в начало что заного прочитать сообщение
        int messageLength = this.message.readBuffer.getInt(); // читаем длинну сообщения

        if(messageLength > (position - BYTES_MESSAGE_LENGTH)) {
            // сообщение пришло не полностью
            this.message.readBuffer.position(position);
            System.out.println("Packet too small: message not full. len=" + messageLength + " num=" + numRead);
            return;
        }

        byte[] messageByteArray = new byte[messageLength]; // создаём массив для сохранения сообщения
        this.message.readBuffer.get(messageByteArray); // читаем сообщение из буфера и сохраняем в массив байтов

        System.out.println("Message: " + new String(messageByteArray, "UTF-8"));

        message.setMessage(messageByteArray);

        outgoingBuffers.add(message.getByteBufferMessage()); // кладем готовое сообщениев массий исходящих сообщений
        this.clientKey.interestOps(SelectionKey.OP_WRITE); // выставляем флаг о том что необходимо отправить данные
        message.clear();
    }

    public void write() {
        //String attachment = (String)clientKey.attachment();
        Iterator<ByteBuffer> iterator = this.outgoingBuffers.iterator(); // формируем итератор исходящей очереди
        int numWrite = 0; // будет хранить кол-во записанных байтов

        while (iterator.hasNext()) { // проходим по элементам исходящей очереди
            ByteBuffer bb = iterator.next(); // запоминаем текущий элемент
            if (bb.position() < bb.limit()) { // проверям корректность буфера
                try {
                    numWrite = this.clientChannel.write(bb); // записываем в канал данные из буфера и получам кол-во записанных байтов
                    System.out.println("Message was send");
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
            if (this.outgoingBuffers.size() == 0) {
                this.clientKey.interestOps(SelectionKey.OP_READ);
            }
        }

    }

    // закрывает канал и отменяет ключ
    private void closeChannel() {
        clientKey.cancel();
        try {
            if(this.clientChannel.isConnected())
                this.clientChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
