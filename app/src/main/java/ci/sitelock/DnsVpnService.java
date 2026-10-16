package ci.sitelock;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/* Minimal IPv4 DNS VPN. It blocks exact listed domains and forwards other DNS requests to Cloudflare.
   It is a starter implementation, not a tamper-proof parental-control system. */
public class DnsVpnService extends VpnService {
    private ParcelFileDescriptor tun; private Thread worker; private volatile boolean running;
    private final Set<String> blocked = new HashSet<>(Arrays.asList(
        "betclic.ci","1xbet.ci","melbet.ci","betwinner.com","22bet.com","1win.com","betway.com","bet365.com","unibet.com","bwin.com","paripesa.com","mostbet.com","sportybet.com","stake.com","bc.game","betano.com","sportaza.com",
        "pornhub.com","xvideos.com","xnxx.com","xhamster.com","redtube.com","youporn.com","onlyfans.com","chaturbate.com","livejasmin.com","spankbang.com","eporner.com"
    ));
    @Override public int onStartCommand(android.content.Intent intent,int flags,int id){ if(running)return START_STICKY;
        SharedPreferences p=getSharedPreferences("sitelock",MODE_PRIVATE); if(System.currentTimeMillis()>=p.getLong("until",0)){stopSelf();return START_NOT_STICKY;}
        try { Builder b=new Builder().setSession("SiteLock CI").addAddress("10.111.222.1",32).addDnsServer("10.111.222.2").addRoute("10.111.222.2",32).setMtu(1500); tun=b.establish(); if(tun==null){stopSelf();return START_NOT_STICKY;} running=true; worker=new Thread(this::loop,"SiteLock-DNS");worker.start(); }
        catch(Exception e){Log.e("SiteLock","VPN setup failed",e);stopSelf();} return START_STICKY;
    }
    private void loop(){ byte[] buf=new byte[32767]; try(FileInputStream in=new FileInputStream(tun.getFileDescriptor()); FileOutputStream out=new FileOutputStream(tun.getFileDescriptor())){
        while(running){int n=in.read(buf); if(n<28)continue; byte[] packet=Arrays.copyOf(buf,n); int ihl=(packet[0]&15)*4; if((packet[0]>>4)!=4 || (packet[9]&255)!=17 || n<ihl+8)continue;
            int dst=ByteBuffer.wrap(packet,16,4).getInt(); if(dst!=ByteBuffer.wrap(new byte[]{10,111,(byte)222,2}).getInt())continue;
            int udp=ihl; int srcPort=((packet[udp]&255)<<8)|(packet[udp+1]&255); int dstPort=((packet[udp+2]&255)<<8)|(packet[udp+3]&255); int len=((packet[udp+4]&255)<<8)|(packet[udp+5]&255); if(dstPort!=53||len<8||udp+len>n)continue;
            byte[] dns=Arrays.copyOfRange(packet,udp+8,udp+len); String domain=parseQuestion(dns); byte[] answer;
            if(domain!=null && isBlocked(domain)) answer=blockedResponse(dns); else answer=forwardDns(dns);
            if(answer==null)continue; byte[] response=makeResponse(packet,ihl,srcPort,dstPort,answer); if(response!=null)out.write(response);
        }
    }catch(Exception e){if(running)Log.e("SiteLock","DNS loop ended",e);} }
    private boolean isBlocked(String d){for(String s:blocked)if(d.equals(s)||d.endsWith("."+s))return true;return false;}
    private String parseQuestion(byte[] d){try{if(d.length<17)return null;int p=12;StringBuilder s=new StringBuilder();while(p<d.length){int l=d[p++]&255;if(l==0)break;if(l>63||p+l>d.length)return null;if(s.length()>0)s.append('.');s.append(new String(d,p,l,java.nio.charset.StandardCharsets.US_ASCII));p+=l;}return s.toString().toLowerCase(java.util.Locale.ROOT);}catch(Exception e){return null;}}
    private byte[] blockedResponse(byte[] q){if(q.length<12)return null;byte[] a=Arrays.copyOf(q,12);a[2]=(byte)0x81;a[3]=(byte)0x83;a[6]=0;a[7]=0;a[8]=0;a[9]=0;a[10]=0;a[11]=0;return a;}
    private byte[] forwardDns(byte[] q){try(DatagramSocket s=new DatagramSocket()){protect(s);s.setSoTimeout(2500);byte[] ip={1,1,1,1};DatagramPacket req=new DatagramPacket(q,q.length,InetAddress.getByAddress(ip),53);s.send(req);byte[] buf=new byte[4096];DatagramPacket resp=new DatagramPacket(buf,buf.length);s.receive(resp);return Arrays.copyOf(resp.getData(),resp.getLength());}catch(SocketTimeoutException e){return null;}catch(Exception e){return null;}}
    private byte[] makeResponse(byte[] req,int ihl,int srcPort,int dstPort,byte[] dns){try{int udpLen=8+dns.length,total=ihl+udpLen;byte[] r=new byte[total];System.arraycopy(req,0,r,0,ihl);System.arraycopy(req,12,r,16,4);System.arraycopy(req,16,r,12,4);r[2]=(byte)(total>>8);r[3]=(byte)total;r[6]=0;r[7]=0;r[8]=64;r[10]=0;r[11]=0;short ipcs=checksum(r,0,ihl);r[10]=(byte)(ipcs>>8);r[11]=(byte)ipcs;int u=ihl;r[u]=(byte)(dstPort>>8);r[u+1]=(byte)dstPort;r[u+2]=(byte)(srcPort>>8);r[u+3]=(byte)srcPort;r[u+4]=(byte)(udpLen>>8);r[u+5]=(byte)udpLen;r[u+6]=0;r[u+7]=0;System.arraycopy(dns,0,r,u+8,dns.length);return r;}catch(Exception e){return null;}}
    private short checksum(byte[] b,int off,int len){long sum=0;for(int i=off;i<off+len;i+=2){sum+=((b[i]&255)<<8)|(i+1<off+len?(b[i+1]&255):0);while((sum>>>16)!=0)sum=(sum&0xffff)+(sum>>>16);}return (short)~sum;}
    @Override public void onDestroy(){running=false;try{if(tun!=null)tun.close();}catch(Exception ignored){}super.onDestroy();}
    @Override public void onRevoke(){running=false;stopSelf();super.onRevoke();}
}
