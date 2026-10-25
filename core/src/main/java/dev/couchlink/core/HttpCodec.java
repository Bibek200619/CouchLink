package dev.couchlink.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small HTTP/1.1 request parser for the app's fixed local API. One request per connection. */
public final class HttpCodec {
    private HttpCodec(){}
    public static final class Request {
        public final String method,path;public final Map<String,String> headers;public final byte[] body;
        Request(String method,String path,Map<String,String> headers,byte[] body){this.method=method;this.path=path;this.headers=headers;this.body=body;}
    }
    public static Request read(InputStream in) throws IOException {
        String[] start=line(in).split(" ",-1);
        if(start.length!=3 || !(start[0].equals("GET")||start[0].equals("POST")) || !start[1].startsWith("/") || !start[2].equals("HTTP/1.1"))throw new IOException("Invalid request");
        Map<String,String> headers=new HashMap<>();int total=0;
        for(;;){String line=line(in);total+=line.length();if(total>16384)throw new IOException("Headers too large");if(line.isEmpty())break;
            int colon=line.indexOf(':');if(colon<1)throw new IOException("Invalid header");
            String key=line.substring(0,colon).toLowerCase(Locale.US);String value=line.substring(colon+1).trim();
            if(!key.matches("[a-z0-9-]+")||headers.put(key,value)!=null)throw new IOException("Duplicate or invalid header");
        }
        if(headers.containsKey("transfer-encoding"))throw new IOException("Chunked requests are not accepted");
        int size=0;try{size=Integer.parseInt(headers.getOrDefault("content-length","0"));}catch(NumberFormatException e){throw new IOException("Invalid length");}
        if(size<0||size>16384)throw new IOException("Body too large");
        byte[] body=new byte[size];int pos=0;while(pos<size){int n=in.read(body,pos,size-pos);if(n<0)throw new EOFException();pos+=n;}
        return new Request(start[0],start[1],headers,body);
    }
    private static String line(InputStream in)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int prev=-1;
        for(int i=0;i<4096;i++){int v=in.read();if(v<0)throw new EOFException();if(prev==13 && v==10){byte[] a=b.toByteArray();return new String(a,0,a.length-1,StandardCharsets.US_ASCII);}b.write(v);prev=v;}
        throw new IOException("Line too long");
    }
    public static void respond(OutputStream out,int code,String type,byte[] body)throws IOException {
        String reason=code==200?"OK":code==204?"No Content":code==401?"Unauthorized":code==404?"Not Found":code==429?"Too Many Requests":code==503?"Unavailable":"Bad Request";
        out.write(("HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);out.flush();
    }
    public static void respondFile(OutputStream out,String type,File file)throws IOException {
        long size=file.length();if(size<1||size>32L*1024*1024)throw new IOException("Media size is outside the limit");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nContent-Length: "+size+"\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;long remaining=size;while(remaining>0){n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException("Media changed during transfer");out.write(buffer,0,n);remaining-=n;}}out.flush();
    }
}
