package dev.couchlink.phone;

import dev.couchlink.core.*;
import dev.couchlink.ui.RelayConfig;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class RemoteApi {
    final String host,pin,token,deviceId;
    final int port;
    RemoteApi(String host,int port,String pin,String token){this(host,port,pin,token,"");}
    RemoteApi(String host,int port,String pin,String token,String deviceId){this.host=host;this.port=port;this.pin=pin;this.token=token;this.deviceId=deviceId;}
    static final class Reply {final byte[] bytes;final int code;final String certificate;Reply(byte[] b,int c,String f){bytes=b;code=c;certificate=f;}JSONObject json()throws JSONException{return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}}
    Reply call(String path,JSONObject body)throws Exception{
        try{return direct(path,body);}catch(Exception local){if(deviceId.isEmpty()||token.isEmpty())throw local;try{return relay(path,body);}catch(Exception cloud){cloud.addSuppressed(local);throw cloud;}}
    }
    private Reply direct(String path,JSONObject body)throws Exception{
        HttpsURLConnection c=(HttpsURLConnection)new URL("https",host,port,path).openConnection();
        c.setSSLSocketFactory(PinnedTls.factory(pin));
        // The trust manager validates the TV's certificate pin; its private local IP is not a DNS name.
        c.setHostnameVerifier((name,session)->{try{return PairingGate.fingerprint(session.getPeerCertificates()[0].getEncoded()).startsWith(pin.toLowerCase(java.util.Locale.US));}catch(Exception e){return false;}});
        c.setConnectTimeout(5000);c.setReadTimeout(path.equals("/v1/frame")?25000:15000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept",path.equals("/v1/frame")?"image/jpeg":"application/json");
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(bytes.length);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();String fingerprint=PairingGate.fingerprint(c.getServerCertificates()[0].getEncoded());
            InputStream input=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(input!=null)try(InputStream in=input){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()>4*1024*1024-n)throw new IOException("TV response is too large");out.write(buffer,0,n);}}
            byte[] bytes=out.toByteArray();
            if(code>=300){String error="TV returned "+code;try{error=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).optString("error",error);}catch(JSONException ignored){}throw new IOException(error);}
            return new Reply(bytes,code,fingerprint);
        }finally{c.disconnect();}
    }
    private Reply relay(String path,JSONObject body)throws Exception{
        JSONObject request=new JSONObject().put("action","phone").put("device_id",deviceId).put("token",token).put("path",path).put("body",body);
        HttpsURLConnection c=(HttpsURLConnection)new URL(RelayConfig.URL).openConnection();c.setConnectTimeout(9000);c.setReadTimeout(path.equals("/v1/frame")?50000:40000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("apikey",RelayConfig.API_KEY);c.setRequestProperty("Content-Type","application/json");
        byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
        int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream src=in){byte[] b=new byte[8192];int n;while((n=src.read(b))!=-1){if(out.size()+n>10*1024*1024)throw new IOException("Relay response too large");out.write(b,0,n);}}
        if(status>=300)throw new IOException(new JSONObject(out.toString("UTF-8")).optString("error","Cloud relay returned "+status));
        JSONObject response=new JSONObject(out.toString("UTF-8"));int code=response.optInt("code",502);byte[] result;
        if(response.has("base64"))result=android.util.Base64.decode(response.getString("base64"),android.util.Base64.DEFAULT);
        else result=response.optJSONObject("body")==null?new byte[0]:response.getJSONObject("body").toString().getBytes(StandardCharsets.UTF_8);
        if(code>=300){String message="TV returned "+code;try{message=new JSONObject(new String(result,StandardCharsets.UTF_8)).optString("error",message);}catch(Exception ignored){}throw new IOException(message);}
        return new Reply(result,code,pin);
    }
    JSONObject assistant(String message)throws Exception{
        if(deviceId.isEmpty()||token==null||token.length()<32)throw new IOException("Pair CouchLink with your TV before using Jarvis.");
        HttpsURLConnection c=(HttpsURLConnection)new URL(RelayConfig.URL).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(45000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("apikey",RelayConfig.API_KEY);c.setRequestProperty("Content-Type","application/json");
        JSONObject request=new JSONObject().put("action","assistant").put("device_id",deviceId).put("token",token).put("message",message);
        byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
        try{try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream src=in){byte[] b=new byte[4096];int n;while((n=src.read(b))!=-1){if(out.size()+n>24000)throw new IOException("Jarvis response is too large");out.write(b,0,n);}}JSONObject result=new JSONObject(out.toString("UTF-8"));if(status>=300)throw new IOException(result.optString("error","Jarvis could not answer right now."));return result;}finally{c.disconnect();}
    }
    JSONObject get(String path)throws Exception{return call(path,null).json();}
    void key(String action)throws Exception{call("/v1/key",new JSONObject().put("key",action));}
}
