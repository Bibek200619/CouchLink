package dev.couchlink.tv;

import android.content.*;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared durable home state; paired phones and the TV edit the same document. */
final class StudioStore {
    private static StudioStore instance;
    static synchronized StudioStore get(Context c){if(instance==null)instance=new StudioStore(c.getApplicationContext());return instance;}
    final Context context;
    final SharedPreferences prefs;
    final File media;
    private JSONObject scene;
    private JSONArray creations;
    private String panel="";
    private String jarvisMessage="";
    private String watchMedia="";
    private long action=0,revision;
    private final Map<String,JSONObject> uploads=new HashMap<>();
    static final Set<String> BACKGROUNDS=new HashSet<>(Arrays.asList("mountain","coast","cove","forest","dunes","peaks","city","golden","garden"));
    private StudioStore(Context c){
        context=c;prefs=c.getSharedPreferences("studio",0);media=new File(c.getFilesDir(),"media");media.mkdirs();
        File[] incomplete=media.listFiles();if(incomplete!=null)for(File f:incomplete)if(f.getName().endsWith(".part")||f.getName().endsWith(".download"))f.delete();
        try{scene=new JSONObject(prefs.getString("scene","{}"));creations=new JSONArray(prefs.getString("creations","[]"));}catch(JSONException e){scene=new JSONObject();creations=new JSONArray();}
        try{if(!scene.has("layout")){scene=new JSONObject("{\"design\":\"app-icons\",\"layout\":\"app-icons\",\"background\":\"mountain\",\"tiles\":\"brand\",\"labels\":\"both\",\"logoColor\":\"brand\",\"font\":\"modern\",\"clock\":true,\"apps\":[\"com.netflix.ninja\",\"com.google.android.youtube.tv\",\"com.amazon.amazonvideo.livingroom\",\"in.startv.hotstar\",\"com.sonyliv\",\"com.graymatrix.did\",\"com.crunchyroll.crunchyroid\",\"com.google.android.videos\",\"com.internet.tvbrowser\"]}");}}catch(JSONException impossible){throw new IllegalStateException(impossible);}
        revision=prefs.getLong("revision",1);
        JSONArray oldApps=scene.optJSONArray("apps");if(oldApps!=null){JSONArray clean=new JSONArray();boolean changed=false;for(int i=0;i<oldApps.length();i++){String pkg=oldApps.optString(i);if(pkg.equals("@studio")){changed=true;continue;}clean.put(pkg);}if(changed)try{scene.put("apps",clean);prefs.edit().putString("scene",scene.toString()).apply();}catch(JSONException e){throw new IllegalStateException(e);}}
        new File(c.getFilesDir(),"studio-key.enc").delete();
    }
    synchronized JSONObject state()throws JSONException{return new JSONObject().put("scene",new JSONObject(scene.toString())).put("creations",new JSONArray(creations.toString())).put("revision",revision).put("panel",panel).put("action",action).put("watchMedia",watchMedia).put("jarvisMessage",jarvisMessage);}
    private synchronized void save(){revision++;prefs.edit().putString("scene",scene.toString()).putString("creations",creations.toString()).putLong("revision",revision).apply();}
    private static String choice(JSONObject body,String key,String... choices){String value=body.optString(key);if(!Arrays.asList(choices).contains(value))throw new IllegalArgumentException("Unknown "+key);return value;}
    synchronized JSONObject change(JSONObject changes)throws Exception{
        JSONObject next=new JSONObject(scene.toString());
        for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();switch(key){
            case "layout":next.put(key,choice(changes,key,"app-icons","spotlight","mosaic","sidebar","dock","minimal"));break;
            case "design":next.put(key,choice(changes,key,"app-icons","spotlight","mosaic","sidebar","dock","minimal","custom"));break;
            case "tiles":next.put(key,choice(changes,key,"brand","glass","dark"));break;
            case "labels":next.put(key,choice(changes,key,"both","logo","name"));break;
            case "logoColor":next.put(key,choice(changes,key,"brand","white"));break;
            case "font":next.put(key,choice(changes,key,"modern","elegant","rounded"));break;
            case "clock":next.put(key,changes.getBoolean(key));break;
            case "background":String id=changes.getString(key);if(!BACKGROUNDS.contains(id)&&!file(id).isFile())throw new IllegalArgumentException("Background unavailable");next.put(key,id);break;
            case "apps":JSONArray apps=changes.getJSONArray(key);if(apps.length()<1||apps.length()>24)throw new IllegalArgumentException("Choose between 1 and 24 apps");Set<String> seen=new HashSet<>();for(int i=0;i<apps.length();i++){String p=apps.getString(i);if(!p.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+"))throw new IllegalArgumentException("Invalid app");if(!seen.add(p))throw new IllegalArgumentException("Duplicate app");}next.put(key,apps);break;
            default:throw new IllegalArgumentException("Unknown appearance setting");
        }}scene=next;save();return state();
    }
    synchronized JSONObject show(String name)throws Exception{
        if(!Arrays.asList("appearance","designs","settings","apps","remote","watch","jarvis").contains(name))throw new IllegalArgumentException("Unknown TV screen");
        panel=name;action++;
        return state();
    }
    synchronized JSONObject jarvisMessage(JSONObject body)throws Exception{String message=body.optString("message","").trim();if(message.isEmpty()||message.length()>1200)throw new IllegalArgumentException("Jarvis reply must be between 1 and 1,200 characters");jarvisMessage=message;panel="jarvis";action++;return state();}
    File file(String id){if(id==null||!id.matches("[a-f0-9-]{36}\\.(jpg|mp4)"))throw new IllegalArgumentException("Invalid media ID");return new File(media,id);}
    File thumb(String id){File f=file(id);return id.endsWith(".jpg")?f:new File(media,id.substring(0,36)+"-thumb.jpg");}
    synchronized JSONObject begin(JSONObject b)throws Exception{
        if(uploads.size()>=2)throw new IllegalArgumentException("Wait for the current import");
        String type=choice(b,"type","image","video");int size=b.getInt("size");if(size<=0||size>(type.equals("image")?4:32)*1024*1024)throw new IllegalArgumentException("Choose a photo under 4 MB or a video under 32 MB");
        long used=0;File[] files=media.listFiles();if(files!=null)for(File f:files)used+=f.length();if(used+size>256L*1024*1024||media.getUsableSpace()<size*2L+16L*1024*1024)throw new IllegalArgumentException("The TV's media library is full. Delete an older import first.");
        String id=UUID.randomUUID().toString();JSONObject item=new JSONObject().put("type",type).put("size",size).put("offset",0).put("title",b.optString("title","Your "+type).substring(0,Math.min(100,b.optString("title","Your "+type).length())));uploads.put(id,item);return new JSONObject().put("id",id);
    }
    synchronized JSONObject chunk(JSONObject b)throws Exception{
        String id=b.getString("id");JSONObject upload=uploads.get(id);if(upload==null)throw new IllegalArgumentException("Import expired");int offset=b.getInt("offset");if(offset!=upload.getInt("offset"))throw new IllegalArgumentException("Import offset does not match");
        byte[] bytes=Base64.decode(b.getString("data"),Base64.DEFAULT);if(bytes.length<1||bytes.length>8192||offset+bytes.length>upload.getInt("size"))throw new IllegalArgumentException("Invalid import chunk");
        try(FileOutputStream out=new FileOutputStream(new File(media,id+".part"),true)){out.write(bytes);}upload.put("offset",offset+bytes.length);return new JSONObject().put("offset",offset+bytes.length);
    }
    synchronized JSONObject finish(JSONObject b)throws Exception{
        String id=b.getString("id");JSONObject upload=uploads.remove(id);if(upload==null||upload.getInt("offset")!=upload.getInt("size"))throw new IllegalArgumentException("Import is incomplete");
        File partial=new File(media,id+".part");boolean image=upload.getString("type").equals("image");String filename=id+(image?".jpg":".mp4");File destination=file(filename);
        try{
            Bitmap bitmap;
            if(image){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(partial.getPath(),options);if(options.outWidth<1||options.outHeight<1||options.outWidth>16000||options.outHeight>16000)throw new IllegalArgumentException("This photo format is not supported");options.inJustDecodeBounds=false;options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1600);bitmap=BitmapFactory.decodeFile(partial.getPath(),options);if(bitmap==null)throw new IllegalArgumentException("Could not open this photo");try(FileOutputStream out=new FileOutputStream(destination)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}}
            else{MediaMetadataRetriever reader=new MediaMetadataRetriever();try{reader.setDataSource(partial.getPath());bitmap=reader.getFrameAtTime();if(bitmap==null)throw new IllegalArgumentException("Could not open this video");}finally{reader.release();}if(!partial.renameTo(destination))throw new IOException("Could not save the video");}
            Bitmap small=Bitmap.createScaledBitmap(bitmap,480,Math.max(1,480*bitmap.getHeight()/bitmap.getWidth()),true);try(FileOutputStream out=new FileOutputStream(new File(media,id+"-thumb.jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,out);}if(small!=bitmap)small.recycle();bitmap.recycle();
            JSONObject item=new JSONObject().put("id",id).put("media",filename).put("title",upload.getString("title")).put("kind",image?"image":"video").put("status","completed").put("source","import");creations.put(item);trim();save();return new JSONObject().put("media",filename).put("state",state());
        }finally{partial.delete();}
    }
    private void trim(){while(creations.length()>40){int remove=-1;for(int i=0;i<creations.length();i++){JSONObject j=creations.optJSONObject(i);if(j!=null&&Arrays.asList("completed","failed","canceled","nsfw","unknown").contains(j.optString("status"))&&!j.optString("media").equals(scene.optString("background"))){remove=i;String name=j.optString("media");if(!name.isEmpty()){thumb(name).delete();file(name).delete();}break;}}if(remove<0)break;creations.remove(remove);}}
    synchronized void addJob(JSONObject job){creations.put(job);trim();save();prefs.edit().putString("creations",creations.toString()).commit();}
    synchronized JSONObject job(String id)throws JSONException{for(int i=0;i<creations.length();i++){JSONObject j=creations.getJSONObject(i);if(j.optString("id").equals(id))return j;}return null;}
    synchronized JSONArray jobs()throws JSONException{return new JSONArray(creations.toString());}
    synchronized void jobChanged(){save();}
    synchronized void removeImportEntry(String id)throws JSONException{for(int i=creations.length()-1;i>=0;i--)if(creations.getJSONObject(i).optString("id").equals(id))creations.remove(i);save();}
    synchronized JSONObject remove(String id)throws Exception{
        JSONObject j=job(id);if(j==null)throw new IllegalArgumentException("Creation unavailable");if(!Arrays.asList("completed","failed","canceled","nsfw","unknown").contains(j.optString("status")))throw new IllegalArgumentException("Cancel this generation before deleting it");
        String filename=j.optString("media");if(!filename.isEmpty()){if(scene.optString("background").equals(filename))scene.put("background","mountain");thumb(filename).delete();file(filename).delete();}
        for(int i=creations.length()-1;i>=0;i--)if(creations.getJSONObject(i).optString("id").equals(id))creations.remove(i);save();return state();
    }
    JSONObject api(String path,JSONObject b)throws Exception{
        if(path.equals("/v1/studio/state")&&b==null)return state();
        if(path.equals("/v1/studio/scene")&&b!=null)return change(b);
        if(path.equals("/v1/studio/show")&&b!=null){if(b.optString("panel").equals("watch")){String id=b.getString("media");if(!file(id).isFile())throw new IllegalArgumentException("Video unavailable");watchMedia=id;}return show(b.getString("panel"));}
        if(path.equals("/v1/studio/jarvis")&&b!=null)return jarvisMessage(b);
        if(path.equals("/v1/studio/delete")&&b!=null)return remove(b.getString("id"));
        if(path.equals("/v1/studio/import/begin")&&b!=null)return begin(b);
        if(path.equals("/v1/studio/import/chunk")&&b!=null)return chunk(b);
        if(path.equals("/v1/studio/import/finish")&&b!=null)return finish(b);
        throw new IllegalArgumentException("Unknown studio action");
    }
}
