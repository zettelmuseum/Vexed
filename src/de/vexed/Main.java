package de.vexed;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class Main extends Activity implements View.OnClickListener {

    // <G> ---- Spiel-Engine: Brett 10x8, 0=leer, 1-8=Steine, 9=Wand
    static class G {
        int[] b = new int[80];
        int moves;
        ArrayList<int[]> fr = new ArrayList<int[]>(), u = new ArrayList<int[]>();

        G(String s) {
            Arrays.fill(b, 9);
            int x = 0, y = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '/') { y++; x = 0; }
                else if (c == '~') b[y * 10 + x++] = 0;
                else if (c >= 'a' && c <= 'h') b[y * 10 + x++] = c - 'a' + 1;
                else if (c >= '0' && c <= '9') {
                    int k = c - '0';
                    while (i + 1 < s.length() && Character.isDigit(s.charAt(i + 1))) k = k * 10 + s.charAt(++i) - '0';
                    x += k;
                }
            }
        }

        void snap() { fr.add(b.clone()); }

        // Nachbar links/rechts (s=-1/+1); ausserhalb des Bretts = Wand
        int at(int p, int s) { int c = p % 10 + s; return c < 0 || c > 9 ? 9 : b[p + s]; }

        boolean won() { for (int v : b) if (v > 0 && v < 9) return false; return true; }

        // Stein (x,y) um d Felder verschieben (stoppt bei Fall/Treffer), dann Schwerkraft + Treffer
        boolean move(int x, int y, int d) {
            if (x < 0 || x > 9 || y < 0 || y > 7 || b[y * 10 + x] < 1 || b[y * 10 + x] > 8) return false;
            int p = y * 10 + x, s = d < 0 ? -1 : 1, k = Math.abs(d), q = moves;
            int[] v = Arrays.copyOf(b, 82);
            v[80] = moves;
            while (k-- > 0 && at(p, s) == 0) {
                b[p + s] = b[p]; b[p] = 0; p += s; moves++; snap();
                if (b[p + 10] == 0 || b[p] == b[p + 10] || b[p] == at(p, -1) || b[p] == at(p, 1)) break;
            }
            if (moves == q) return false;
            u.add(v);
            if (u.size() > 15) u.remove(0);
            for (;;) {
                boolean f = false;
                for (int i = 69; i >= 10; i--)
                    if (b[i] > 0 && b[i] < 9 && b[i + 10] == 0) { b[i + 10] = b[i]; b[i] = 0; f = true; }
                if (f) { snap(); continue; }
                boolean[] m = new boolean[80];
                for (int i = 10; i < 70; i++)
                    if (b[i] > 0 && b[i] < 9) {
                        if (b[i] == at(i, 1)) m[i] = m[i + 1] = f = true;
                        if (b[i] == b[i + 10]) m[i] = m[i + 10] = f = true;
                    }
                if (!f) return true;
                for (int i = 0; i < 80; i++) if (m[i]) b[i] = 0;
                snap();
            }
        }

        void undo() {
            if (u.isEmpty()) return;
            int[] v = u.remove(u.size() - 1);
            System.arraycopy(v, 0, b, 0, 80);
            moves = v[80];
        }

        // Loesung: Paare "xy", Kleinbuchstabe x = rechts, Grossbuchstabe = links
        void sol(String s) {
            for (int i = 0; i + 1 < s.length(); i += 2) {
                char c = s.charAt(i);
                move((c | 32) - 'a', (s.charAt(i + 1) | 32) - 'a', c < 'a' ? -1 : 1);
            }
        }
    }

    // Level-Pack lesen: je Level {board, solution, title}
    static ArrayList<String[]> parse(InputStream in) throws IOException {
        ArrayList<String[]> L = new ArrayList<String[]>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
        String l, t[] = null;
        int i;
        while ((l = r.readLine()) != null) {
            if ((i = l.indexOf("board=")) >= 0) L.add(t = new String[] { l.substring(i + 6).trim(), "", "" });
            else if (t == null) continue;
            else if ((i = l.indexOf("solution=")) >= 0) t[1] = l.substring(i + 9).trim();
            else if ((i = l.indexOf("title=")) >= 0) t[2] = l.substring(i + 6).trim();
            else if (t[1].length() == 0 && l.trim().matches("[0-9a-h~/]+")) t[0] += l.trim();
        }
        return L;
    }
    // </G>

    String[] packs;
    ArrayList<String[]> L;
    int pk = -1, lv, bs;
    G g;
    Bitmap[] S = new Bitmap[7];
    TextView info;
    View v;
    SharedPreferences sp;
    Handler h = new Handler();
    ArrayList<int[]> q;
    int qi;
    int[] shown;

    class Board extends View {
        Paint p = new Paint();
        Rect r = new Rect();
        RectF f = new RectF();
        float cs, ox, oy, tx, ty;

        Board(Context c) { super(c); p.setFilterBitmap(true); }

        protected void onDraw(Canvas c) {
            c.drawColor(0xFFD4D0C8);
            cs = Math.min(getWidth() / 10f, getHeight() / 8f);
            ox = (getWidth() - cs * 10) / 2;
            oy = (getHeight() - cs * 8) / 2;
            try {
                if (S[bs] == null) S[bs] = BitmapFactory.decodeStream(getAssets().open("s" + bs + ".png"));
            } catch (Exception e) { return; }
            int[] a = shown != null ? shown : g.b;
            for (int i = 0; i < 80; i++) {
                float x = ox + i % 10 * cs, y = oy + i / 10 * cs;
                r.set(a[i] * 48, 0, a[i] * 48 + 48, 48);
                f.set(x, y, x + cs, y + cs);
                c.drawBitmap(S[bs], r, f, p);
            }
        }

        // Wischen/Ziehen = Stein verschieben; Antippen = eine Zelle zur angetippten Seite
        public boolean onTouchEvent(MotionEvent e) {
            if (q != null) return true;
            if (e.getAction() == MotionEvent.ACTION_DOWN) { tx = e.getX(); ty = e.getY(); }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float d = (e.getX() - tx) / cs;
                int n = Math.round(d);
                if (n == 0) n = Math.abs(d) > .25f ? (d > 0 ? 1 : -1) : ((tx - ox) % cs < cs / 2 ? -1 : 1);
                g.fr.clear();
                g.snap();
                if (g.move((int) ((tx - ox) / cs), (int) ((ty - oy) / cs), n)) play(g.fr, true);
            }
            return true;
        }
    }

    protected void onCreate(Bundle s) {
        super.onCreate(s);
        sp = getPreferences(0);
        try {
            ArrayList<String> a = new ArrayList<String>();
            for (String n : getAssets().list("")) if (n.endsWith(".ini")) a.add(n);
            Collections.sort(a);
            packs = a.toArray(new String[0]);
        } catch (Exception e) { throw new RuntimeException(e); }
        LinearLayout root = new LinearLayout(this), row = null;
        root.setOrientation(LinearLayout.VERTICAL);
        info = new TextView(this);
        info.setPadding(12, 8, 12, 8);
        root.addView(info);
        root.addView(v = new Board(this), new LinearLayout.LayoutParams(-1, 0, 1));
        String[] t = { "<", ">", "Neu", "Undo", "L\u00f6sung", "Level", "Pack", "Set" };
        for (int i = 0; i < t.length; i++) {
            if (i % 4 == 0) root.addView(row = new LinearLayout(this));
            Button x = new Button(this);
            x.setText(t[i]);
            x.setId(i);
            x.setOnClickListener(this);
            row.addView(x, new LinearLayout.LayoutParams(0, -2, 1));
        }
        setContentView(root);
        bs = sp.getInt("b", 6);
        load(Math.min(sp.getInt("p", Math.max(0, Arrays.asList(packs).indexOf("Classic Levels.ini"))), packs.length - 1), sp.getInt("l", 0));
        upd();
    }

    void load(int p, int l) {
        if (p != pk) {
            pk = p;
            try { L = parse(getAssets().open(packs[p])); } catch (Exception e) { throw new RuntimeException(e); }
        }
        lv = Math.max(0, Math.min(l, L.size() - 1));
        g = new G(L.get(lv)[0]);
        sp.edit().putInt("p", pk).putInt("l", lv).putInt("b", bs).commit();
    }

    void upd() {
        String[] e = L.get(lv);
        info.setText(packs[pk].replace(".ini", "") + "  " + (lv + 1) + "/" + L.size() + "\n" + e[2]
                + "   Par " + e[1].length() / 2 + "   Z\u00fcge " + g.moves);
        v.invalidate();
    }

    // Bildfolge abspielen (70 ms pro Schritt); danach ggf. Sieg pruefen
    void play(ArrayList<int[]> f, final boolean chk) {
        q = f;
        qi = 0;
        h.post(new Runnable() {
            public void run() {
                if (qi < q.size()) { shown = q.get(qi++); v.invalidate(); h.postDelayed(this, 70); return; }
                q = null;
                shown = null;
                upd();
                if (chk && g.won()) {
                    int par = L.get(lv)[1].length() / 2;
                    Toast.makeText(Main.this, g.moves <= par ? "Par oder besser!" : "Geschafft! Par " + par + ", du " + g.moves, Toast.LENGTH_SHORT).show();
                    if (lv < L.size() - 1) { load(pk, lv + 1); upd(); }
                    else Toast.makeText(Main.this, "Pack beendet!", Toast.LENGTH_LONG).show();
                }
            }
        });
        upd();
    }

    void pick(String[] n, final boolean pack) {
        new AlertDialog.Builder(this).setItems(n, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int i) { if (pack) load(i, 0); else load(pk, i); upd(); }
        }).show();
    }

    public void onClick(View w) {
        h.removeCallbacksAndMessages(null);
        q = null;
        shown = null;
        switch (w.getId()) {
            case 0: load(pk, lv - 1); break;
            case 1: load(pk, lv + 1); break;
            case 2: load(pk, lv); break;
            case 3: g.undo(); break;
            case 4: {
                G s = new G(L.get(lv)[0]);
                s.snap();
                s.sol(L.get(lv)[1]);
                play(s.fr, false);
                return;
            }
            case 5: {
                String[] n = new String[L.size()];
                for (int i = 0; i < n.length; i++) n[i] = (i + 1) + "  " + L.get(i)[2];
                pick(n, false);
                return;
            }
            case 6: {
                String[] n = new String[packs.length];
                for (int i = 0; i < n.length; i++) n[i] = packs[i].replace(".ini", "");
                pick(n, true);
                return;
            }
            case 7: bs = (bs + 1) % 7; sp.edit().putInt("b", bs).commit(); break;
        }
        upd();
    }
}
