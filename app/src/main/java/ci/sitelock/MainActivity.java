package ci.sitelock;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 100;
    private static final int BG = Color.rgb(16,29,43);
    private static final int PANEL = Color.rgb(27,44,58);
    private static final int GREEN = Color.rgb(128,229,167);
    private static final int MUTED = Color.rgb(166,187,200);
    private static final String[] LABELS = {"1 mois","3 mois","6 mois","1 an","2 ans","5 ans","10 ans"};
    private static final long[] DAYS = {30,90,180,365,730,1825,3650};
    private SharedPreferences prefs;
    private int selection = 3;
    private TextView status, remaining, shield;
    private Button activate, stop;
    private final Button[] durationButtons = new Button[LABELS.length];
    private ValueAnimator pulse;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("sitelock", MODE_PRIVATE);
        buildScreen();
        refresh();
    }

    private GradientDrawable background(int color, float radius, int borderColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (borderColor != Color.TRANSPARENT) d.setStroke(dp(1), borderColor);
        return d;
    }

    private int dp(float v) { return (int) (getResources().getDisplayMetrics().density * v + 0.5f); }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(content); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top); p.bottomMargin = dp(bottom);
        return p;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(30));
        scroll.addView(root);

        TextView title = text("◈  SiteLock CI", 26, Color.WHITE, true);
        root.addView(title);
        TextView subtitle = text("Ton espace, tes règles.", 14, MUTED, false);
        root.addView(subtitle, margins(5, 18));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(12), dp(22), dp(12), dp(22));
        hero.setBackground(background(PANEL, 24, Color.rgb(51,75,80)));
        root.addView(hero, margins(2, 22));

        shield = text("🔒", 70, GREEN, true);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(background(Color.rgb(31,80,62), 120, GREEN));
        LinearLayout.LayoutParams shieldParams = new LinearLayout.LayoutParams(dp(142), dp(142));
        shieldParams.gravity = Gravity.CENTER;
        hero.addView(shield, shieldParams);
        startPulse();

        status = text("Protection non activée", 20, Color.WHITE, true);
        status.setGravity(Gravity.CENTER);
        hero.addView(status, margins(18, 4));
        remaining = text("Choisis une durée pour commencer", 13, MUTED, false);
        remaining.setGravity(Gravity.CENTER);
        hero.addView(remaining);

        TextView heading = text("Durée de protection", 18, Color.WHITE, true);
        root.addView(heading, margins(0, 12));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        root.addView(grid);
        for (int i = 0; i < LABELS.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setAllCaps(false); b.setText(LABELS[i]); b.setTextSize(13);
            b.setMinHeight(0); b.setMinimumHeight(0);
            b.setPadding(dp(4), dp(13), dp(4), dp(13));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0; gp.height = -2;
            gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gp.setMargins(dp(3), dp(4), dp(3), dp(4));
            grid.addView(b, gp);
            durationButtons[i] = b;
            b.setOnClickListener(v -> { selection = index; updateDurations(); });
        }
        updateDurations();

        TextView categories = text("Catégories protégées", 18, Color.WHITE, true);
        root.addView(categories, margins(18, 10));
        addCategory(root, "⚑", "Paris sportifs", "Liste actuelle du moteur VPN");
        addCategory(root, "◇", "Contenus adultes", "Liste actuelle du moteur VPN");

        activate = new Button(this);
        activate.setAllCaps(false);
        activate.setText("Activer ma protection  →");
        activate.setTextSize(17); activate.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activate.setTextColor(Color.rgb(11,43,31));
        activate.setBackground(background(GREEN, 18, Color.TRANSPARENT));
        root.addView(activate, margins(24, 6));
        activate.setOnClickListener(v -> askActivation());

        stop = new Button(this);
        stop.setAllCaps(false);
        stop.setText("Arrêter après la période");
        stop.setTextColor(Color.WHITE);
        stop.setBackground(background(PANEL, 18, Color.rgb(58,80,95)));
        root.addView(stop, margins(6, 14));
        stop.setOnClickListener(v -> {
            if (isLocked()) {
                new AlertDialog.Builder(this).setTitle("Engagement en cours")
                    .setMessage("La période choisie n'est pas terminée. Attention : ce verrouillage dans l'application n'empêche pas sa désinstallation ou l'arrêt du VPN depuis Android.")
                    .setPositiveButton("Compris", null).show();
            } else {
                stopService(new Intent(this, DnsVpnService.class));
                Toast.makeText(this, "Arrêt demandé", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
        TextView disclaimer = text("Protection DNS expérimentale. Les navigateurs avec DNS chiffré indépendant, d'autres VPN et certains réglages Android peuvent contourner ce filtre. Les catégories affichées ne constituent pas une garantie de blocage complet.", 12, MUTED, false);
        root.addView(disclaimer, margins(5, 0));
        setContentView(scroll);
    }

    private void addCategory(LinearLayout root, String icon, String name, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setBackground(background(PANEL, 16, Color.rgb(50,71,84)));
        TextView label = text(icon + "  " + name + "   ✓", 16, Color.WHITE, true);
        TextView note = text(detail, 12, MUTED, false);
        box.addView(label); box.addView(note);
        root.addView(box, margins(0, 8));
    }

    private void updateDurations() {
        for (int i=0; i<durationButtons.length; i++) {
            boolean selected = i == selection;
            durationButtons[i].setTextColor(selected ? Color.rgb(10,45,30) : Color.WHITE);
            durationButtons[i].setBackground(background(selected ? GREEN : PANEL, 14, selected ? GREEN : Color.rgb(54,73,87)));
            durationButtons[i].setEnabled(!isLocked());
        }
    }

    private void startPulse() {
        pulse = ValueAnimator.ofFloat(1f, 1.065f, 1f);
        pulse.setDuration(2300);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.addUpdateListener(animation -> {
            float s = (Float) animation.getAnimatedValue();
            shield.setScaleX(s); shield.setScaleY(s);
        });
        pulse.start();
    }

    private boolean isLocked() { return System.currentTimeMillis() < prefs.getLong("until", 0L); }

    private void refresh() {
        boolean locked = isLocked();
        status.setText(locked ? "Engagement actif" : "Prêt à te protéger");
        if (locked) {
            long end = prefs.getLong("until", 0L);
            long daysLeft = Math.max(0, TimeUnit.MILLISECONDS.toDays(end - System.currentTimeMillis()));
            remaining.setText(daysLeft + " jour(s) restant(s) • Fin : " + DateFormat.getDateInstance().format(new Date(end)));
        } else remaining.setText("Choisis ta durée et confirme");
        activate.setEnabled(!locked);
        activate.setAlpha(locked ? 0.45f : 1f);
        updateDurations();
    }

    private void askActivation() {
        if (isLocked()) return;
        final EditText pin = new EditText(this);
        pin.setHint("Code de 4 chiffres minimum");
        pin.setInputType(2);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(22), dp(6), dp(22), 0);
        wrapper.addView(pin, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
            .setTitle("Activer pour " + LABELS[selection] + " ?")
            .setMessage("Le verrouillage sera enregistré dans l'application. Il ne résiste pas à une désinstallation, une suppression des données ou à la désactivation du VPN dans Android.")
            .setView(wrapper)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Confirmer", (dialog, which) -> {
                String code = pin.getText().toString().trim();
                if (code.length() < 4) { Toast.makeText(this, "Code trop court. Recommence.", Toast.LENGTH_LONG).show(); return; }
                long end = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DAYS[selection]);
                // Keep the existing preference keys for compatibility with the current VPN.
                prefs.edit().putLong("until", end).putString("pin", code).apply();
                Intent permission = VpnService.prepare(this);
                if (permission != null) startActivityForResult(permission, VPN_REQUEST);
                else startFilter();
                refresh();
            }).show();
    }

    private void startFilter() {
        if (isLocked()) {
            startService(new Intent(this, DnsVpnService.class));
            Toast.makeText(this, "Démarrage du VPN demandé", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) startFilter();
            else Toast.makeText(this, "Autorisation VPN refusée : filtrage inactif", Toast.LENGTH_LONG).show();
            refresh();
        }
    }

    @Override protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }
    }
