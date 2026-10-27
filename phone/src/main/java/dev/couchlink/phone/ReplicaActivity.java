package dev.couchlink.phone;
import android.content.*;
import android.net.Uri;
import android.net.*;
import android.net.nsd.*;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.*;
import dev.couchlink.ui.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class ReplicaActivity extends LocalWebActivity {
    private SharedPreferences prefs;
    private NsdManager discoveryManager;
    private NsdManager.DiscoveryListener discovery;
    private boolean resolving;
    private final ArrayDeque<NsdServiceInfo> found=new ArrayDeque<>();
    private final Map<String,byte[]> imageCache=Collections.synchronizedMap(new LinkedHashMap<String,byte[]>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,byte[]> e){return size()>40;}});
    @Override protected boolean television(){return false;}
    @Override public void onCreate(Bundle state){prefs=getSharedPreferences("phone",0);super.onCreate(state);discover();Uri data=getIntent().getData();if(data!=null&&"couchlink".equals(data.getScheme())&&"pair".equals(data.getHost())){String address=data.getQueryParameter("address"),code=data.getQueryParameter("code"),pin=data.getQueryParameter("fingerprint");if(address!=null&&address.length()<150&&code!=null&&code.matches("[0-9]{6}")&&pin!=null&&pin.matches("[A-Fa-f0-9]{12}"))startActivity(new Intent(this,MainActivity.class).putExtra("replica",true).putExtra("qrAddress",address).putExtra("qrCode",code).putExtra("qrPin",pin));}}
    @Override protected void onResume(){ConnectivityManager manager=getSystemService(ConnectivityManager.class);for(Network n:manager.getAllNetworks()){NetworkCapabilities caps=manager.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){manager.bindProcessToNetwork(n);break;}}super.onResume();}
    private void discover(){discoveryManager=getSystemService(NsdManager.class);discovery=new NsdManager.DiscoveryListener(){public void onDiscoveryStarted(String type){}public void onDiscoveryStopped(String type){}public void onStartDiscoveryFailed(String type,int error){}public void onStopDiscoveryFailed(String type,int error){}public void onServiceLost(NsdServiceInfo s){}public void onServiceFound(NsdServiceInfo s){runOnUiThread(()->{if(found.size()<8){found.add(s);resolveNext();}});}};try{discoveryManager.discoverServices("_couchlink._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);}catch(Exception ignored){}}
    @SuppressWarnings("deprecation") private void resolveNext(){if(resolving||found.isEmpty()||isDestroyed())return;resolving=true;discoveryManager.resolveService(found.remove(),new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo s,int error){runOnUiThread(()->{resolving=false;resolveNext();});}public void onServiceResolved(NsdServiceInfo s){runOnUiThread(()->{byte[] id=s.getAttributes().get("id");if(id!=null&&s.getHost()!=null&&new String(id,java.nio.charset.StandardCharsets.UTF_8).equals(prefs.getString("id","")))prefs.edit().putString("host",s.getHost().getHostAddress()).putInt("port",s.getPort()).apply();resolving=false;resolveNext();});}});}
    @Override protected void onDestroy(){try{if(discoveryManager!=null&&discovery!=null)discoveryManager.stopServiceDiscovery(discovery);}catch(Exception ignored){}super.onDestroy();}
    private RemoteApi api(){String token=prefs.getString("token","");if(token.isEmpty())throw new IllegalArgumentException("Tap Connect to pair with your TV");prefs.edit().remove("relaySecret").apply();return new RemoteApi(prefs.getString("host",""),prefs.getInt("port",8787),prefs.getString("pin",""),token,prefs.getString("id",""));}
    @Override protected JSONObject request(String path,JSONObject body)throws Exception{
        if(path.equals("/native/connection")){boolean paired=!prefs.getString("token","").isEmpty();return new JSONObject().put("paired",paired).put("name",prefs.getString("name","Nova")).put("host",prefs.getString("host",""));}
        if(path.equals("/native/pair")){runOnUiThread(()->startActivity(new Intent(this,MainActivity.class).putExtra("replica",true)));return new JSONObject();}
        if(path.equals("/native/disconnect")){RemoteApi old=api();old.call("/v1/unpair",new JSONObject());prefs.edit().clear().apply();return new JSONObject();}
        if(path.equals("/native/assistant")){String message=body==null?"":body.optString("message","");if(message.trim().isEmpty()||message.length()>600)throw new IllegalArgumentException("Ask a question up to 600 characters");return api().assistant(message);}
        if(path.equals("/native/frame")){RemoteApi.Reply frame=api().call("/v1/frame",null);return new JSONObject().put("image",frame.code==204?"":"data:image/jpeg;base64,"+Base64.encodeToString(frame.bytes,Base64.NO_WRAP));}
        if(path.startsWith("/v1/studio/")||Arrays.asList("/v1/status","/v1/apps","/v1/key","/v1/text","/v1/launch","/v1/tap").contains(path))return api().call(path,body).json();
        throw new IllegalArgumentException("Unknown phone action");
    }
    @Override protected WebResourceResponse resource(String path)throws Exception{
        if(!(path.startsWith("/icon?")||path.startsWith("/thumb?")))return denied();byte[] data=imageCache.get(path);if(data==null){data=api().call("/v1"+path,null).bytes;imageCache.put(path,data);}return new WebResourceResponse(path.startsWith("/icon?")?"image/png":"image/jpeg",null,new ByteArrayInputStream(data));
    }
    @Override protected void pickResult(Uri uri,String type,String target)throws Exception{RemoteApi targetApi=api();String id=MediaImport.send(this,uri,type,(path,body)->targetApi.call(path,body).json(),message->emit("notify("+JSONObject.quote(message)+")"));emit("imported("+JSONObject.quote(id)+","+JSONObject.quote(target)+")");}
}
