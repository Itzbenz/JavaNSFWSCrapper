package itzbenz;

import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.xfer.TransferListener;

public class LoggerTransferLister implements TransferListener {
    
    
    protected final String realPath;
    
    public LoggerTransferLister(String realPath) {
        this.realPath = realPath;
    }
    
    public LoggerTransferLister() {
        this("");
    }
    
    @Override
    public TransferListener directory(String name) {
        System.err.println("Started transferring directory `" + realPath + name + "`");
        return new LoggerTransferLister(realPath + name + "/");
    }
    
    @Override
    public StreamCopier.Listener file(final String name, final long size) {
        final String path = realPath + name;
        //System.err.println("Started transferring file `" + path + "`, size: " + size);
        return transferred -> {
            
            long percent = 100;
            if (size > 0){
                percent = (transferred * 100) / size;
            }
            System.err.println("Transferred " + path + " file, " + percent + "%" + " of " + size);
            
        };
    }
}
