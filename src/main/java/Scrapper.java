import Atom.Utility.Pool;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Future;

public interface Scrapper extends Serializable {
    
    default Future<List<URL>> asyncNSFW(String url) {
        return Pool.submit(this::nsfw);
    }
    
    default Future<List<URL>> asyncSFW(String url) {
        return Pool.submit(this::sfw);
    }
    
    List<URL> nsfw() throws IOException;
    
    List<URL> sfw() throws IOException;
    
    default void saveScrapperState(File file) throws IOException {
    
    }
    
    default void loadScrapperState(File file) throws IOException {
    
    }
}
