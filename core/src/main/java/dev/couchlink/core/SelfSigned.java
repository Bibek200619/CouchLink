package dev.couchlink.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Minimal RFC 5280 self-signed leaf; trust comes from the displayed fingerprint. */
public final class SelfSigned {
    private SelfSigned(){}
    public static X509Certificate create(KeyPair pair)throws GeneralSecurityException{
        byte[] algorithm={0x30,0x0d,0x06,0x09,0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x0b,0x05,0x00};
        byte[] cn=seq(tag(0x31,seq(new byte[]{0x06,0x03,0x55,0x04,0x03},tag(0x0c,"CouchLink TV".getBytes(StandardCharsets.UTF_8)))));
        byte[] serial=new byte[16];new SecureRandom().nextBytes(serial);serial[0]&=0x7f;serial[0]|=1;
        long now=System.currentTimeMillis();
        byte[] tbs=seq(tag(0xa0,tag(2,new byte[]{2})),tag(2,serial),algorithm,cn,seq(time(new Date(now-86400000L)),time(new Date(now+315360000000L))),cn,pair.getPublic().getEncoded());
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(tbs);
        byte[] signed=signature.sign(),bits=new byte[signed.length+1];System.arraycopy(signed,0,bits,1,signed.length);
        return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(seq(tbs,algorithm,tag(3,bits))));
    }
    private static byte[] time(Date date){SimpleDateFormat f=new SimpleDateFormat("yyMMddHHmmss'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return tag(0x17,f.format(date).getBytes(StandardCharsets.US_ASCII));}
    private static byte[] seq(byte[]... parts){ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] p:parts)b.write(p,0,p.length);return tag(0x30,b.toByteArray());}
    private static byte[] tag(int type,byte[] value){ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(type);int n=value.length;if(n<128)b.write(n);else{int size=(32-Integer.numberOfLeadingZeros(n)+7)/8;b.write(128|size);for(int i=size-1;i>=0;i--)b.write(n>>>(8*i));}b.write(value,0,n);return b.toByteArray();}
}
