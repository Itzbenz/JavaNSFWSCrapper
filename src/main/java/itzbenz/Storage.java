package itzbenz;

import java.io.IOException;

public interface Storage {
    void write(String name, byte[] data) throws IOException;
    
    boolean exists(String name);
    
    default int size() {
        return 0;
    }
    
    default long totalBytes() {
        return 0;
    }
    
    boolean failing();
}
