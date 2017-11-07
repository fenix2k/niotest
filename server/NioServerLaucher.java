package server;


import java.io.IOException;
import java.util.logging.*;

// Класс сервера
public class NioServerLaucher {
    private static Logger logger = Logger.getLogger(NioServer.class.getName()); // логгер

    // TO DO:
    // +1. При отключении клиента убивать сессию. Эксепшены прокидывать в одно место для обработки.
    // +2. Метод регистрации клиента перенести в NioServerClient. Добавлять и удалять сессии делать в клиенте.
    // 3. В клиенте пакеты не всегда все приходят. длинна сообщения считывается не верно.
    // Сделать проверки. Использовать класс Message
    // 4. если много сообщений на сервер, то он не отвечает на все. Пропадают ответы. Возможно буфера не хватает

    public static void main(String[] args) {
        // настраиваем логирование (уровень, файл, формат)
        setLoggerSettings();

        // запуск экземпляра сервера
        logger.fine("===================START=====================");
        NioServer nio = new NioServer(8000); // создаём экземпляр сервера
        Thread nioThread = new Thread(nio); // создаём экземпляр потока
        nioThread.setDaemon(false); // делаем его обычным потоком
        nioThread.start(); // запускаем поток сервера
    }

    // Метод инициализации логгера
    private static void setLoggerSettings() {
        Level fileLevel = Level.FINE; // уровень логирования файла
        Level consoleLevel = Level.INFO; // уровень логирования в консоли

        Handler handlerFile;
        try {
            Logger.getLogger("").setLevel(Level.ALL); // все уровни
            handlerFile = new FileHandler("D:\\_Sources\\fenix\\_logs\\nioserver.log", 50000, 4, false);
            handlerFile.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(handlerFile);
        } catch(IOException e) {
            System.out.println("Failed to open log file");
            return;
        }
        for (Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(consoleLevel); // указываем тотже уровень для обработчика
        }
        handlerFile.setLevel(fileLevel);
    }
}
