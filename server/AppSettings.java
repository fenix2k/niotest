package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class AppSettings extends Properties {
    private static final Logger logger = LoggerFactory.getLogger(AppSettings.class.getName());

    private static AppSettings config;
    static {
        config = new AppSettings();
    }

    // CONFIG
    private String configFile = "config.properties"; // Путь к файлу конфигурации
    private Map<String, String> paramsDefault = new TreeMap<>();
    {
        paramsDefault.put("server.ip", "localhost");
        paramsDefault.put("server.port", "8000");
        paramsDefault.put("server.client.thread.count", "5");
        paramsDefault.put("server.client.thread.maxcount", "10");
        paramsDefault.put("server.client.thread.keepalive", "1000");
        paramsDefault.put("network.packet.maxsize", "1024");
    }

    // SERVER SETTINGS
    public String SERVER_IP;
    public int SERVER_PORT;

    // SERVER CLIENT SETTINGS
    public int CLIENT_THREAD_COUNT;
    public int CLIENT_THREAD_MAX_COUNT;
    public int CLIENT_THREAD_KEEPALIVE;

    //NETWORK SETTINGS
    public int NET_MAX_PACKET_SIZE;

    private AppSettings() {}

    public static AppSettings getInstance() {
        return config;
    }

    // Метод создаёт конф. файл с параметрами по-умолчанию
    private boolean create(String filePath) {
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(filePath);
            config.clear();

            for(Map.Entry<String, String> pair: paramsDefault.entrySet()) {
                config.setProperty(pair.getKey(), pair.getValue());
            }

            config.store(fos,"New config created");
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    // Метод загружает настиройки из конф. файла
    public boolean load(String filePath) {
        if(!isConfigExist(filePath)) {
            return false;
        }

        FileInputStream fis;

        try {
            fis = new FileInputStream(filePath);
            config.clear();
            config.load(fis);

            SERVER_IP = getStringProperty("server.ip");
            SERVER_PORT = getIntProperty("server.port");

            CLIENT_THREAD_COUNT = getIntProperty("server.client.thread.count");
            CLIENT_THREAD_MAX_COUNT = getIntProperty("server.client.thread.maxcount");
            CLIENT_THREAD_KEEPALIVE = getIntProperty("server.client.thread.keepalive");

            NET_MAX_PACKET_SIZE = getIntProperty("network.packet.maxsize");

        } catch (IOException e) {
            return false;
        }
        configFile = filePath;
        return true;
    }

    public boolean loadOrCreate(String filePath) {
        if(!isConfigExist(filePath)) {
            this.create(filePath);
        }
        if(this.load(configFile)) {
            configFile = filePath;
            return true;
        }
        return false;
    }

    // Метод проверяет существование конф. файла
    private boolean isConfigExist(String filePath) {
        if(filePath != null && filePath.length() > 0) {
            File configFile = new File(filePath);
            if(configFile.isFile() && configFile.exists()) {
                return true;
            }
        }
        return false;
    }

    // Читает из файла параметр тика String
    private String getStringProperty(String prop) {
        String property = null;
        try {
            property = AppSettings.config.getProperty(prop);
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Read error property <{}>. Use default value", prop);
        }

        if(property == null) {
            property = getDefaultParamValue(prop);
        }

        return property;
    }

    // Читает из файла параметр тика int
    private int getIntProperty(String prop) {
        String property = null;
        try {
            property = AppSettings.config.getProperty(prop);
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Read error property <{}>. Use default value", prop);
        }

        if(property == null) {
            property = getDefaultParamValue(prop);
        }

        int result = 0;
        try {
            result = Integer.parseInt(property);
        } catch (Exception e) {
            logger.warn("Property cast error: {}", property);
        }

        return result;
    }

    // Возвращает значение параметра по-умолчанию
    private String getDefaultParamValue(String param) {
        String defaultValue = "";
        if(this.paramsDefault.containsKey(param)) {
            defaultValue = this.paramsDefault.get(param);
        }
        return defaultValue;
    }

    // Метод выводит текущий конфиг в консоль
    public void printConfig() {
        System.out.println("CURRENT CONFIG:");

        System.out.println("AppSettings file path = " + configFile);

        System.out.println("Server IP = " + SERVER_IP);
        System.out.println("Server PORT = " + SERVER_PORT);

        System.out.println("Client thread count = " + CLIENT_THREAD_COUNT);
        System.out.println("Client thread max count = " + CLIENT_THREAD_MAX_COUNT);
        System.out.println("Client thread keepalive timeout = " + CLIENT_THREAD_KEEPALIVE);

        System.out.println("Network max packet size = " + NET_MAX_PACKET_SIZE);

    }

}
