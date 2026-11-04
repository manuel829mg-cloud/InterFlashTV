import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
public class SignApk {
 public static void main(String[] a) throws Exception {
  Properties p=new Properties();
  for(String l:Files.readAllLines(Path.of(a[0]))) {int i=l.indexOf(':'); if(i>0)p.setProperty(l.substring(0,i).trim(),l.substring(i+1).trim());}
  KeyStore ks=KeyStore.getInstance(new File(a[1]),p.getProperty("Contraseña del almacén").toCharArray());
  String alias=p.getProperty("Alias");
  PrivateKey key=(PrivateKey)ks.getKey(alias,p.getProperty("Contraseña de clave").toCharArray());
  var cert=(X509Certificate)ks.getCertificate(alias);
  var conf=new ApkSigner.SignerConfig.Builder(alias,key,List.of(cert)).build();
  new ApkSigner.Builder(List.of(conf)).setInputApk(new File(a[2])).setOutputApk(new File(a[3])).setMinSdkVersion(26).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign();
  var result=new ApkVerifier.Builder(new File(a[3])).setMinCheckedPlatformVersion(26).build().verify();
  if(!result.isVerified())throw new Exception(result.getErrors().toString());
  var old=new ApkVerifier.Builder(new File(a[4])).setMinCheckedPlatformVersion(26).build().verify();
  if(!old.isVerified() || !Arrays.equals(result.getSignerCertificates().get(0).getEncoded(),old.getSignerCertificates().get(0).getEncoded()))throw new Exception("Installed-version certificate mismatch");
  System.out.println("Verified APK signature and identical v0.28 update certificate");
 }
}
