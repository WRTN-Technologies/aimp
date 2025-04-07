package io.wrtn.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Threads {

    private static final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public static void shutdown() {
        ioExecutor.shutdownNow();
    }

    public static ExecutorService getIOExecutor() {
        return ioExecutor;
    }
}
