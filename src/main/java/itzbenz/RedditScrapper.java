package itzbenz;

import Atom.Net.Request;
import Atom.Utility.Random;
import Atom.Utility.Utility;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RedditScrapper implements Scrapper {
    //source: https://github.com/alex000kim/nsfw_data_scraper/tree/master/scripts/source_urls
    public static transient final String[] hentai = new String[]{"doujinshi", "hentai", "HentaiHumiliation", "HentaiLover", "Hentai4Everyone", "ecchi", "MonsterGirl", "sukebei", "yaoi"}, drawings = new String[]{ "manga", "AnimeCalendar", "Melanime", "anime", "Boruto", "overlord", "streetmoe", "animeponytails", "awoonime", "awwnime", "bishounen", "cutelittlefangs", "cuteanimeboys", "endcard", "gunime", "Moescape", "headpats", "imouto", "kyoaniyuri", "Patchuu", "Pixiv", "pokemoe", "Pouts", "Tsunderes", "twintails", "WholesomeYuri", "zettairyouiki"}, neutral = new String[]{"memes", "mildlypenis", "mildlyvagina", "Damnthatsinteresting", "tattoos", "progresspics", "photoshopbattles", "aww", "funny", "pics", "photographs", "photography", "EarthPorn", "HistoryPorn", "Rateme", "roastme", "wtfstockphotos", "mildlyinteresting", "interestingasfuck", "wholesomememes", "memes", "FoodPorn", "desert", "desertporn", "ImaginaryDeserts", "mildlyboobs", "portraits", "Portraiture", "Faces", "Pareidolia", "reactionpics", "facesinthings", "estoration", "PrettyGirlsUglyFaces", "Pictures", "lastimages", "cursedimages", "Instagramreality", "PhotoshopRequest", "hmmm", "blunderyears", "cringepics", "cutebabies", "cutekids", "BabyPhotos", "uglybabies", "MadeMeSmile", "Instagram"}, porn = new String[]{"gangbang", "Hegoesdown", "anal", "pornpics", "blowjob", "Triplepenetration"}, sexy = new String[]{"celebritylegs", "Models", "VSModels", "goddesses", "tightdresses", "girlsinyogapants", "burstingout", "randomsexiness", "BustyPetite", "SexyTummies", "RealGirls", "sexygirls", "stripgirls", "OnePieceSuits", "swimsuit", "nsfwswimsuit", "leotards", "swimsuits", "bikinis", "CrochetBikinis", "MicroBikini", "asiansinswimsuits", "InstagramHotties"}, gore = new String[]{"thebullwins", "guro", "eyeblach", "medizzy", "FiftyFifty"};
    public static transient final String[] sfwArray = Utility.merge(neutral, drawings);
    public static transient final String[] nsfwArray = Utility.merge(hentai, porn, sexy, gore);
    public static transient final int totalSubreddit = hentai.length + drawings.length + porn.length + sexy.length + gore.length;
    
    private static final long serialVersionUID = 1;
    protected HashMap<Integer, String> lastID = new HashMap<>();
    
    public List<URL> get(String subreddit, String id, boolean nsfw) throws IOException, ParseException {
        URL u = new URL("https://www.reddit.com/r/" + subreddit + "/new.json?limit=100" + (id == null ? "" : "&after=" + id));
        //http request
        //nsfw contain
        
        byte[] b = Request.get(u.toExternalForm(), c -> {
            //set user agent
            c.setRequestProperty("User-Agent", "Reddit API");
        });
        JSONObject object = new JSONObject(new String(b));
        try {
            lastID.put(subreddit.hashCode(), object.getJSONObject("data").getString("after"));
        }catch(Exception e){
            //shrug
        }
        ArrayList<URL> filtered = new ArrayList<>();
        JSONArray children = object.getJSONObject("data").getJSONArray("children");
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i).getJSONObject("data");
            if (!nsfw && child.getBoolean("over_18")) continue;
            String url = child.getString("url");
            if (url == null || url.isEmpty()) continue;
            if (url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".jpeg")){
                try {
                    filtered.add(new URL(url));
                }catch(MalformedURLException e){
                    System.err.println("Malformed URL: " + url);
                }
            }else{
                //System.err.println("Not a valid image: " + url);
            }
        }
        return filtered;
    }
    
    public List<URL> get(String subreddit, boolean nsfw) throws IOException, ParseException {
        return get(subreddit, lastID.getOrDefault(subreddit.hashCode(), null), nsfw);
    }
    
    @Override
    public List<URL> nsfw() throws IOException {
        try {
            return get(Random.getRandom(nsfwArray), true);
        }catch(ParseException e){
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void saveScrapperState(File file) throws IOException {
        new JSONObject(lastID).write(new FileWriter(file)).flush();
    }
    
    @Override
    public void loadScrapperState(File file) throws IOException {
        new JSONObject(new FileReader(file)).toMap().forEach((key, value) -> lastID.put(key.hashCode(), value == null ? null : value.toString()));
    }
    
    @Override
    public List<URL> sfw() throws IOException {
        try {
            return get(Random.getRandom(sfwArray), false);
        }catch(ParseException e){
            throw new RuntimeException(e);
        }
    }
}
