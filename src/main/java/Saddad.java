import Atom.Time.Timer;
import Atom.Utility.Digest;
import Atom.Utility.Pool;
import Atom.Utility.Random;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.InMemorySourceFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Saddad {
    
    public static final int sizeW = 224, sizeH = 224;
    public static String[] args;
    public static SSHClient ssh;
    public static final ThreadLocal<SSHClient> sshLocal = ThreadLocal.withInitial(() -> {
        try {
            return setupSshj();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    });   
    public static final ThreadLocal<SFTPClient> ftpLocal = ThreadLocal.withInitial(() -> {
        try {
            SFTPClient c = sshLocal.get().newSFTPClient();
            //c.getFileTransfer().setTransferListener(new LoggerTransferLister());
            return c;
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    });
    public static Integer[] scalingType = {Image.SCALE_FAST, Image.SCALE_REPLICATE, Image.SCALE_AREA_AVERAGING, Image.SCALE_SMOOTH, Image.SCALE_DEFAULT};
    static volatile boolean save = false;
    public static String remoteDir = "dataset";
    public static File getSaveFile(Scrapper s) {
        return new File("state-" + s.getClass().getSimpleName() + ".state");
    }
    
    public static void loadState(Scrapper[] s) {
        for (Scrapper scrapper : s) {
            try {
                if (!getSaveFile(scrapper).exists()) continue;
                scrapper.loadScrapperState(getSaveFile(scrapper));
            }catch(IOException e){
                System.err.println("Failed to load state for scrapper: " + scrapper.getClass().getSimpleName());
                System.err.println(e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) throws IOException {
        Saddad.args = args;
        if (args.length < 4){
            System.err.println("<host> <port> <username> <password>");
            System.exit(1);
        }
        ssh = setupSshj();
        if (args.length > 5){
            remoteDir = args[4];
        
        }
        remoteDir = remoteDir.endsWith("/") ? remoteDir.substring(0, remoteDir.length() - 1) : remoteDir;
        System.err.println("Remote dir: " + remoteDir);
        if (Pool.service instanceof ThreadPoolExecutor){
            ((ThreadPoolExecutor) Pool.service).setMaximumPoolSize(Runtime.getRuntime().availableProcessors() * 40);
            System.err.println("Setting max pool size to " + ((ThreadPoolExecutor) Pool.service).getMaximumPoolSize());
        }
    
        //20 gigabytes
        long bytes = 20L * 1024 * 1024 * 1024;
        final Scrapper[] scrappers = new Scrapper[]{new RedditScrapper()};
        loadState(scrappers);
        Thread savingScrapper = new Thread(() -> {
            if (save) return;
            save = true;
            System.err.println("Saving scrapper state...");
            for (Scrapper scrapper : scrappers) {
                File f = getSaveFile(scrapper);
                try {
                    scrapper.saveScrapperState(f);
                }catch(Exception e){
                    System.err.println("Fail to save scrapper state: " + scrapper.getClass().getSimpleName());
                    System.err.println(e.getMessage());
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(savingScrapper);
        Timer timer = new Timer(TimeUnit.SECONDS, 5), limit = new Timer(TimeUnit.MINUTES, 30);
        long nsfwCount = 0, sfwCount = 0;
        while (true) {
            for (Scrapper scrapper : scrappers) {
                try {
                    boolean nsfw = Random.getBool();
                    List<URL> urls = nsfw ? scrapper.nsfw() : scrapper.sfw();
                    if (nsfw) nsfwCount += urls.size();
                    else sfwCount += urls.size();
                    if (timer.get())
                        System.out.println("nsfw: " + nsfwCount + " sfw: " + sfwCount + " total: " + (nsfwCount + sfwCount) + " threads: " + ((ThreadPoolExecutor) Pool.service).getPoolSize());
                    
                    Pool.submit(() -> {
                        try {
                            process(urls, nsfw);
                        }catch(Exception e){
                            System.err.println("Failed to process urls, scrapper: " + scrapper.getClass()
                                    .getSimpleName());
                            System.err.println(e.getMessage());
                        }
                    });
                }catch(Exception bs){
                    System.err.println(bs.getMessage());
                    System.err.println(scrapper.getClass().getName());
                    bs.printStackTrace();
                }
            }
            if (limit.get()){
                System.out.println("nsfw: " + nsfwCount + " sfw: " + sfwCount + " total: " + (nsfwCount + sfwCount) + " threads: " + ((ThreadPoolExecutor) Pool.service).getPoolSize());
                System.err.println("Limit reached");
                Pool.service.shutdown();
                Pool.parallelAsync.shutdown();
                System.err.println("Awaiting termination Timeout: 2 minutes");
                try {
                    boolean b = Pool.service.awaitTermination(1, TimeUnit.MINUTES);
                    if (!b) System.err.println("Timeout while awaiting service termination");
                }catch(InterruptedException e){
                    System.err.println("Interrupted while awaiting service termination");
                }
                try {
                    boolean b = Pool.parallelAsync.awaitTermination(1, TimeUnit.MINUTES);
                    if (!b) System.err.println("Timeout while awaiting parallelAsync termination");
                }catch(InterruptedException e){
                    System.err.println("Interrupted while awaiting parallelAsync termination");
                }
                savingScrapper.start();
                break;
            }
        }
        
    }
    
    public static void process(List<URL> urls, boolean nsfw) {
        for (URL url : urls) {
            try {
                BufferedImage image = ImageIO.read(url);
                if (image == null) continue;
                //CPU Intensive
                Pool.async(() -> {
                    BufferedImage resized = resize(image, sizeW, sizeH);
                    try {
                        byte[] encoded = encodeImage(resized);
                        //switch to IO intensive
                        Pool.submit(() -> {
                            try {
                                uploadToSftp(encoded, nsfw);
                            }catch(IOException e){
                                System.err.println("Failed to upload image to sftp, image: " + url);
                                System.err.println(e.getMessage());
                            }
                        });
                    }catch(IOException e){
                        System.err.println("Failed to encode image: " + url);
                        System.err.println(e.getMessage());
                    }
                });
                
                
            }catch(IOException e){
                System.err.println("Failed to process image from url: " + url);
                System.err.println(e.getMessage());
            }
        }
    }
    
    public static SSHClient setupSshj() throws IOException {
        return setupSshj(args[0], Integer.parseInt(args[1]), args[2], args[3]);
    }
    
    public static SSHClient setupSshj(String remoteHost, int port, String username, String password) throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(remoteHost, port);
        client.authPassword(username, password);
        return client;
    }
    
    //upload sftp using org.apache.sshd-sftp
    public static void uploadToSftp(byte[] image, boolean nsfw) throws IOException {
    
        // override any default configuration...
        byte[] b = Atom.Utility.Digest.sha256(image);
        //to hex
        String hex = Digest.toHex(b);
    
        String path = remoteDir + (nsfw ? "/nsfw/" : "/sfw/") + hex + ".jpg";
        try {
            String dir = path.substring(0, path.lastIndexOf("/"));
            ftpLocal.get().mkdirs(dir);
        }catch(IOException ignored){
        
        }
        if (ftpLocal.get().statExistence(path) != null) return;
        ftpLocal.get().put(new InMemorySourceFile() {
            @Override
            public String getName() {
                return hex + ".jpg";
            }
        
            @Override
            public long getLength() {
                return image.length;
            }
            
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(image);
            }
        }, path);
    
    }
    
    //encode to upload to sftp
    public static byte[] encodeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        baos.flush();
        byte[] imageInByte = baos.toByteArray();
        baos.close();
        return imageInByte;
    }
    
    
    //rescale image
    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Random.getRandom(scalingType));
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }
    
    //compress image
    
}
