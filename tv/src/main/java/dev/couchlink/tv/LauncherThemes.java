package dev.couchlink.tv;

import android.content.*;
import dev.couchlink.core.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Applies small runtime profiles through the launcher's existing shell-only theme provider. */
final class LauncherThemes {
    private final Context context;
    private final Identity identity;
    private final SharedPreferences prefs;
    private static final String URI="content://com.gathin.tvhome.theme/";
    LauncherThemes(Context context,Identity identity){this.context=context;this.identity=identity;prefs=context.getSharedPreferences("remote",0);}
    private String shell(String command)throws Exception{return new AdbClient("127.0.0.1",5555,identity.adbKey).shell(command);}
    synchronized void apply(String name)throws Exception{
        if(!Arrays.asList("classic","modern","cinematic","original").contains(name))throw new IllegalArgumentException("Unknown TV theme");
        if(!shell("pm path com.gathin.tvhome").startsWith("package:"))throw new IllegalArgumentException("Install the CleanYourTV home screen first");
        if(!prefs.getBoolean("priorThemeSaved",false)){
            String old=shell("content read --uri "+URI+"config.json 2>/dev/null");
            try{new JSONObject(old);}catch(JSONException e){old="";}
            prefs.edit().putString("priorTheme",old).putBoolean("priorThemeSaved",true).commit();
        }
        if(name.equals("original")){
            String previous=prefs.getString("priorTheme","");
            if(previous.isEmpty())shell("content delete --uri "+URI+"config.json");else put("config.json",previous);
        }else put("config.json",profile(name).toString());
        put("theme_revision",UUID.randomUUID().toString());
        prefs.edit().putString("theme",name).apply();
        shell("am force-stop com.gathin.tvhome");
        String result=shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity");
        if(result.contains("Error:"))throw new IllegalArgumentException("The TV could not reopen Home");
    }
    private void put(String file,String text)throws Exception{
        String temp="/data/local/tmp/couchlink-theme-"+file;
        String encoded=Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        try{
            shell("printf '%s' '"+encoded+"' | base64 -d > "+temp);
            String result=shell("content write --uri "+URI+file+" < "+temp);
            if(result.contains("Error")||result.contains("Exception")||result.contains("Unsupported"))throw new IllegalArgumentException("The TV home did not accept this theme");
        }finally{shell("rm -f "+temp);}
    }
    private JSONObject profile(String name)throws JSONException{
        JSONObject p=new JSONObject().put("columns",4).put("date_below_clock",true).put("header_white",true)
            .put("wallpaper",false).put("animations",true).put("advanced_effects",false).put("app_library",true)
            .put("tile_menu",true).put("dock_even_padding",true).put("show_stats",false).put("home_idle_clock_minutes",10)
            .put("dock_apps",new JSONArray(Arrays.asList("com.google.android.youtube.tv","com.netflix.ninja","com.amazon.amazonvideo.livingroom","in.startv.hotstar")));
        if(name.equals("classic"))return p.put("background_color","#060A14").put("cinematic_home",false).put("uniform_tiles",false).put("app_glow",false).put("ambient_glow",true).put("dock_glass",false).put("sky_intensity",0);
        if(name.equals("modern"))return p.put("background_color","#0B1325").put("cinematic_home",false).put("uniform_tiles",true).put("app_glow",true).put("ambient_glow",true).put("dock_glass",true).put("sky_intensity",45).put("focus_overshoot",false);
        return p.put("background_color","#030610").put("cinematic_home",true).put("cinematic_dots",false).put("cinematic_white_focus",true).put("cinematic_scrim",85).put("cinematic_art",70).put("uniform_tiles",false).put("app_glow",true).put("ambient_glow",true).put("dock_glass",true).put("sky_intensity",0);
    }
}
