package ci.sitelock;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.VpnService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status, timer;
    private Button start, stop;
    private final String[] durations = {"1 mois", "3 mois", "6 mois", "1 an", "2 ans", "5 ans", "10 ans"};
    private final long[] millis = {30L,90L,180L,365L,730L,1825L,3650L};
    @Override public void onCreate(Bundle b) { super.onCreate(b); prefs=getSharedPreferences("sitelock", MODE_PRIVATE); buildUi(); updateUi(); }
    private void buildUi() {
        ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,32,28,24); root.setBackgroundColor(Color.rgb(245,248,247)); sc.addView(root);
        TextView logo=new TextView(this); logo.setText("🔒 SiteLock CI"); logo.setTextSize(28); logo.setTextColor(Color.rgb(10,80,61)); logo.setGravity(Gravity.CENTER); logo.setPadding(0,8,0,8); root.addView(logo);
        TextView desc=new TextView(this); desc.setText("Filtrage DNS local pour réduire l’accès aux sites de paris et aux contenus adultes. Une protection utile, sans promesse magique."); desc.setTextSize(15); desc.setTextColor(Color.DKGRAY); desc.setGravity(Gravity.CENTER); desc.setPadding(0,4,0,22); root.addView(desc);
        status=new TextView(this); status.setTextSize(18); status.setTextColor(Color.rgb(10,80,61)); status.setGravity(Gravity.CENTER); root.addView(status);
        timer=new TextView(this); timer.setTextSize(14); timer.setGravity(Gravity.CENTER); timer.setPadding(0,10,0,18); root.addView(timer);
        TextView label=new TextView(this); label.setText("Choisis la durée de ton engagement"); label.setTextSize(16); label.setTextColor(Color.BLACK); root.addView(label);
        Spinner sp=new Spinner(this); ArrayAdapter<String> ad=new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,durations); sp.setAdapter(ad); root.addView(sp);
        EditText pin=new EditText(this); pin.setHint("Code secret (4 chiffres minimum)"); pin.setInputType(2); root.addView(pin);
        start=new Button(this); start.setText("ACTIVER LE VERROUILLAGE"); root.addView(start);
        stop=new Button(this); stop.setText("Arrêter le filtrage"); root.addView(stop);
        TextView note=new TextView(this); note.setText("À savoir : Android demandera l’autorisation VPN. Le filtrage DNS peut être contourné par certains VPN, DNS privés ou navigateurs configurés spécialement. Pour empêcher réellement la désinstallation, il faut configurer le téléphone en appareil géré. Ne choisis qu’une durée que tu souhaites vraiment respecter."); note.setTextSize(13); note.setTextColor(Color.DKGRAY); note.setPadding(0,22,0,0); root.addView(note);
        setContentView(sc);
        start.setOnClickListener(v->{
            long until=prefs.getLong("until",0); if(System.currentTimeMillis()<until){ toast("Le verrouillage est déjà actif."); return; }
            String code=pin.getText().toString().trim(); if(code.length()<4){pin.setError("Choisis au moins 4 chiffres");return;}
            new AlertDialog.Builder(this).setTitle("Confirmer la durée").setMessage("Le verrouillage restera actif pendant "+durations[sp.getSelectedItemPosition()]+". Cette application ne peut pas empêcher à elle seule sa désinstallation.").setNegativeButton("Annuler",null).setPositiveButton("Confirmer",(d,w)->{
                long end=System.currentTimeMillis()+TimeUnit.DAYS.toMillis(millis[sp.getSelectedItemPosition()]); prefs.edit().putLong("until",end).putString("pin",code).apply();
                Intent i=VpnService.prepare(this); if(i!=null) startActivityForResult(i,100); else startFilter(); updateUi();
            }).show();
        });
        stop.setOnClickListener(v->{ long until=prefs.getLong("until",0); if(System.currentTimeMillis()<until){new AlertDialog.Builder(this).setTitle("Verrouillage actif").setMessage("Le délai choisi n’est pas terminé. Pour une protection fiable, utilise aussi les contrôles parentaux ou un appareil géré.").setPositiveButton("Compris",null).show();} else {stopService(new Intent(this,DnsVpnService.class)); updateUi();}});
    }
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data); if(req==100 && res==RESULT_OK) startFilter(); else toast("Autorisation VPN non accordée. Le filtrage n’est pas actif."); updateUi();}
    private void startFilter(){ if(System.currentTimeMillis()<prefs.getLong("until",0)){startService(new Intent(this,DnsVpnService.class)); toast("Filtrage DNS démarré.");} updateUi(); }
    private void updateUi(){ boolean locked=System.currentTimeMillis()<prefs.getLong("until",0); status.setText(locked?"🔐 Engagement actif":"Protection non verrouillée"); long left=Math.max(0,prefs.getLong("until",0)-System.currentTimeMillis()); timer.setText(locked?"Temps restant : "+TimeUnit.MILLISECONDS.toDays(left)+" jour(s)":"Choisis une durée puis active le filtrage."); stop.setEnabled(true); start.setEnabled(!locked); }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
