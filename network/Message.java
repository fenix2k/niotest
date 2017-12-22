package network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Random;

public class Message {
    private static final Logger logger = LoggerFactory.getLogger(Message.class.getName());

    public static final int LENGTH_SIZE = 4; // кол-во байт выделенные под длинну сообщения
    public static final int ID_SIZE = 4; // кол-во байт выделенные под длинну идентификатора сообщения
    public static final int TYPE_SIZE = 4; // кол-во байт выделенные под длинну типа сообщения
    public static final int HEADER_SIZE = LENGTH_SIZE + ID_SIZE + TYPE_SIZE; // кол-во байт заголовка (длинна+ИД+тип)

    private final int MAX_MESSAGE_SIZE; // максимальная длинна сообщения

    // поля данных сообщения
    private int messageLength = 0; // длинна тела сообщения
    private int messageId = 0; // уникальный идентификатор сообщения
    private int messageType = 0; // тип сообщения
    private byte[] messageBody = null; // тело сообщения

    public ByteBuffer readBuffer = null; // буфер для сбора сообщения по частям

    // Конструктор используется для копирования сообщения
    public Message() {
        this.MAX_MESSAGE_SIZE = 0;
    }

    // Конструктор используется для создание нового сообщения и считывания из канала
    public Message(int bufferSize) throws IOException {
        if(bufferSize > 0 && bufferSize <= Integer.MAX_VALUE) { // проверка валидности размера буфера
            this.MAX_MESSAGE_SIZE = bufferSize; // устанавливаем максимальную длинну сообщения
            readBuffer = ByteBuffer.allocate(bufferSize); // выделяем память
        }
        else
            throw new IOException("Invalid buffer size. Must be > 0");
    }

    // геттер длинны сообщения
    public int getMessageLength() {
        return messageLength;
    }

    // геттер ИД сообщения
    public int getMessageId() {
        return messageId;
    }

    // геттер ТИПа сообщения
    public int getMessageType() {
        return messageType;
    }

    // геттер ТЕЛА сообщения
    public byte[] getMessageBody() {
        return messageBody;
    }

    // Метод заполняет поля сообщения и генерирует случайный ID
    // !!! type сделать ENUM?
    public void setMessage(int type, byte[] messageBody)
            throws IOException {
            Random rand = new Random();
            int id = rand.nextInt(Integer.MAX_VALUE);
            this.setMessage(id, type, messageBody);
    }

    // Метод заполняет поля сообщения. ID задаётся вручную
    public void setMessage(int id, int type, byte[] messageBody)
            throws IOException {
        if(id >= 0 && type >= 0 && messageBody != null) {
            this.messageId = id;
            this.messageType = type;
            this.messageBody = messageBody;
            this.messageLength = this.messageBody.length + ID_SIZE + TYPE_SIZE;
        }
        else
            throw new IOException("Invalid ID or TYPE or Message Body. Must be > 0");
    }

    // метод читает данные из readBuffer в соответствующие поля класса
    public void readBuffer(int messageLength) throws IOException {
        if(this.readBuffer == null) // проверяем валидность
            throw new IOException("Invalid readBuffer. Must be not null");
        if(messageLength <= 0 || messageLength > MAX_MESSAGE_SIZE) // проверяем валидность
            throw new IOException("Invalid messageLength. Must be: 0 < messageLength < " + MAX_MESSAGE_SIZE);

        this.messageLength = messageLength; // запонимаем длинну пакета
        this.readBuffer.position(LENGTH_SIZE); // устанавливаем позицию на 5й байт
        this.messageId = this.readBuffer.getInt(); // запонимаем ID пакета
        this.messageType = this.readBuffer.getInt(); // запоминаем тип пакета

        this.messageBody = new byte[this.messageLength - ID_SIZE - TYPE_SIZE]; // выделяем память под тело сообщения
        this.readBuffer.get(this.messageBody); // запоминаем тело сообщения

        // удаляем из буфера прочитанное сообщение из начала и сдвигаем весь массив
        int endBuffer = this.readBuffer.limit() - (messageLength + LENGTH_SIZE);
        byte[] cutBuffer = this.readBuffer.array();
        this.readBuffer.position(0);
        this.readBuffer.put(cutBuffer, messageLength + LENGTH_SIZE, endBuffer);

        this.readBuffer.clear(); // очищаем буфер
    }

    // преобразовываем Message в ByteBuffer
    public ByteBuffer getByteBufferMessage() {
        ByteBuffer writeBuffer = ByteBuffer.allocate(HEADER_SIZE + this.messageLength);
        byte[] length = ByteBuffer.allocate(LENGTH_SIZE).putInt(this.messageLength).array(); // формирует byte[] из длинны сообщения
        byte[] id = ByteBuffer.allocate(ID_SIZE).putInt(this.messageId).array(); // формирует byte[] из id сообщения
        byte[] type = ByteBuffer.allocate(TYPE_SIZE).putInt(this.messageType).array(); // формирует byte[] из типа сообщения

        writeBuffer.clear(); // очищаем буфер
        writeBuffer.put(length); // записываем длинну сообщения
        writeBuffer.put(id); // записываем id сообщения
        writeBuffer.put(type); // записываем тип сообщения
        writeBuffer.put(this.messageBody); // записываем сообщение
        writeBuffer.flip(); // выставляем размер буфера в соовествии с размером записанных данных

        return writeBuffer;
    }

    // преобразовываем Message в byte[]
    public byte[] getByteArrayMessage() throws IOException {
        if(this.messageLength <= 0 || this.messageBody == null) {
            throw new IOException("Invalid message");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ByteBuffer.allocate(LENGTH_SIZE).putInt(this.messageLength).array());
        baos.write(ByteBuffer.allocate(ID_SIZE).putInt(this.messageId).array());
        baos.write(ByteBuffer.allocate(TYPE_SIZE).putInt(this.messageType).array());
        baos.write(this.messageBody);
        return baos.toByteArray();
    }

    // очищаем поля класса
    public void clear() {
        this.messageLength = 0;
        this.messageId = 0;
        this.messageType = 0;
        this.messageBody = null;
        this.readBuffer.clear();
    }

    public Message clone() {
        Message newMessage = new Message();
        newMessage.messageLength = this.messageLength;
        newMessage.messageId = this.messageId;
        newMessage.messageType = this.messageType;
        newMessage.messageBody = this.messageBody;
        return newMessage;
    }

    @Override
    public String toString() {
        String result = "";
        try {
            result = new String("[" + Integer.toString(this.messageLength) + ",");
            result += new String(Integer.toString(this.messageId) + ",");
            result += new String(Integer.toString(this.messageType) + "] ");
            result += new String(this.messageBody, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.debug("Exception: ", e);
        }
        return result;
    }

    public String getMessage() {
        String result = null;
        try {
            result = new String(this.messageBody, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.debug("Exception: ", e);
        }
        return result;
    }
}
