package dev.couchlink.tv;
import android.content.*;
import android.graphics.*;
import android.content.pm.ResolveInfo;
import java.util.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import dev.couchlink.ui.*;
import org.json.*;
import java.io.*;

public final class HomeActivity extends LocalWebActivity {
    private StudioStore store;
    @Override protected boolean television(){return true;}
    @Override public void onCreate(Bundle state){store=StudioStore.get(this);super.onCreate(state);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);startForegroundService(new Intent(this,RemoteService.class));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(Intent.ACTION_MAIN.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_HOME))emit("home()");}
    @Override protected JSONObject request(String path,JSONObject body)throws Exception{
        if(path.startsWith("/v1/studio/"))return store.api(path,body);
        RemoteService service=RemoteService.current;
        if(path.equals("/native/connection"))return new JSONObject().put("connected",true).put("name","Nova");
        if(path.equals("/v1/apps"))return new JSONObject().put("apps",apps());
        if(path.equals("/native/pair")){if(service==null||service.gate==null||service.identity==null)throw new IOException("The secure remote is still starting");String code=service.gate.newCode();String address=service.addresses().split("\\s+")[0];String fingerprint=service.identity.fingerprint.substring(0,12).toUpperCase(java.util.Locale.US);String link=new Uri.Builder().scheme("couchlink").authority("pair").appendQueryParameter("address",address).appendQueryParameter("code",code).appendQueryParameter("fingerprint",fingerprint).build().toString();return new JSONObject().put("address",address).put("code",code).put("fingerprint",fingerprint).put("link",link);}
        if(path.equals("/native/authorize")){runOnUiThread(()->startActivity(new Intent(this,MainActivity.class)));return new JSONObject();}
        if(path.equals("/native/settings")){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return new JSONObject();}
        if(path.equals("/native/old-home")){startActivity(new Intent().setComponent(new ComponentName("com.gathin.tvhome","com.gathin.tvhome.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return new JSONObject();}
        if(path.equals("/v1/launch")){Intent launch=find(body.getString("package"));if(launch==null)throw new IllegalArgumentException("App unavailable");startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return new JSONObject().put("ok",true);}
        throw new IllegalArgumentException("Unknown TV action");
    }
    @Override protected WebResourceResponse resource(String path)throws Exception{
        Uri uri=Uri.parse(path);
        if(path.startsWith("/icon?")){String pkg=uri.getQueryParameter("package");if(find(pkg)==null)return denied();Drawable drawable=getPackageManager().getApplicationIcon(pkg);Bitmap bitmap=Bitmap.createBitmap(180,100,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);drawable.setBounds(40,0,140,100);drawable.draw(canvas);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return new WebResourceResponse("image/png",null,new ByteArrayInputStream(out.toByteArray()));}
        if(path.startsWith("/media?")||path.startsWith("/thumb?")){String id=uri.getQueryParameter("id");File file=path.startsWith("/thumb?")?store.thumb(id):store.file(id);return new WebResourceResponse(file.getName().endsWith(".mp4")?"video/mp4":"image/jpeg",null,new FileInputStream(file));}
        return denied();
    }
    private Intent find(String pkg){if(pkg==null)return null;String p=pkg.toLowerCase(Locale.US);if(p.contains("factory")||p.contains("production")||p.contains("fac.menu")||p.contains("hkcfac"))return null;return getPackageManager().getLeanbackLaunchIntentForPackage(pkg);}
    private JSONArray apps()throws JSONException{
        List<ResolveInfo> list=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0);
        list.sort((a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();
        for(ResolveInfo info:list){String p=info.activityInfo.packageName;if(find(p)!=null&&seen.add(p))result.put(new JSONObject().put("package",p).put("label",info.loadLabel(getPackageManager()).toString()));}
        return result;
    }
    @Override protected void pickResult(Uri uri,String type,String target)throws Exception{String id=MediaImport.send(this,uri,type,(path,body)->store.api(path,body),message->emit("notify("+JSONObject.quote(message)+")"));emit("imported("+JSONObject.quote(id)+","+JSONObject.quote(target)+")");}
}
