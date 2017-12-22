package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Класс сервера
public class NioServerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(NioServerLauncher.class.getName()); // логгер

    private static String configFile = "config.properties";

    private static String serverIP;
    private static int serverPort;
    private static AppSettings config;

    // TO DO:
    //

    public static void main(String[] args) {
        logger.info("PROGRAM STARTED");
        config = AppSettings.getInstance();

        // Проверяем существует ли конфигурационный файл.
        // Если нет, то создаём с параметрами по-умолчанию
        // Загружаем файл с параметрами конфигурации
        if(!config.loadOrCreate(configFile)) {
            logger.error("Cannot read config file. Terminate server.");
        }
        else {
            serverIP = config.SERVER_IP;
            serverPort = config.SERVER_PORT;

            // Запуск экземпляра сервера
            NioServer nioServer = new NioServer(serverIP, serverPort); // создаём экземпляр сервера
            Thread nioThread = new Thread(nioServer); // создаём экземпляр потока
            nioThread.setDaemon(false); // делаем его обычным потоком
            nioThread.start(); // запускаем поток сервера

            // Запуск консоли
            ConsoleHandler consoleHandler = new ConsoleHandler(nioServer);
            Thread consoleThread = new Thread(consoleHandler);
            consoleThread.start();

            try {
                nioThread.join();
            } catch (InterruptedException e) {
                logger.error("Server thread interrupted");
            }
        }

        logger.info("PROGRAM STOPPED");
    }

}
