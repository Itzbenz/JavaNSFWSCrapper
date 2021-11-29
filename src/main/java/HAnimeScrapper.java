import Atom.Exception.ShouldNotHappenedException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class HAnimeScrapper implements Scrapper {
    
    public static final URL nsfwURL, sfwURL;
    
    static {
        try {
            nsfwURL = new URL(
                    "https://community-uploads.highwinds-cdn.com/api/v9/community_uploads?channel_name__in[]=nsfw-general&channel_name__in[]=furry&channel_name__in[]=futa&channel_name__in[]=yaoi&channel_name__in[]=yuri&channel_name__in[]=traps&channel_name__in[]=irl-3d&query_method=seek&loc=https://hanime.tv");
            sfwURL = new URL(
                    "https://community-uploads.highwinds-cdn.com/api/v9/community_uploads?channel_name__in[]=media&query_method=seek&loc=https://hanime.tv");//todo &before_id=1480717
        }catch(MalformedURLException e){
            throw new ShouldNotHappenedException(e);
        }
        
    }
    
    @Override
    public List<URL> nsfw() {
        return null;
    }
    
    @Override
    public List<URL> sfw() {
        return null;
    }
}
