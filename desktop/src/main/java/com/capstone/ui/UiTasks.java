package com.capstone.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Runs blocking work (DB, network, hashing) off the Swing UI thread. */
final class UiTasks {

    @FunctionalInterface
    interface Work<T> { T call() throws Exception; }

    private UiTasks() {}

    /** Runs work in the background; onSuccess / onError are called on the UI thread. */
    static <T> void run(Work<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }

            @Override protected void done() {
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    onError.accept(e.getCause());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onError.accept(e);
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }
}
