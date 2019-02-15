package ru.orehovai.livegps;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient {

    //данные сервера
    public static final String SERVER_IP = "srv1.livegpstracks.com"; // your computer IP
    public static final int SERVER_PORT = 3359;
    // Слушатель для ответных сообщений с сервера(на будущее)
    //private OnMessageReceived mMessageListener = null;
    //private String mServerMessage;
    // Сервер запушен если true
    private boolean mRun = false;
    // Для передачи сообщений
    private PrintWriter mBufferOut;
    // Для приема сообщений
    private BufferedReader mBufferIn;
    //сообщение для передачи
    private String stringForSend;

    public String getStringForSend() {
        return stringForSend;
    }

    public void setStringForSend(String stringForSend) {
        this.stringForSend = stringForSend;
    }

    /**
     * Конструктор со слушателем ответа(на будущее)
     */
//    public TCPClient(OnMessageReceived listener) {
//        mMessageListener = listener;
//    }

    //передача сообщения
    public void sendMessage(String message) {
        if (mBufferOut != null && !mBufferOut.checkError()) {
            mBufferOut.println(message);
            mBufferOut.flush();
        }
    }


    /**
     * Закрываем соединение
     */
    public void stopClient() {

        mRun = false;

        if (mBufferOut != null) {
            mBufferOut.flush();
            mBufferOut.close();
        }

        //mMessageListener = null;
        mBufferIn = null;
        mBufferOut = null;
        //mServerMessage = null;
    }

    public void run() {

            mRun = true;
            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                Socket socket = new Socket(serverAddr, SERVER_PORT);
                try {
                    mBufferOut = new PrintWriter(new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())), true);
                    mBufferIn = new BufferedReader(new InputStreamReader(
                            socket.getInputStream()));
                    sendMessage(stringForSend);
//                    while (mRun) {
//                        mServerMessage = mBufferIn.readLine();
//                        if (mServerMessage != null && mMessageListener != null) {
//                            // call the method messageReceived from MyActivity class
//                            mMessageListener.messageReceived(mServerMessage);
//                        }
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // the socket must be closed. It is not possible to reconnect to
                    // this socket
                    // after it is closed, which means a new socket instance has to
                    // be created.
                    socket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


    }

    // Declare the interface. The method messageReceived(String message) will
    // must be implemented in the MyActivity
    // class at on asynckTask doInBackground
//    public interface OnMessageReceived {
//        public void messageReceived(String message);
//    }
}