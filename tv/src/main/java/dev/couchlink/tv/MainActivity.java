package dev.couchlink.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,address,code,fingerprint,detail;
    private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,1500);}};
    private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},10);
        startForegroundService(new Intent(this,RemoteService.class));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(32),dp(14),dp(32),dp(12));root.setBackgroundColor(Color.rgb(9,14,26));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        TextView brand=text("CouchLink",28,Color.WHITE);brand.setTypeface(null,Typeface.BOLD);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(text("Your phone. Your remote.",14,Color.rgb(154,172,201)));
        LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(12),0,dp(10));root.addView(row,new LinearLayout.LayoutParams(-1,dp(188)));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(dp(18),dp(10),dp(18),dp(10));GradientDrawable card=new GradientDrawable();card.setColor(Color.rgb(23,35,59));card.setCornerRadius(dp(18));left.setBackground(card);row.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        left.addView(text("PAIR YOUR ANDROID PHONE",11,Color.rgb(130,168,255)));
        code=text("••••••",38,Color.WHITE);code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);left.addView(code);
        left.addView(text("One-use code · valid for 5 minutes",10,Color.LTGRAY));
        fingerprint=text("Fingerprint: preparing…",14,Color.WHITE);fingerprint.setPadding(0,dp(6),0,dp(2));left.addView(fingerprint);
        address=text("Finding network address…",14,Color.WHITE);left.addView(address);
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(dp(28),dp(10),0,0);row.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        status=text("Starting…",18,Color.rgb(130,168,255));right.addView(status);
        detail=text("",11,Color.LTGRAY);detail.setMaxLines(3);detail.setPadding(0,dp(6),0,dp(6));right.addView(detail);
        right.addView(text("1   Open CouchLink on your phone\n2   Choose this TV, or enter its address\n3   Enter its code and fingerprint",14,Color.WHITE));
        LinearLayout actions=new LinearLayout(this);root.addView(actions);
        button(actions,"Authorize TV controls",()->{RemoteService s=RemoteService.current;if(s!=null){if(s.state.startsWith("Accept the debugging"))moveTaskToBack(true);else s.authorize();}});
        button(actions,"New pairing code",()->{RemoteService s=RemoteService.current;if(s!=null&&s.gate!=null)s.gate.newCode();update();});
        button(actions,"Restore TV keyboard",()->new Thread(()->{RemoteService s=RemoteService.current;if(s!=null)s.restoreKeyboard();}).start());
        button(actions,"Forget phones",()->new AlertDialog.Builder(this).setTitle("Disconnect all paired phones?").setMessage("Each phone will need to pair again.").setNegativeButton("Cancel",null).setPositiveButton("Forget",(d,w)->{RemoteService s=RemoteService.current;if(s!=null&&s.gate!=null){s.gate.revokeAll();s.syncRelayTokens();getSharedPreferences("remote",0).edit().remove("tokens").apply();s.gate.newCode();update();}}).show());
        TextView themeLabel=text("TV HOME",11,Color.rgb(130,168,255));themeLabel.setPadding(0,dp(14),0,dp(6));root.addView(themeLabel);
        LinearLayout themeActions=new LinearLayout(this);root.addView(themeActions);
        String[] themeIds={"classic","modern","cinematic","original"},themeNames={"Classic","Modern","Cinematic","Previous look"};
        for(int i=0;i<themeIds.length;i++){String id=themeIds[i];button(themeActions,themeNames[i],()->new Thread(()->{RemoteService s=RemoteService.current;try{if(s!=null&&s.themes!=null)s.themes.apply(id);}catch(Exception e){if(s!=null)s.error=RemoteService.friendly(e);}}).start());}
        TextView note=text("Keep USB/network debugging enabled for controls. Protected video and some HDMI screens cannot be previewed. Press Home to leave the remote running.",11,Color.rgb(154,172,201));note.setPadding(0,dp(8),0,0);root.addView(note);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }
    private void button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(Color.rgb(232,240,255));android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable focus=new GradientDrawable();focus.setColor(Color.rgb(130,168,255));focus.setCornerRadius(dp(10));GradientDrawable normal=new GradientDrawable();normal.setColor(Color.rgb(30,46,76));normal.setCornerRadius(dp(10));normal.setStroke(dp(1),Color.rgb(51,76,118));states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},normal);b.setBackground(states);b.setOnFocusChangeListener((v,focused)->b.setTextColor(focused?Color.rgb(9,14,26):Color.rgb(232,240,255)));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(0,0,dp(8),0);parent.addView(b,p);}
    private void set(TextView view,String value){if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
    private void update(){RemoteService s=RemoteService.current;if(s==null){set(status,"Remote is stopped");set(detail,"Reopen this app to start it.");return;}set(status,s.adbReady?"●  TV controls connected":s.state);set(detail,s.relayOnline?"Cloud relay ready · works over internet":(s.error.isEmpty()?"Connecting to cloud relay…":s.error));if(s.gate!=null)set(code,s.gate.displayCode());if(s.identity!=null)set(fingerprint,"Fingerprint: "+s.identity.fingerprint.substring(0,12).toUpperCase(java.util.Locale.US));if(s.port>0)set(address,s.addresses());}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
