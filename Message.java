import java.nio.ByteBuffer;

public class Message {
    private int messageLength = 0;
    private byte[] message = null;
    public ByteBuffer readBuffer = null;

    public Message(int bufferSize) {
        readBuffer = ByteBuffer.allocate(bufferSize);
    }

    public int getMessageLength() {
        return messageLength;
    }

    public byte[] getMessage() {
        return message;
    }

    public void setMessage(byte[] message) {
        this.message = message;
        this.messageLength = this.message.length;
    }

    public ByteBuffer getByteBufferMessage() {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4 + this.messageLength);
        byte[] length = ByteBuffer.allocate(4).putInt(this.messageLength).array(); // формирует byte[] из длинны сообщения
        writeBuffer.clear(); // очищаем буфер
        writeBuffer.put(length); // записываем длинну сообщения
        writeBuffer.put(this.message); // записываем сообщение
        writeBuffer.flip(); // выставляем размер буфера в соовествии с размером записанных данных
        return writeBuffer;
    }

    public void clear() {
        this.message = null;
        this.messageLength = 0;
        this.readBuffer.clear();
    }
}
