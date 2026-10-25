package dev.couchlink.core;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/** A single bounded ADB stream. Every instance owns and closes its own TCP connection. */
public final class AdbClient {
    public static final int CNXN=0x4e584e43, AUTH=0x48545541, OPEN=0x4e45504f,
            OKAY=0x59414b4f, WRTE=0x45545257, CLSE=0x45534c43;
    private final String host;
    private final int port;
    private final KeyPair key;
    private final java.util.function.Consumer<String> progress;
    public AdbClient(String host, int port, KeyPair key) { this(host,port,key,message->{}); }
    public AdbClient(String host,int port,KeyPair key,java.util.function.Consumer<String> progress){this.host=host;this.port=port;this.key=key;this.progress=progress;}

    public byte[] execute(String service, int maxBytes, int timeoutMs) throws IOException, GeneralSecurityException {
        if (service.indexOf('\0')>=0 || service.length()>4095) throw new IOException("Invalid ADB service");
        try (Socket socket=new Socket()) {
            progress.accept("Connecting to TV controls…");
            socket.connect(new InetSocketAddress(host,port),5000);
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            InputStream in=socket.getInputStream(); OutputStream out=socket.getOutputStream();
            write(out,CNXN,0x01000000,4096,"host::couchlink\0".getBytes(StandardCharsets.UTF_8));
            progress.accept("Waiting for TV debug handshake…");
            boolean signed=false, offered=false;
            for (;;) {
                Packet p=read(in);
                if(p.command==CNXN) break;
                if(p.command!=AUTH || p.arg0!=1 || p.data.length!=20) throw new IOException("Unsupported ADB authentication");
                if(!signed) { write(out,AUTH,2,0,sign(p.data,key.getPrivate())); signed=true; }
                else if(!offered) { write(out,AUTH,3,0,publicKey(key)); offered=true; progress.accept("Accept the debugging prompt on TV"); }
                else throw new IOException("Accept CouchLink's USB debugging prompt on the TV");
            }
            write(out,OPEN,1,0,(service+'\0').getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            int remote=0;
            for (;;) {
                Packet p=read(in);
                if(p.arg1!=1) throw new IOException("Unexpected ADB stream ID");
                if(p.command==OKAY) { remote=p.arg0; }
                else if(p.command==WRTE) {
                    if(remote!=0 && remote!=p.arg0) throw new IOException("ADB stream changed");
                    remote=p.arg0;
                    if(result.size()>maxBytes-p.data.length) throw new IOException("ADB response exceeds limit");
                    result.write(p.data); write(out,OKAY,1,remote,new byte[0]);
                } else if(p.command==CLSE) {
                    write(out,CLSE,1,p.arg0,new byte[0]); return result.toByteArray();
                } else throw new IOException("Unexpected ADB packet");
            }
        }
    }
    public String shell(String command) throws IOException, GeneralSecurityException {
        return new String(execute("shell:"+command,1024*1024,15000),StandardCharsets.UTF_8).trim();
    }
    static byte[] sign(byte[] token, PrivateKey key) throws GeneralSecurityException {
        // ADB supplies the SHA-1 digest itself; hashing it again would produce the wrong signature.
        byte[] prefix={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        Signature s=Signature.getInstance("NONEwithRSA"); s.initSign(key); s.update(prefix); s.update(token); return s.sign();
    }
    public static byte[] publicKey(KeyPair key) {
        RSAPublicKey pub=(RSAPublicKey)key.getPublic(); BigInteger n=pub.getModulus();
        ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64); b.putInt(n.modInverse(BigInteger.ONE.shiftLeft(32)).negate().intValue());
        b.put(little(n,256)); b.put(little(BigInteger.ONE.shiftLeft(4096).mod(n),256));
        b.putInt(pub.getPublicExponent().intValue());
        return (Base64.getEncoder().encodeToString(b.array())+" couchlink@tv\0").getBytes(StandardCharsets.US_ASCII);
    }
    private static byte[] little(BigInteger n,int length) {
        byte[] big=n.toByteArray(), out=new byte[length];
        for(int i=0;i<length && i<big.length;i++) out[i]=big[big.length-1-i]; return out;
    }
    public static final class Packet {
        public final int command,arg0,arg1; public final byte[] data;
        Packet(int command,int arg0,int arg1,byte[] data) {this.command=command;this.arg0=arg0;this.arg1=arg1;this.data=data;}
    }
    public static Packet read(InputStream in) throws IOException {
        byte[] header=fully(in,24); ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int cmd=b.getInt(), a=b.getInt(), c=b.getInt(), length=b.getInt(), checksum=b.getInt(), magic=b.getInt();
        if(magic!=(cmd^0xffffffff) || length<0 || length>1024*1024) throw new IOException("Invalid ADB packet header");
        byte[] data=fully(in,length); int sum=0; for(byte v:data) sum+=v&255;
        if(checksum!=sum) throw new IOException("Invalid ADB checksum");
        return new Packet(cmd,a,c,data);
    }
    public static void write(OutputStream out,int command,int a,int b,byte[] data) throws IOException {
        int sum=0; for(byte v:data) sum+=v&255;
        ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(command).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(command^0xffffffff);
        out.write(h.array());out.write(data);out.flush();
    }
    private static byte[] fully(InputStream in,int size) throws IOException {
        byte[] result=new byte[size]; int pos=0;
        while(pos<size) {int n=in.read(result,pos,size-pos);if(n<0)throw new EOFException("ADB disconnected");pos+=n;}
        return result;
    }
}
