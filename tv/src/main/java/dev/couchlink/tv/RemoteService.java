package dev.couchlink.tv;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import dev.couchlink.core.*;
import dev.couchlink.ui.RelayConfig;
import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteService extends Service {
    static volatile RemoteService current;
    volatile String state="Starting secure connection…",error="";
    volatile boolean adbReady,relayOnline;
    volatile Identity identity;
    volatile PairingGate gate;
    volatile int port;
    volatile LauncherThemes themes;
    volatile StudioStore studio;
    private SSLServerSocket server;
    private NsdManager nsd;
    private NsdManager.RegistrationListener registration;
    private final ExecutorService startup=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService relay=Executors.newSingleThreadScheduledExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean relayTokensDirty=new java.util.concurrent.atomic.AtomicBoolean(true);
    private final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12));
    private final AtomicBoolean authorizing=new AtomicBoolean();
    private volatile boolean closed;
    private final Object frameLock=new Object();
    private byte[] cachedFrame;
    private long frameTime;
    private boolean fileCapture;
    private final java.util.concurrent.atomic.AtomicInteger frameRequests=new java.util.concurrent.atomic.AtomicInteger(),framesSent=new java.util.concurrent.atomic.AtomicInteger(),textRequests=new java.util.concurrent.atomic.AtomicInteger(),textsSent=new java.util.concurrent.atomic.AtomicInteger();
    private volatile String lastFrameIssue="",lastTextIssue="",lastTextMode="";
    private SharedPreferences prefs;
    private static final String IME="dev.couchlink.tv/.PhoneIme";
    private static final Map<String,Integer> KEYS=new HashMap<>();
    static {
        KEYS.put("up",19);KEYS.put("down",20);KEYS.put("left",21);KEYS.put("right",22);KEYS.put("ok",23);
        KEYS.put("home",3);KEYS.put("back",4);KEYS.put("menu",82);KEYS.put("play",85);KEYS.put("volume_up",24);KEYS.put("volume_down",25);KEYS.put("mute",164);
        KEYS.put("sleep",223);KEYS.put("wake",224);KEYS.put("delete",67);KEYS.put("enter",66);KEYS.put("inputs",178);
    }
    @Override public void onCreate(){
        super.onCreate();current=this;prefs=getSharedPreferences("remote",0);
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("remote","Phone remote",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RemoteService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(1,new Notification.Builder(this,"remote").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("CouchLink remote is ready")
            .setContentText("Paired phones can control this TV on your network").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Stop remote",stop).build()).build());
        startup.execute(()->{
            try {
                bindLocalNetwork();
                identity=new Identity(this);themes=new LauncherThemes(this,identity);studio=StudioStore.get(this);gate=new PairingGate(prefs.getStringSet("tokens",Collections.emptySet()),SystemClock::elapsedRealtime);gate.newCode();
                SSLServerSocket s=(SSLServerSocket)identity.tls.getServerSocketFactory().createServerSocket();
                s.setEnabledProtocols(new String[]{"TLSv1.2"});
                s.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"});
                s.setReuseAddress(true);try{s.bind(new InetSocketAddress(8787));}catch(BindException e){s.bind(new InetSocketAddress(0));}
                if(closed){s.close();return;}server=s;port=s.getLocalPort();advertise();state="Ready to pair";
                Thread accept=new Thread(this::acceptLoop,"couchlink-listener");accept.start();authorize();
                relay.execute(this::relayLoop);
            }catch(Exception e){error=friendly(e);state="Could not start remote";}
        });
    }
    private void bindLocalNetwork(){
        ConnectivityManager manager=getSystemService(ConnectivityManager.class);
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            if(caps!=null&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))){manager.bindProcessToNetwork(network);return;}
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"STOP".equals(intent.getAction())){
            prefs.edit().putBoolean("enabled",false).apply();
            startup.execute(()->{restoreKeyboard();stopSelf();});return START_NOT_STICKY;
        }
        prefs.edit().putBoolean("enabled",true).apply();return START_STICKY;
    }
    private void advertise(){
        nsd=getSystemService(NsdManager.class);String id=prefs.getString("id",null);if(id==null){id=UUID.randomUUID().toString();prefs.edit().putString("id",id).apply();}
        NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("CouchLink · "+Build.MANUFACTURER+" TV");info.setServiceType("_couchlink._tcp.");info.setPort(port);info.setAttribute("id",id);
        registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo s){}public void onRegistrationFailed(NsdServiceInfo s,int e){error="Discovery unavailable. Use the IP address below.";}public void onServiceUnregistered(NsdServiceInfo s){}public void onUnregistrationFailed(NsdServiceInfo s,int e){}};
        nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
    }
    private void acceptLoop(){
        while(!closed){try{SSLSocket socket=(SSLSocket)server.accept();socket.setSoTimeout(20000);try{clients.execute(()->serve(socket));}catch(RejectedExecutionException e){socket.close();}}catch(IOException e){if(!closed)error="Network listener stopped";break;}}
    }
    private void serve(SSLSocket socket){
        try(SSLSocket s=socket){
            s.startHandshake();HttpCodec.Request r=HttpCodec.read(s.getInputStream());
            if(r.path.equals("/v1/pair")&&r.method.equals("POST")){
                JSONObject body=json(r);String token=gate.enroll(body.optString("code"));
                if(token==null){reply(s,401,new JSONObject().put("error","Pairing code is wrong, expired, or already used. Select New pairing code on TV."));return;}
                prefs.edit().putStringSet("tokens",gate.snapshot()).apply();
                syncRelayTokens();
                reply(s,200,new JSONObject().put("token",token).put("fingerprint",identity.fingerprint).put("name",Build.MANUFACTURER+" TV").put("id",prefs.getString("id","")));return;
            }
            String auth=r.headers.getOrDefault("authorization","");
            if(!auth.startsWith("Bearer ")||!gate.authorized(auth.substring(7))){reply(s,401,new JSONObject().put("error","Pair this phone with the TV first"));return;}
            route(s,r);
        }catch(Exception e){if(e instanceof javax.net.ssl.SSLException)android.util.Log.w("CouchLink","TLS connection failed: "+friendly(e));}
    }
    private void route(SSLSocket s,HttpCodec.Request r)throws Exception{
        try {
            if(r.path.startsWith("/v1/studio/")){if(r.path.equals("/v1/studio/show")){requireAdb();shell("input keyevent WAKEUP; input keyevent HOME; am start -W -n dev.couchlink.tv/.HomeActivity");}JSONObject result=studio.api(r.path,r.method.equals("POST")?json(r):null);reply(s,200,result);return;}
            if(r.method.equals("GET")&&(r.path.startsWith("/v1/media?")||r.path.startsWith("/v1/thumb?"))){String id=Uri.parse(r.path).getQueryParameter("id");File file=r.path.startsWith("/v1/thumb?")?studio.thumb(id):studio.file(id);if(!file.isFile()||file.length()>32*1024*1024)throw new IllegalArgumentException("Media unavailable");HttpCodec.respondFile(s.getOutputStream(),file.getName().endsWith(".mp4")?"video/mp4":"image/jpeg",file);return;}
            if(r.method.equals("GET")&&r.path.equals("/v1/status")){
                AudioManager audio=getSystemService(AudioManager.class);
                reply(s,200,new JSONObject().put("name",Build.MANUFACTURER+" TV").put("adbReady",adbReady).put("error",error)
                    .put("volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC)).put("maxVolume",audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).put("screenMode","preview").put("theme",prefs.getString("theme","original"))
                    .put("diagnostics",new JSONObject().put("frameRequests",frameRequests.get()).put("framesSent",framesSent.get()).put("lastFrameIssue",lastFrameIssue).put("textRequests",textRequests.get()).put("textsSent",textsSent.get()).put("lastTextIssue",lastTextIssue).put("textMode",lastTextMode)));return;
            }
            if(r.method.equals("GET")&&r.path.equals("/v1/apps")){reply(s,200,new JSONObject().put("apps",apps()));return;}
            if(r.method.equals("POST")&&r.path.equals("/v1/theme")){requireAdb();String name=json(r).optString("theme");themes.apply(name);reply(s,200,new JSONObject().put("ok",true).put("theme",name));return;}
            if(r.method.equals("POST")&&r.path.equals("/v1/unpair")){gate.revoke(r.headers.get("authorization").substring(7));prefs.edit().putStringSet("tokens",gate.snapshot()).apply();syncRelayTokens();reply(s,200,new JSONObject().put("ok",true));return;}
            if(r.method.equals("GET")&&r.path.startsWith("/v1/icon?")){
                String pkg=Uri.parse(r.path).getQueryParameter("package");if(find(pkg)==null)throw new IllegalArgumentException("App unavailable");
                Drawable d=getPackageManager().getApplicationIcon(pkg);Bitmap b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);d.setBounds(0,0,96,96);d.draw(canvas);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();HttpCodec.respond(s.getOutputStream(),200,"image/png",out.toByteArray());return;
            }
            if(r.method.equals("GET")&&r.path.equals("/v1/frame")){
                frameRequests.incrementAndGet();requireAdb();byte[] frame=frame();if(frame!=null){framesSent.incrementAndGet();String recovered=lastFrameIssue;lastFrameIssue="";if(!recovered.isEmpty()&&recovered.equals(error))error="";}else lastFrameIssue="TV returned an empty capture for this screen";HttpCodec.respond(s.getOutputStream(),frame==null?204:200,"image/jpeg",frame==null?new byte[0]:frame);return;
            }
            if(r.method.equals("POST")&&r.path.equals("/v1/key")){
                requireAdb();String action=json(r).optString("key");
                if(action.equals("settings"))shell("am start -a android.settings.SETTINGS");
                else if(action.equals("wake")){shell("input keyevent WAKEUP");shell("input keyevent HOME");}
                else{Integer key=KEYS.get(action);if(key==null)throw new IllegalArgumentException("Unknown control");shell("input keyevent "+key);}
                reply(s,200,new JSONObject().put("ok",true));return;
            }
            if(r.method.equals("POST")&&r.path.equals("/v1/tap")){
                requireAdb();JSONObject b=json(r);double x=b.getDouble("x"),y=b.getDouble("y");if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("Tap is outside the TV screen");
                DisplayMetrics metrics=new DisplayMetrics();getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
                shell("input tap "+Math.min(metrics.widthPixels-1,(int)(x*metrics.widthPixels))+" "+Math.min(metrics.heightPixels-1,(int)(y*metrics.heightPixels)));reply(s,200,new JSONObject().put("ok",true));return;
            }
            if(r.method.equals("POST")&&r.path.equals("/v1/launch")){
                requireAdb();String pkg=json(r).optString("package");Intent launch=find(pkg);if(launch==null||launch.getComponent()==null)throw new IllegalArgumentException("This app cannot be launched");
                String component=launch.getComponent().flattenToString();if(!component.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))throw new IllegalArgumentException("Invalid app component");
                String result=shell("am start -W -n '"+component+"'");if(result.contains("Error:"))throw new IllegalArgumentException("The TV could not open this app");reply(s,200,new JSONObject().put("ok",true));return;
            }
            if(r.method.equals("POST")&&r.path.equals("/v1/text")){
                textRequests.incrementAndGet();requireAdb();String text=json(r).optString("text");if(text.isEmpty()||text.length()>2000)throw new IllegalArgumentException("Enter between 1 and 2,000 characters");
                String foreground=shell("dumpsys activity activities | grep mResumedActivity");boolean youtube=foreground.contains("com.google.android.youtube.tv/");
                boolean committed=false;
                if(!youtube){
                    enableKeyboard();
                    try{for(int i=0;i<4&&!committed;i++){committed=PhoneIme.commit(text);if(!committed)Thread.sleep(200);}}
                    finally{restoreKeyboard();}
                }
                if(!committed)for(String command:ShellText.commands(text))shell(command);
                textsSent.incrementAndGet();lastTextIssue="";lastTextMode=youtube?"youtube_keys":committed?"input_method":"key_events";reply(s,200,new JSONObject().put("ok",true));return;
            }
            reply(s,404,new JSONObject().put("error","Unknown route"));
        }catch(IllegalArgumentException|JSONException e){if(r.path.equals("/v1/text"))lastTextIssue=friendly(e);if(r.path.equals("/v1/frame"))lastFrameIssue=friendly(e);reply(s,400,new JSONObject().put("error",e.getMessage()));}
        catch(Exception e){error=friendly(e);if(r.path.equals("/v1/text"))lastTextIssue=error;if(r.path.equals("/v1/frame"))lastFrameIssue=error;if(!r.path.equals("/v1/frame")&&(e instanceof IOException||e instanceof java.security.GeneralSecurityException))adbReady=false;reply(s,503,new JSONObject().put("error",error));}
    }
    private void requireAdb(){if(!adbReady)throw new IllegalArgumentException("Open CouchLink TV and choose Authorize TV controls. Keep USB / network debugging enabled.");}
    private AdbClient adb(){return new AdbClient("127.0.0.1",5555,identity.adbKey);}
    private String shell(String command)throws Exception{return adb().shell(command);}
    void authorize(){
        if(identity==null||!authorizing.compareAndSet(false,true))return;
        new Thread(()->{try{String result=new String(new AdbClient("127.0.0.1",5555,identity.adbKey,message->state=message).execute("shell:echo couchlink-ready",4096,120000),StandardCharsets.UTF_8);adbReady=result.contains("couchlink-ready");state=adbReady?"Remote controls ready":"Authorization needed";error=adbReady?"":"TV control authorization did not complete";if(adbReady)restoreKeyboard();}catch(Exception e){adbReady=false;error="Enable USB/network debugging, then accept the CouchLink debugging prompt on the TV. "+friendly(e);}finally{authorizing.set(false);}},"couchlink-adb-auth").start();
    }
    private synchronized void enableKeyboard()throws Exception{
        String original=shell("settings get secure default_input_method");
        if(!original.equals(IME)&&!original.equals("dev.couchlink.tv/dev.couchlink.tv.PhoneIme")){
            if(!original.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))throw new IllegalArgumentException("Unable to save the TV's original keyboard");
            prefs.edit().putString("previousIme",original).commit();shell("ime enable "+IME);shell("ime set "+IME);
        }
    }
    synchronized void restoreKeyboard(){
        try{String original=prefs.getString("previousIme","");if(original.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")){String selected=shell("settings get secure default_input_method");if(selected.startsWith("dev.couchlink.tv/"))shell("ime set "+original);prefs.edit().remove("previousIme").apply();}}catch(Exception e){error="Select your usual keyboard in TV settings to restore it.";}
    }
    private byte[] frame()throws Exception{
        synchronized(frameLock){
            if(cachedFrame!=null&&SystemClock.elapsedRealtime()-frameTime<700)return cachedFrame;
            byte[] png;
            if(!fileCapture){png=adb().execute("exec:screencap -p",8*1024*1024,20000);if(png.length<8||png[0]!=(byte)137||png[1]!=80)fileCapture=true;}
            else png=new byte[0];
            if(fileCapture){shell("screencap -p /data/local/tmp/couchlink-screen.png");try{png=adb().execute("exec:cat /data/local/tmp/couchlink-screen.png",8*1024*1024,20000);}finally{shell("rm -f /data/local/tmp/couchlink-screen.png");}}
            if(png.length<8){cachedFrame=null;return null;}
            Bitmap b=BitmapFactory.decodeByteArray(png,0,png.length);if(b==null){cachedFrame=null;return null;}
            Bitmap scaled=b;if(b.getWidth()>960)scaled=Bitmap.createScaledBitmap(b,960,Math.max(1,b.getHeight()*960/b.getWidth()),true);
            ByteArrayOutputStream out=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,65,out);if(scaled!=b)scaled.recycle();b.recycle();cachedFrame=out.toByteArray();frameTime=SystemClock.elapsedRealtime();return cachedFrame;
        }
    }
    JSONArray apps()throws JSONException{
        List<ResolveInfo> list=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0);
        list.sort((a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();
        for(ResolveInfo info:list){String p=info.activityInfo.packageName;if(safe(p)&&seen.add(p))result.put(new JSONObject().put("package",p).put("label",info.loadLabel(getPackageManager()).toString()));}
        return result;
    }
    private boolean safe(String pkg){if(pkg==null)return false;String p=pkg.toLowerCase(Locale.US);return !p.contains("factory")&&!p.contains("production")&&!p.contains("fac.menu")&&!p.contains("hkcfac");}
    Intent find(String pkg){if(!safe(pkg))return null;return getPackageManager().getLeanbackLaunchIntentForPackage(pkg);}
    private static JSONObject json(HttpCodec.Request r)throws JSONException{return new JSONObject(new String(r.body,StandardCharsets.UTF_8));}
    private static void reply(SSLSocket s,int status,JSONObject object)throws IOException{HttpCodec.respond(s.getOutputStream(),status,"application/json; charset=utf-8",object.toString().getBytes(StandardCharsets.UTF_8));}
    static String friendly(Exception e){if(e instanceof java.net.SocketTimeoutException)return "TV response timed out. Try Authorize TV controls again.";return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    String addresses(){
        List<String> result=new ArrayList<>();try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))if(n.isUp()&&!n.isLoopback())for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&!a.isLoopbackAddress())result.add(a.getHostAddress()+":"+port);}catch(Exception ignored){}return String.join("   ",result);
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private synchronized String relaySecret(){String value=prefs.getString("relaySecret","");if(value.length()<32){byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);value=android.util.Base64.encodeToString(bytes,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);prefs.edit().putString("relaySecret",value).commit();}return value;}
    private JSONObject cloud(String action,JSONObject data)throws Exception{
        JSONObject payload=new JSONObject(data.toString()).put("action",action).put("device_id",prefs.getString("id",""));
        HttpsURLConnection c=(HttpsURLConnection)new URL(RelayConfig.URL).openConnection();c.setConnectTimeout(9000);c.setReadTimeout(15000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("apikey",RelayConfig.API_KEY);c.setRequestProperty("Content-Type","application/json");
        byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream src=in){byte[] b=new byte[4096];int n;while((n=src.read(b))!=-1){if(out.size()+n>256*1024)throw new IOException("Relay response too large");out.write(b,0,n);}}
        JSONObject response=new JSONObject(out.toString("UTF-8"));if(code>=300)throw new IOException(response.optString("error","Cloud relay error"));return response;
    }
    void syncRelayTokens(){relayTokensDirty.set(true);}
    private void relayLoop(){
        long lastRegister=0;
        while(!closed){try{
            if(relayTokensDirty.getAndSet(false)||SystemClock.elapsedRealtime()-lastRegister>30000){JSONArray tokenHashes=new JSONArray();for(String hash:gate.snapshot())tokenHashes.put(hash);cloud("register",new JSONObject().put("secret",relaySecret()).put("token_hashes",tokenHashes));lastRegister=SystemClock.elapsedRealtime();}
            JSONObject response=cloud("poll",new JSONObject().put("secret",relaySecret()));relayOnline=true;JSONObject command=response.optJSONObject("command");
            if(command==null){Thread.sleep(900);continue;}
            JSONObject request=command.getJSONObject("request");JSONObject result;
            try{result=loopback(request.optString("path"),request.optJSONObject("body"),command.optString("phone_token"));}
            catch(Exception e){result=new JSONObject().put("code",503).put("body",new JSONObject().put("error",friendly(e))).put("contentType","application/json");}
            cloud("complete",new JSONObject().put("secret",relaySecret()).put("command_id",command.getString("command_id")).put("response",result));
        }catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){relayOnline=false;try{Thread.sleep(2500);}catch(InterruptedException stop){Thread.currentThread().interrupt();return;}}}
    }
    private JSONObject loopback(String path,JSONObject body,String phoneToken)throws Exception{
        if(!path.matches("/v1/[A-Za-z0-9_./?=&%-]{1,240}"))throw new IllegalArgumentException("Invalid TV route");
        if(!gate.authorized(phoneToken))throw new IllegalArgumentException("Phone was unpaired");
        String bearer=phoneToken;
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://127.0.0.1:"+port+path).openConnection();
        X509TrustManager trust=new X509TrustManager(){public java.security.cert.X509Certificate[] getAcceptedIssuers(){return new java.security.cert.X509Certificate[0];}public void checkClientTrusted(java.security.cert.X509Certificate[] chain,String auth){}public void checkServerTrusted(java.security.cert.X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{try{if(chain.length==0||!PairingGate.fingerprint(chain[0].getEncoded()).equals(identity.fingerprint))throw new java.security.cert.CertificateException("TV identity changed");}catch(java.security.cert.CertificateException e){throw e;}catch(Exception e){throw new java.security.cert.CertificateException(e);}}};
        SSLContext clientTls=SSLContext.getInstance("TLS");clientTls.init(null,new TrustManager[]{trust},new java.security.SecureRandom());c.setSSLSocketFactory(clientTls.getSocketFactory());
        c.setHostnameVerifier((host,session)->{try{return PairingGate.fingerprint(session.getPeerCertificates()[0].getEncoded()).equals(identity.fingerprint);}catch(Exception e){return false;}});
        c.setConnectTimeout(4000);c.setReadTimeout(path.equals("/v1/frame")?25000:15000);c.setRequestProperty("Authorization","Bearer "+bearer);
        if(body!=null){byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(data);}}
        int code=c.getResponseCode();String type=c.getContentType()==null?"application/json":c.getContentType();InputStream in=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(code!=204&&in!=null)try(InputStream src=in){byte[] b=new byte[8192];int n;while((n=src.read(b))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("TV response too large");out.write(b,0,n);}}
        JSONObject result=new JSONObject().put("code",code).put("contentType",type);
        if(type.startsWith("application/json")){try{result.put("body",new JSONObject(out.toString("UTF-8")));}catch(JSONException e){result.put("body",new JSONObject().put("error","TV returned invalid data"));}}
        else result.put("base64",android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));
        c.disconnect();return result;
    }
    private static String sha256(String value)throws Exception{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.US,"%02x",b));return out.toString();}
    @Override public void onDestroy(){closed=true;current=null;relay.shutdownNow();try{if(server!=null)server.close();}catch(IOException ignored){}try{if(nsd!=null&&registration!=null)nsd.unregisterService(registration);}catch(Exception ignored){}clients.shutdownNow();startup.shutdownNow();super.onDestroy();}
}
