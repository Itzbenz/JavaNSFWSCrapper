package itzbenz;

import Atom.Time.Timer;
import Atom.Utility.Digest;
import Atom.Utility.Pool;
import Atom.Utility.Random;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Saddad {
    
    public static final int sizeW = 224, sizeH = 224;
    public static String[] args;
    
    
    public static Integer[] scalingType = {Image.SCALE_FAST, Image.SCALE_REPLICATE, Image.SCALE_AREA_AVERAGING, Image.SCALE_SMOOTH, Image.SCALE_DEFAULT};
    
    public static WeakHashMap<URL, Object> processed = new WeakHashMap<>(20000, 0.8f);
    static volatile boolean save = false;
    static long nsfwCount = 0, sfwCount = 0;
    public static final String format = "jpg";
    
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
    
    public static Storage nsfwStorage, sfwStorage;
    
    //break down this method
    public static void main(String[] args) throws IOException {
        Saddad.args = args;
        if (args.length != 1){
            args = new String[]{"./dataset"};
        }
        File path = new File(args[0]);
        path = path.getAbsoluteFile();
        path.mkdirs();
        nsfwStorage = new LocalFileStorage(path, "nsfw");
        sfwStorage = new LocalFileStorage(path, "sfw");
        
        System.err.println("Dir: " + path.getAbsolutePath());
        if (Pool.service instanceof ThreadPoolExecutor){
            ((ThreadPoolExecutor) Pool.service).setMaximumPoolSize(Runtime.getRuntime().availableProcessors() * 40);
            System.err.println("Setting max pool size to " + ((ThreadPoolExecutor) Pool.service).getMaximumPoolSize());
        }
        //Pool.parallelAsync = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
        Timer timer = new Timer(TimeUnit.SECONDS, 5), limit = new Timer(TimeUnit.MINUTES, 40);
        
        while (true) {
            boolean limited = limit.get();
            for (Scrapper scrapper : scrappers) {
                try {
                    boolean nsfw = Random.getBool();
                    List<URL> urls = nsfw ? scrapper.nsfw() : scrapper.sfw();
                    
                    if (timer.get()){
                        System.out.println("nsfw: " + nsfwCount + " sfw: " + sfwCount + " total in storage: " + (sfwStorage.length() + nsfwStorage.length()) + " threads: " + ((ThreadPoolExecutor) Pool.service).getPoolSize());
                    }
                    // Pool.submit(() -> {
                    try {
                        process(urls, nsfw);
                    }catch(Exception e){
                        System.err.println("Failed to process urls, scrapper: " + scrapper.getClass().getSimpleName());
                        System.err.println(e.getMessage());
                    }
                    //});
                } catch (Exception bs) {
                    System.err.println(bs.getMessage());
                    System.err.println(scrapper.getClass().getName());
                    bs.printStackTrace();
                }
            }
            //we still inside loop
            if (limited) {
                System.out.println("nsfw: " + nsfwCount + " sfw: " + sfwCount + " total: " + (nsfwCount + sfwCount) + " threads: " + ((ThreadPoolExecutor) Pool.service).getPoolSize());
                System.err.println("Limit reached");
                Pool.parallelAsync.shutdown();
                Pool.service.shutdown();

                System.err.println("Awaiting termination Timeout: 2 minutes");
                try {
                    boolean b = Pool.parallelAsync.awaitTermination(1, TimeUnit.MINUTES);
                    if (!b) System.err.println("Timeout while awaiting parallelAsync termination");
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while awaiting parallelAsync termination");
                }
                try {
                    boolean b = Pool.service.awaitTermination(1, TimeUnit.MINUTES);
                    if (!b) System.err.println("Timeout while awaiting service termination");
                }catch(InterruptedException e){
                    System.err.println("Interrupted while awaiting service termination");
                }
                
                savingScrapper.start();
                break;
            }
        }
        
    }
    
    public static void process(List<URL> urls, boolean nsfw) {
        
        for (URL url : urls) {
            Object o = processed.get(url);
            if (o != null){
                continue;
            }
            processed.put(url, url.toExternalForm());
            if (nsfw) nsfwCount++;
            else sfwCount++;
            
            
            Pool.async(() -> {
                try {
                    BufferedImage finalImage = ImageIO.read(url);
                    if (finalImage != null)
                        process(finalImage, url.toExternalForm(), nsfw);
                }catch(Throwable e){
                    System.err.println("Failed to process image from url: " + url);
                    System.err.println(e.getMessage());
                }
            });
            
            
        }
    }
    
    //CPU Intensive
    public static void process(BufferedImage image, String name, final boolean nsfw) {
        BufferedImage resized = resize(image, sizeW, sizeH);
        try {
            byte[] encoded = encodeImage(resized);
            //switch to IO intensive
            Pool.submit(() -> {
                try {
                    write(encoded, nsfw);
                }catch(Exception e){
                    //??????
                    System.err.println("Failed to upload image to sftp, image: " + name);
                    System.err.println(e.getMessage());
                }
            });
        }catch(Exception e){
            // ???? what, how
            System.err.println("Failed to encode image: " + name);
            System.err.println(e.getMessage());
        }
        //???????
        resized.flush();
        image.flush();
    }
    
    public static void write(byte[] image, boolean nsfw) throws IOException {
        
        // override any default configuration...
        byte[] b = Atom.Utility.Digest.sha256(image);
        //to hex
        String name = Digest.toHex(b);
        if (nsfw){
            nsfwStorage.write(name + "." + format, image);
        }else{
            sfwStorage.write(name + "." + format, image);
        }
    }
    
    //encode to upload to sftp
    public static byte[] encodeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
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
        img = null;
        return dimg;
    }
    
    //compress image
    
}
