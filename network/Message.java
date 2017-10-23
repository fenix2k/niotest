package network;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class Message {
    private int messageLength = 0; // длинна тела сообщения
    private int messageId = 0; // уникальный идентификатор пакета
    private int messageType = 0; // тип сообщения
    private byte[] messageBody = null; // тело сообщения

    public ByteBuffer readBuffer = null; // буфер для сбора сообщения по частям


    public Message(int bufferSize) {
        readBuffer = ByteBuffer.allocate(bufferSize);
    }

    public int getMessageLength() {
        return messageLength;
    }

    public int getMessageId() {
        return messageId;
    }

    public int getMessageType() {
        return messageType;
    }

    public byte[] getMessageBody() {

        return messageBody;
    }

    // метод читает данные из readBuffer в соответствующие поля класса
    public void readBuffer(int messageLength) {
        this.messageLength = messageLength; // запонимаем длинну пакета

        this.readBuffer.position(4); // устанавливаем позицию на 2й байт
        this.messageId = this.readBuffer.getInt(); // запонимаем ID пакета
        this.messageType = this.readBuffer.getInt(); // запоминаем тип пакета

        this.messageBody = new byte[this.messageLength - 4 - 4]; // выделяем память под тело сообщения
        this.readBuffer.get(this.messageBody); // запоминаем тело сообщения

        this.readBuffer.clear(); // очищаем буфер
    }

    // преобразовываем Message в ByteBuffer
    public ByteBuffer getByteBufferMessage() {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4 + 4 + 4 + this.messageLength);
        byte[] length = ByteBuffer.allocate(4).putInt(this.messageLength).array(); // формирует byte[] из длинны сообщения
        byte[] id = ByteBuffer.allocate(4).putInt(this.messageId).array(); // формирует byte[] из id сообщения
        byte[] type = ByteBuffer.allocate(4).putInt(this.messageType).array(); // формирует byte[] из типа сообщения

        writeBuffer.clear(); // очищаем буфер
        writeBuffer.put(length); // записываем длинну сообщения
        writeBuffer.put(id); // записываем id сообщения
        writeBuffer.put(type); // записываем тип сообщения
        writeBuffer.put(this.messageBody); // записываем сообщение
        writeBuffer.flip(); // выставляем размер буфера в соовествии с размером записанных данных

        return writeBuffer;
    }

    // очищаем поля класса
    public void clear() {
        this.messageLength = 0;
        this.messageId = 0;
        this.messageType = 0;
        this.messageBody = null;
        this.readBuffer.clear();
    }

    @Override
    public String toString() {
        String result = "";
        try {
            result = new String(messageBody, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }
}
