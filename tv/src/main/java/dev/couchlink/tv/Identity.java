package dev.couchlink.tv;

import android.content.Context;
import android.security.keystore.*;
import dev.couchlink.core.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.*;
import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.Arrays;

/** Keystore-encrypted software TLS key for TVs with broken hardware RSA TLS hooks. */
final class Identity {
    final SSLContext tls;
    final String fingerprint;
    final KeyPair adbKey;
    Identity(Context context)throws Exception{
        KeyStore hardware=KeyStore.getInstance("AndroidKeyStore");hardware.load(null);
        String alias="couchlink-identity-wrap";
        if(!hardware.containsAlias(alias)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());gen.generateKey();
        }
        javax.crypto.SecretKey wrap=(javax.crypto.SecretKey)hardware.getKey(alias,null);
        File certFile=new File(context.getFilesDir(),"identity.der"),privateFile=new File(context.getFilesDir(),"identity.enc");
        X509Certificate cert;PrivateKey key;
        if(certFile.exists()&&privateFile.exists()){
            cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Files.readAllBytes(certFile.toPath())));
            byte[] encrypted=Files.readAllBytes(privateFile.toPath());if(encrypted.length<29)throw new IOException("TV identity is damaged");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,wrap,new GCMParameterSpec(128,Arrays.copyOf(encrypted,12)));
            key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(cipher.doFinal(Arrays.copyOfRange(encrypted,12,encrypted.length))));
        }else{
            KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);KeyPair pair=gen.generateKeyPair();key=pair.getPrivate();cert=SelfSigned.create(pair);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrap);byte[] encrypted=cipher.doFinal(key.getEncoded());
            try(FileOutputStream out=context.openFileOutput("identity.enc",Context.MODE_PRIVATE)){out.write(cipher.getIV());out.write(encrypted);}
            try(FileOutputStream out=context.openFileOutput("identity.der",Context.MODE_PRIVATE)){out.write(cert.getEncoded());}
        }
        fingerprint=PairingGate.fingerprint(cert.getEncoded());
        KeyStore software=KeyStore.getInstance("PKCS12");software.load(null);software.setKeyEntry("tv",key,new char[0],new java.security.cert.Certificate[]{cert});
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(software,new char[0]);
        tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,new SecureRandom());
        File adbPrivate=new File(context.getFilesDir(),"adb.pk8"),adbPublic=new File(context.getFilesDir(),"adb.pub");
        if(adbPrivate.exists()&&adbPublic.exists()){
            KeyFactory f=KeyFactory.getInstance("RSA");adbKey=new KeyPair(f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(adbPublic.toPath()))),f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(adbPrivate.toPath()))));
        }else{
            KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);adbKey=gen.generateKeyPair();
            try(FileOutputStream out=context.openFileOutput("adb.pk8",Context.MODE_PRIVATE)){out.write(adbKey.getPrivate().getEncoded());}
            try(FileOutputStream out=context.openFileOutput("adb.pub",Context.MODE_PRIVATE)){out.write(adbKey.getPublic().getEncoded());}
        }
    }
}
