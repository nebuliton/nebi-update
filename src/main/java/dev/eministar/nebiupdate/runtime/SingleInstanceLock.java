package dev.eministar.nebiupdate.runtime;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SingleInstanceLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static SingleInstanceLock acquire(Path lockPath) {
        try {
            Path absolute = lockPath.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            FileChannel channel = FileChannel.open(
                    absolute,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new IllegalStateException("NebiUpdate läuft bereits. Lock-Datei: " + absolute);
            }
            return new SingleInstanceLock(channel, lock);
        } catch (Exception ex) {
            throw new IllegalStateException("Konnte Instanz-Lock nicht setzen", ex);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } finally {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }
}
