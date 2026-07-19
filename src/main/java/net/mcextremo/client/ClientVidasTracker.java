package net.mcextremo.client;

public class ClientVidasTracker {
    private static int vidas = 5;

    public static void setVidas(int nuevasVidas) {
        vidas = nuevasVidas;
    }

    public static int getVidas() {
        return vidas;
    }
}