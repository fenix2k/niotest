package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class ConsoleHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleHandler.class.getName());
    private final NioServer nioServer;
    private final BufferedReader consoleInput;

    public ConsoleHandler(NioServer server) {
        this.nioServer = server;
        this.consoleInput = new BufferedReader(new InputStreamReader(System.in));
    }

    // Метод читает строку с консоли
    private String readConsole() throws IOException {
        return this.consoleInput.readLine();
    }

    // Метод выводит данные в консоль
    private void writeConsole(String msg) {
        System.out.println(msg);
    }

    @Override
    public void run() {
        String command = "";
        boolean isShutdown = false;

        while(!isShutdown) {
            try {
                command = this.readConsole().toLowerCase();
            } catch (IOException e) {
                logger.error("Console command read error");
                return;
            }

            switch (command) {
                case "help":
                    this.getHelp();
                    break;

                case "quit":
                    isShutdown = true;
                    break;

                case "show sessions":
                    this.getSessions();
                    break;

                case "show config":
                    this.printCurrentConfig();
                    break;

                default:
                    this.getHelp();
            }
        }
        this.shutdown();
    }

    private void shutdown() {
        this.writeConsole("Close console handler");
        nioServer.shutdown();
    }

    private void getSessions() {
        ArrayList<String> sessionList = nioServer.getSessionlist();
        if(sessionList.size() > 0) {
            for (String str : sessionList) {
                writeConsole(str);
            }
        }
        else writeConsole("No client connected");
    }

    private void getHelp() {
        String msg;
        msg = "Command list: \n";
        msg += "    help - command list \n";
        msg += "    quit - shutdown server \n";
        msg += "    show sessions - show list of current client sessions \n";
        msg += "    something else... \n";

        this.writeConsole(msg);
    }

    private void printCurrentConfig() {
        AppSettings.getInstance().printConfig();
    }

}
