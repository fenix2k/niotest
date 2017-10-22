package server;


// Класс сервера
public class NioServerLaucher {
    public static void main(String[] args) {
        // запуск экземпляра сервера
        NioServer nio = new NioServer(8000);
        Thread nioThread = new Thread(nio);
        nioThread.setDaemon(false);
        nioThread.start();
    }
}
