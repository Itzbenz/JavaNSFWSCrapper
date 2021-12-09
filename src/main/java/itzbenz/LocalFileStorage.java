package itzbenz;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class LocalFileStorage implements Storage {
    
    protected String parent;
    @NotNull
    protected File parentFile;
    
    public LocalFileStorage(File parent, String name) {
        this(new File(parent, name));
    }
    
    public LocalFileStorage(File datasetDir) {
        datasetDir = datasetDir.getAbsoluteFile();
        datasetDir.mkdirs();
        parent = datasetDir.getAbsolutePath();
        parentFile = datasetDir;
    }
    
    @Override
    public void write(String name, byte[] data) throws IOException {
        if (data.length > 1024 * 1024 * 1024 && exists(name)) return;
        Files.write(Path.of(parent, name), data);
    }

    @Override
    public boolean exists(String name) {
        return Files.exists(Path.of(parent, name));
    }

    @Override
    public long totalBytes() {
        return parentFile.getTotalSpace();
    }

    @Override
    public boolean failing() {
        return false;
    }

    @Override
    public int size() {
        if (parentFile.list() == null) return 0;
        return Objects.requireNonNull(parentFile.list()).length;
    }
}
