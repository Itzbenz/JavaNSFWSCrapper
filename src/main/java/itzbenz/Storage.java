package itzbenz;

import java.io.IOException;

public interface Storage {
    void write(String name, byte[] data) throws IOException;
    
    boolean exists(String name);
    
    default int length() {
        return 0;
    }


    boolean failing();
}
