package dev.couchlink.core;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void mediaResponseStreamsExactBytesAndDeclaresLength()throws Exception{
        File file=File.createTempFile("couchlink-media-",".bin");try{byte[] data=new byte[18000];new SecureRandom().nextBytes(data);try(FileOutputStream output=new FileOutputStream(file)){output.write(data);}ByteArrayOutputStream response=new ByteArrayOutputStream();HttpCodec.respondFile(response,"video/mp4",file);byte[] wire=response.toByteArray();String headers=new String(wire,0,Math.min(256,wire.length),StandardCharsets.ISO_8859_1);int start=headers.indexOf("\r\n\r\n")+4;assertTrue(start>3);assertTrue(headers.contains("Content-Length: 18000\r\n"));assertArrayEquals(data,Arrays.copyOfRange(wire,start,wire.length));}finally{file.delete();}
    }
    @Test public void mediaResponseRefusesOversizedFileBeforeSendingHeaders()throws Exception{
        File file=File.createTempFile("couchlink-media-",".bin");try{try(RandomAccessFile sparse=new RandomAccessFile(file,"rw")){sparse.setLength(32L*1024*1024+1);}ByteArrayOutputStream response=new ByteArrayOutputStream();try{HttpCodec.respondFile(response,"video/mp4",file);fail();}catch(IOException expected){assertEquals(0,response.size());}}finally{file.delete();}
    }
    @Test public void textQuotingPreservesShellMetacharacters()throws Exception{String text="a 'quote'; $(printf injected) `printf wrong` $PATH";Process p=new ProcessBuilder("sh","-c","printf %s "+ShellText.quote(text)).start();assertEquals(text,new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8));assertEquals(0,p.waitFor());}
    @Test public void customKeyboardFallbackPreservesPercentAndRejectsUnsupportedText(){assertEquals(Arrays.asList("input text '100'","input text '%'","input text 's'"),ShellText.commands("100%s"));try{ShellText.commands("नमस्ते");fail();}catch(IllegalArgumentException expected){}}
    @Test public void generatedCertificateVerifiesWithStandardX509Parser()throws Exception{KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);KeyPair key=gen.generateKeyPair();java.security.cert.X509Certificate cert=SelfSigned.create(key);cert.checkValidity();cert.verify(key.getPublic());assertEquals(3,cert.getVersion());assertEquals("CN=CouchLink TV",cert.getSubjectX500Principal().getName());assertArrayEquals(key.getPublic().getEncoded(),cert.getPublicKey().getEncoded());}
    @Test public void pairingExpiresAndCannotBeReplayed(){AtomicLong clock=new AtomicLong(10);PairingGate g=new PairingGate(Set.of(),clock::get);String code=g.newCode();clock.addAndGet(300001);assertNull(g.enroll(code));code=g.newCode();String token=g.enroll(code);assertNotNull(token);assertTrue(g.authorized(token));assertNull(g.enroll(code));assertFalse(g.snapshot().contains(token));g.revokeAll();assertFalse(g.authorized(token));}
    @Test public void pairingLocksAfterFiveFailures(){PairingGate g=new PairingGate(Set.of(),()->1);String code=g.newCode();for(int i=0;i<5;i++)assertNull(g.enroll("wrong"));assertNull(g.enroll(code));assertEquals("------",g.displayCode());}
    @Test public void singlePhoneRevocationKeepsOtherPhones(){PairingGate g=new PairingGate(Set.of(),()->1);String first=g.enroll(g.newCode());String second=g.enroll(g.newCode());g.revoke(first);assertFalse(g.authorized(first));assertTrue(g.authorized(second));}
    @Test public void persistedHashAuthorizesAndRejectsMalformedToken(){PairingGate g=new PairingGate(Set.of(),()->1);String token=g.enroll(g.newCode());PairingGate restored=new PairingGate(g.snapshot(),()->1);assertTrue(restored.authorized(token));assertFalse(restored.authorized(token+"x"));assertFalse(restored.authorized(null));}
    @Test public void parserRejectsSmugglingAndOversizedBodies()throws Exception{
        for(String headers:new String[]{"Content-Length: 1\r\nContent-Length: 2","Transfer-Encoding: chunked","Content-Length: 17000","Content-Length: -1"}){
            try{HttpCodec.read(new ByteArrayInputStream(("POST /v1/key HTTP/1.1\r\n"+headers+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII)));fail();}catch(IOException expected){}
        }
    }
    @Test public void parserPreservesUnicodeBody()throws Exception{byte[] body="नमस्ते".getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(("POST /v1/text HTTP/1.1\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);assertArrayEquals(body,HttpCodec.read(new ByteArrayInputStream(out.toByteArray())).body);}
    @Test public void corruptPacketIsRejected()throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();AdbClient.write(out,AdbClient.WRTE,2,1,new byte[]{1,2,3});byte[] data=out.toByteArray();data[data.length-1]=9;try{AdbClient.read(new ByteArrayInputStream(data));fail();}catch(IOException expected){}}
    @Test public void pinsMustHaveExplicitSafeLength()throws Exception{for(String pin:new String[]{"","123","[a-z]","a".repeat(63)}){try{PinnedTls.factory(pin);fail();}catch(GeneralSecurityException expected){}}PinnedTls.factory("a".repeat(12));}
    @Test public void adbAuthenticatesAndPreservesBinaryStream()throws Exception{
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);KeyPair key=gen.generateKeyPair();byte[] token=new byte[20];new SecureRandom().nextBytes(token);byte[] binary={-119,80,78,71,0,13,10,-1};
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0)){
            Future<?> peer=executor.submit(()->{try(Socket s=server.accept()){
                InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();assertEquals(AdbClient.CNXN,AdbClient.read(in).command);AdbClient.write(out,AdbClient.AUTH,1,0,token);
                AdbClient.Packet signed=AdbClient.read(in);assertEquals(2,signed.arg0);assertArrayEquals(AdbClient.sign(token,key.getPrivate()),signed.data);
                AdbClient.write(out,AdbClient.CNXN,0x01000000,4096,new byte[0]);AdbClient.Packet open=AdbClient.read(in);assertEquals("exec:screencap -p\0",new String(open.data,StandardCharsets.UTF_8));
                AdbClient.write(out,AdbClient.OKAY,7,1,new byte[0]);AdbClient.write(out,AdbClient.WRTE,7,1,binary);assertEquals(AdbClient.OKAY,AdbClient.read(in).command);AdbClient.write(out,AdbClient.CLSE,7,1,new byte[0]);assertEquals(AdbClient.CLSE,AdbClient.read(in).command);
            }catch(Exception e){throw new RuntimeException(e);}});
            assertArrayEquals(binary,new AdbClient("127.0.0.1",server.getLocalPort(),key).execute("exec:screencap -p",1024,3000));peer.get(5,TimeUnit.SECONDS);
        }finally{executor.shutdownNow();}
    }
}
