package dev.couchlink.ui;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;

/** Bundled, offline UI. Only the fixed local origin receives a native bridge. */
public abstract class LocalWebActivity extends Activity {
    protected WebView web;
    protected final ExecutorService worker=Executors.newFixedThreadPool(3);
    private final ExecutorService controls=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8));
    protected abstract boolean television();
    protected abstract JSONObject request(String path,JSONObject body)throws Exception;
    protected abstract WebResourceResponse resource(String path)throws Exception;
    protected void pickResult(Uri uri,String mime,String target)throws Exception{throw new IOException("Import is unavailable");}
    private String pickMime,pickTarget;
    private TextToSpeech speech;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null){pickMime=state.getString("pickMime");pickTarget=state.getString("pickTarget");}speech=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS)speech.setLanguage(Locale.getDefault());});getWindow().setStatusBarColor(0xff27343c);getWindow().setNavigationBarColor(0xff171d22);
        if(television())getWindow().getDecorView().setSystemUiVisibility(5894);
        web=new WebView(this);web.setBackgroundColor(0xff1e2b33);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();if(!"https".equals(u.getScheme())||!"couchlink.local".equals(u.getHost()))return denied();
                String p=u.getPath();try{
                    if(p.startsWith("/api/"))return resource(p.substring(4)+(u.getQuery()==null?"":"?"+u.getQuery()));
                    if(p.contains("..")||!p.matches("/[a-zA-Z0-9_./-]+"))return denied();
                    String name=p.equals("/")?"index.html":p.substring(1);String mime=name.endsWith(".css")?"text/css":name.endsWith(".js")?"application/javascript":name.endsWith(".jpg")?"image/jpeg":name.endsWith(".png")?"image/png":name.endsWith(".ttf")?"font/ttf":"text/html";
                    return new WebResourceResponse(mime,"UTF-8",getAssets().open(name));
                }catch(Exception e){return denied();}
            }
        });
        setContentView(web);web.loadUrl("https://couchlink.local/index.html?mode="+(television()?"tv":"phone"));
    }
    protected WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    public final class Bridge {
        @JavascriptInterface public void call(String id,String path,String data){
            if(id==null||!id.matches("[0-9]{1,12}")||path==null||path.length()>300||data==null||data.length()>17000)return;
            try{(path.equals("/v1/key")||path.equals("/v1/text")||path.equals("/v1/launch")?controls:worker).execute(()->{try{JSONObject result=request(path,data.isEmpty()?null:new JSONObject(data));emit("reply("+JSONObject.quote(id)+",null,"+result.toString()+")");}
                catch(Exception e){String message=e.getMessage()==null?"Could not complete that action":e.getMessage();emit("reply("+JSONObject.quote(id)+","+JSONObject.quote(message)+",null)");}});}catch(RejectedExecutionException e){emit("reply("+JSONObject.quote(id)+",\"Waiting for the TV…\",null)");}
        }
        @JavascriptInterface public void pick(String type,String target){
            if(!(type.equals("image")||type.equals("video"))||!target.equals("background"))return;
            runOnUiThread(()->{pickMime=type;pickTarget=target;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type+"/*").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,804);}catch(Exception e){emit("notify('No file picker is available on this device')");}});
        }
        @JavascriptInterface public void listen(){
            if(television()){emit("voiceError('Use the CouchLink phone app to speak to Jarvis')");return;}
            runOnUiThread(()->{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to Jarvis").putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);try{startActivityForResult(i,805);}catch(Exception e){emit("voiceError('No voice recognition service is available on this phone')");}});
        }
        @JavascriptInterface public void speak(String text){String safe=text==null?"":text.trim();if(safe.isEmpty())return;if(safe.length()>1000)safe=safe.substring(0,1000);final String line=safe;runOnUiThread(()->{if(speech!=null)speech.speak(line,TextToSpeech.QUEUE_FLUSH,null,"couchlink-jarvis");});}
    }
    protected void emit(String script){runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window."+script+";",null);});}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("pickMime",pickMime);state.putString("pickTarget",pickTarget);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(code==805){if(result==RESULT_OK&&data!=null){ArrayList<String> matches=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(matches!=null&&!matches.isEmpty())emit("voiceResult("+JSONObject.quote(matches.get(0))+")");else emit("voiceError('I did not catch that. Try again.')");}else emit("voiceError('Voice input was cancelled.')");return;}if(code==804&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();String mime=pickMime,target=pickTarget;emit("notify('Importing your "+mime+"…')");worker.execute(()->{try{pickResult(uri,mime,target);}catch(Exception e){emit("notify("+JSONObject.quote(e.getMessage()==null?"Import failed":e.getMessage())+")");}});}}
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();emit("resume&&window.resume()");}}
    @Override protected void onPause(){if(web!=null){emit("pause&&window.pause()");web.onPause();}super.onPause();}
    @Override public void onBackPressed(){emit("back()");}
    @Override protected void onDestroy(){web.removeJavascriptInterface("Native");web.destroy();if(speech!=null){speech.stop();speech.shutdown();speech=null;}worker.shutdownNow();controls.shutdownNow();super.onDestroy();}
}
