import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

// Класс экземпляра клиента на сервере
public class NioServerClient {
    private static final int DEFAULT_MESSAGE_SIZE = 512; // константа, размер сообщения по умолчанию
    private static final int DEFAULT_BUFFER_SIZE = 512; // константа, размер буфера по умолчанию

    private final SelectionKey clientKey; // ключ
    private final int MESSAGE_SIZE; // размер сообщения
    private final int BUFFER_SIZE; // размер буфера
    private final SocketChannel clientChannel; // канал клиента
    private final LinkedList<ByteBuffer> outgoingBuffers = new LinkedList<ByteBuffer>(); // очередь отправки

    private ByteBuffer readBuffer; // буфер получения
    private ByteBuffer writeBuffer; // буфер отправки


    public NioServerClient(SelectionKey clientKey) {
        this(clientKey, DEFAULT_MESSAGE_SIZE, DEFAULT_BUFFER_SIZE);
    }

    public NioServerClient(SelectionKey clientKey, int MESSAGE_SIZE, int BUFFER_SIZE) {
        this.clientKey = clientKey;
        this.MESSAGE_SIZE = MESSAGE_SIZE;
        this.BUFFER_SIZE = BUFFER_SIZE;
        readBuffer = ByteBuffer.allocate(this.BUFFER_SIZE);
        writeBuffer = ByteBuffer.allocate(this.BUFFER_SIZE);
        clientChannel = (SocketChannel) this.clientKey.channel();
    }

    public void read() throws IOException {
        readBuffer.clear(); // сбрасывам буффер
        int numRead; // будет хранить кол-во считанных байтов

        try {
            numRead = this.clientChannel.read(readBuffer); // считывам данные из канала и запоминаем кол-во считанных байт
        } catch (IOException e) { // ощибка чтения
            this.closeChannel();
            System.out.println("Client unexpectedly disconnected");
            return;
        }

        if(numRead == -1) {
            // Штатно закрылся канал
            this.closeChannel();
            System.out.println("Client closed connection");
            return;
        }

        if(numRead == 0) {
            // Хрень какаято
            this.closeChannel();
            System.out.println("No data received");
            return;
        }

        if(numRead < 4) {
            // размер сообщения пришел не полностью
            System.out.println("Packet too small: packet size < 4");
            return;
        }

        readBuffer.flip(); // выставляем размер буфера в соовествии с размером прочитанных данных
        int messageLength = readBuffer.getInt(); // читаем длинну сообщения

        if(messageLength > (numRead - 4)) {
            // сообщение пришло не полностью
            System.out.println("Packet too small: message not full. len=" + messageLength + " num=" + numRead);
            return;
        }

        byte[] messageByteArray = new byte[messageLength]; // создаём массив для сохранения сообщения
        readBuffer.get(messageByteArray); // читаем сообщение из буфера и сохраняем в массив байтов

        System.out.println("Message: " + new String(messageByteArray, "UTF-8"));

        createMessage(messageByteArray); // создаём ByteBuffer, пишем туда сообщения
        outgoingBuffers.add(writeBuffer); // кладем готовое сообщениев массий исходящих сообщений
        this.clientKey.interestOps(SelectionKey.OP_WRITE); // выставляем флаг о том что необходимо отправить данные
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

    // метод формирует сообщение на отправку. Модифицирует переменную класса writeBuffer
    // на вход принимает сообщение для отправки в формате byte[]
    private void createMessage(byte[] byteArray) {
        int messageLength = byteArray.length; // определяем длинну сообщения
        byte[] ba = ByteBuffer.allocate(4).putInt(messageLength).array(); // формирует byte[] из длинны сообщения
        writeBuffer.clear(); // очищаем буфер
        writeBuffer.put(ba); // записываем длинну сообщения
        writeBuffer.put(byteArray); // записываем сообщение
        writeBuffer.flip(); // выставляем размер буфера в соовествии с размером записанных данных
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
