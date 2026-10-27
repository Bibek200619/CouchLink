package dev.couchlink.phone;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.nsd.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int BG=0xff090e1a,CARD=0xff17233b,MINT=0xff82a8ff,TEXT=0xfff1f5ff,MUTED=0xff9aacc9;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newFixedThreadPool(3),frames=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor controls=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final AtomicBoolean frameBusy=new AtomicBoolean();
    private final Map<String,NsdServiceInfo> devices=new LinkedHashMap<>();
    private final ArrayDeque<NsdServiceInfo> resolveQueue=new ArrayDeque<>();
    private final List<Button> repeatButtons=new ArrayList<>();
    private boolean resolving,resumed,connected;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    private SharedPreferences prefs;
    private volatile RemoteApi api;
    private LinearLayout root,body,nav;
    private TextView connection,status,previewStatus;
    private ImageView preview;
    private int page;
    private boolean touchpad;
    private final Runnable previewTick=new Runnable(){public void run(){if(!resumed||page!=1||api==null)return;fetchFrame();handler.postDelayed(this,900);}};

    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private android.content.res.ColorStateList colors(int normal,int pressed){return new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{pressed,normal});}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void ui(Runnable r){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())r.run();});}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackground(shape(CARD,16));b.setBackgroundTintList(colors(CARD,0xff304878));b.setMinHeight(dp(48));b.setPadding(dp(10),dp(6),dp(10),dp(6));b.setOnClickListener(v->action.run());return b;}
    private void addRow(LinearLayout parent,String[] labels,String[] keys){LinearLayout row=new LinearLayout(this);for(int i=0;i<labels.length;i++){String key=keys[i];Button b=button(labels[i],()->key(key));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(3),dp(5),dp(3),dp(5));row.addView(b,p);}parent.addView(row);}
    @Override public void onCreate(Bundle state){
        if(getIntent().getBooleanExtra("replica",false))setTheme(R.style.PairingTheme);
        super.onCreate(state);bindLocalNetwork();prefs=getSharedPreferences("phone",0);touchpad=prefs.getBoolean("touchpad",false);restoreApi();
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(12),dp(16),dp(8));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("CouchLink",27,TEXT);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button device=button("TVs",this::devicesDialog);device.setContentDescription("Connect or change TV");header.addView(device,new LinearLayout.LayoutParams(dp(64),dp(44)));root.addView(header);
        connection=text("Finding your TV…",13,MUTED);connection.setPadding(dp(2),dp(4),0,dp(6));root.addView(connection);
        status=text("",12,MINT);status.setPadding(dp(2),0,0,dp(4));root.addView(status);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));nav=new LinearLayout(this);root.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);
        render();if(getIntent().getBooleanExtra("replica",false))root.setVisibility(View.INVISIBLE);discover();if(getIntent().hasExtra("qrAddress"))handler.postDelayed(()->pairDialog(getIntent().getStringExtra("qrAddress")),500);else if(getIntent().getBooleanExtra("replica",false))handler.postDelayed(this::devicesDialog,500);else if(api!=null)checkConnection();else handler.postDelayed(this::devicesDialog,500);
    }
    private void restoreApi(){prefs.edit().remove("relaySecret").apply();String token=prefs.getString("token","");if(!token.isEmpty())api=new RemoteApi(prefs.getString("host",""),prefs.getInt("port",8787),prefs.getString("pin",""),token,prefs.getString("id",""));}
    private void bindLocalNetwork(){ConnectivityManager manager=getSystemService(ConnectivityManager.class);for(Network network:manager.getAllNetworks()){NetworkCapabilities caps=manager.getNetworkCapabilities(network);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){manager.bindProcessToNetwork(network);return;}}}
    private void render(){
        for(Button b:repeatButtons)b.setPressed(false);repeatButtons.clear();
        handler.removeCallbacks(previewTick);body.removeAllViews();nav.removeAllViews();preview=null;
        String[] tabs={"Remote","Screen","Apps","Themes"};for(int i=0;i<tabs.length;i++){final int selected=i;Button b=button(tabs[i],()->{page=selected;render();});b.setTextColor(page==i?BG:MUTED);b.setBackgroundTintList(colors(page==i?MINT:BG,0xffaac4ff));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(6),dp(3),0);nav.addView(b,p);}
        connection.setText(api==null?"Pair with your TV to begin":(connected?"●  ":"○  ")+prefs.getString("name","Your TV")+" · "+api.host);
        if(api==null){welcome();return;}if(page==0)remote();else if(page==1)screen();else if(page==2)apps();else themes();
    }
    private void welcome(){
        LinearLayout c=column();c.setGravity(Gravity.CENTER);body.addView(c,new LinearLayout.LayoutParams(-1,-1));
        TextView mark=text("TV + phone",32,MINT);mark.setGravity(Gravity.CENTER);c.addView(mark);
        TextView subtitle=text("A remote that is always nearby.",21,TEXT);subtitle.setGravity(Gravity.CENTER);subtitle.setPadding(0,dp(24),0,dp(12));c.addView(subtitle);
        TextView description=text("Open CouchLink TV on your television.\nKeep both devices on the same Wi-Fi,\nthen enter its pairing code and fingerprint.",15,MUTED);description.setGravity(Gravity.CENTER);c.addView(description);
        Button connect=button("Find my TV",this::devicesDialog);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.setMargins(dp(24),dp(28),dp(24),0);c.addView(connect,p);
    }
    private LinearLayout scroller(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout content=column();content.setPadding(0,dp(6),0,dp(12));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return content;}
    private void remote(){
        LinearLayout content=scroller();
        LinearLayout actions=new LinearLayout(this);
        Button power=button("Power",()->new AlertDialog.Builder(this).setTitle("TV power").setItems(new String[]{"Wake TV","Put TV to sleep"},(d,w)->key(w==0?"wake":"sleep")).show());
        Button inputs=button("Inputs",()->key("inputs")),settings=button("Settings",()->key("settings"));
        for(Button b:new Button[]{power,inputs,settings}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),dp(3),dp(3),dp(8));actions.addView(b,p);}content.addView(actions);
        LinearLayout modes=new LinearLayout(this);modes.setGravity(Gravity.CENTER);
        for(int i=0;i<2;i++){boolean selected=i==1;Button mode=button(i==0?"D-pad":"Touchpad",()->{touchpad=selected;prefs.edit().putBoolean("touchpad",touchpad).apply();render();});boolean active=touchpad==selected;mode.setTextColor(active?MINT:MUTED);mode.setBackgroundTintList(colors(active?0xff203459:BG,0xff304878));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);p.setMargins(dp(3),0,dp(3),dp(12));modes.addView(mode,p);}content.addView(modes);
        int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(52),dp(270));
        if(touchpad){TouchPadView pad=new TouchPadView(this,this::key);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,width);p.setMargins(dp(3),dp(3),dp(3),dp(12));content.addView(pad,p);}
        else{
            FrameLayout pad=new FrameLayout(this);GradientDrawable ring=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff243756,0xff111a2c});ring.setCornerRadius(width/2f);ring.setStroke(dp(1),0xff334c76);pad.setBackground(ring);pad.setElevation(dp(3));
            LinearLayout.LayoutParams padParams=new LinearLayout.LayoutParams(width,width);padParams.gravity=Gravity.CENTER;padParams.setMargins(0,dp(2),0,dp(12));content.addView(pad,padParams);
            int size=width/3;padButton(pad,"↑","up",size,Gravity.TOP|Gravity.CENTER_HORIZONTAL);padButton(pad,"↓","down",size,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);padButton(pad,"‹","left",size,Gravity.LEFT|Gravity.CENTER_VERTICAL);padButton(pad,"›","right",size,Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            Button ok=button("OK",()->key("ok"));ok.setTextSize(21);ok.setTextColor(BG);ok.setTypeface(null,Typeface.BOLD);ok.setBackground(shape(MINT,60));ok.setBackgroundTintList(colors(MINT,0xffb2cbff));ok.setElevation(dp(6));pad.addView(ok,new FrameLayout.LayoutParams(size-dp(4),size-dp(4),Gravity.CENTER));
        }
        addRow(content,new String[]{"Back","Home","Menu"},new String[]{"back","home","menu"});
        addRow(content,new String[]{"Vol −","Mute","Vol +","Play / pause"},new String[]{"volume_down","mute","volume_up","play"});
        Button type=button("Keyboard",this::keyboard);type.setTextColor(BG);type.setTypeface(null,Typeface.BOLD);type.setBackgroundTintList(colors(MINT,0xffb2cbff));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));p.setMargins(dp(3),dp(7),dp(3),0);content.addView(type,p);
        TextView note=text("Wake is available while the TV remains connected to Wi-Fi.",11,MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(10),0,0);content.addView(note);
    }
    private void padButton(FrameLayout pad,String label,String action,int size,int gravity){
        Button b=button(label,()->key(action));b.setTextSize(32);b.setContentDescription("TV "+action);b.setBackgroundTintList(colors(0xff1b2b49,0xff4263a0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(size-dp(8),size-dp(8),gravity);p.setMargins(dp(4),dp(4),dp(4),dp(4));pad.addView(b,p);
        repeatButtons.add(b);
        final boolean[] fromTouch={false};b.setOnClickListener(v->{if(!fromTouch[0])key(action);fromTouch[0]=false;});
        Runnable repeat=new Runnable(){public void run(){if(resumed&&page==0&&b.isAttachedToWindow()&&b.isPressed()){key(action);handler.postDelayed(this,180);}else b.setPressed(false);}};
        b.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){b.setPressed(true);v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);key(action);handler.postDelayed(repeat,400);return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){b.setPressed(false);handler.removeCallbacks(repeat);if(event.getActionMasked()==MotionEvent.ACTION_UP){fromTouch[0]=true;b.performClick();}return true;}return true;});
    }
    private void screen(){
        LinearLayout c=scroller();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Live TV",23,TEXT);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));Button refresh=button("Refresh",this::fetchFrame);heading.addView(refresh,new LinearLayout.LayoutParams(dp(82),dp(42)));c.addView(heading);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(shape(0xff03060c,20));preview.setClipToOutline(true);preview.setAlpha(1f);preview.setImageAlpha(255);preview.setContentDescription("Live TV screen. Tap to interact.");
        int width=getResources().getDisplayMetrics().widthPixels-dp(32);LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,width*9/16);imageParams.setMargins(0,dp(10),0,0);c.addView(preview,imageParams);
        previewStatus=text("Waiting for a picture from your TV…",12,MUTED);previewStatus.setPadding(dp(4),dp(12),dp(4),dp(14));c.addView(previewStatus);
        preview.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_UP&&preview.getDrawable()!=null){double x=e.getX()/v.getWidth(),y=e.getY()/v.getHeight();submit(()->api.call("/v1/tap",new JSONObject().put("x",Math.max(0,Math.min(1,x))).put("y",Math.max(0,Math.min(1,y)))));v.performClick();}return true;});
        addRow(c,new String[]{"←","↑","OK","↓","→"},new String[]{"left","up","ok","down","right"});addRow(c,new String[]{"Back","Home","Vol −","Vol +"},new String[]{"back","home","volume_down","volume_up"});
        Button keyboard=button("Keyboard",this::keyboard);keyboard.setTextColor(BG);keyboard.setBackgroundTintList(colors(MINT,0xffb2cbff));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));p.setMargins(dp(3),dp(7),dp(3),dp(8));c.addView(keyboard,p);
        TextView note=text("Live snapshots · about 1 fps · no audio\nProtected videos and some HDMI inputs may be blank.",12,MUTED);note.setPadding(dp(4),dp(10),dp(4),0);c.addView(note);
        if(resumed)handler.post(previewTick);
    }
    private void fetchFrame(){
        RemoteApi target=api;if(target==null||!frameBusy.compareAndSet(false,true))return;
        frames.execute(()->{try{RemoteApi.Reply result=target.call("/v1/frame",null);Bitmap bitmap=result.code==204?null:BitmapFactory.decodeByteArray(result.bytes,0,result.bytes.length);
            ui(()->{if(!resumed||page!=1||api!=target||preview==null){if(bitmap!=null)bitmap.recycle();return;}preview.setAlpha(1f);preview.setImageAlpha(255);preview.setImageBitmap(bitmap);String label=bitmap==null?"Preview unavailable for this screen. Controls still work.":"●  Live picture · tap the screen to interact";if(!android.text.TextUtils.equals(previewStatus.getText(),label))previewStatus.setText(label);});
        }catch(Exception e){ui(()->{if(api==target&&page==1&&previewStatus!=null){previewStatus.setText("Preview paused · "+friendly(e));if(preview!=null)preview.setAlpha(.35f);}});}finally{frameBusy.set(false);}});
    }
    private void themes(){
        LinearLayout c=scroller();TextView title=text("Set the scene",23,TEXT);title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(4),dp(10),0,dp(6));c.addView(title);
        TextView subtitle=text("A fresh look for your TV, in one tap.",13,MUTED);subtitle.setPadding(dp(4),0,0,dp(18));c.addView(subtitle);
        String[] ids={"classic","modern","cinematic"},names={"Classic","Modern","Cinematic"},descriptions={"Quiet, dark and familiar.","Colourful icons. A softer atmosphere.","Big artwork, dark glass and a movie-night feel."};
        for(int i=0;i<ids.length;i++){
            String id=ids[i],name=names[i];LinearLayout card=column();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(shape(CARD,22));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(dp(3),0,dp(3),dp(14));c.addView(card,cp);
            ThemePreviewView preview=new ThemePreviewView(this,id);card.addView(preview,new LinearLayout.LayoutParams(-1,dp(142)));
            TextView label=text(name,20,TEXT);label.setTypeface(null,Typeface.BOLD);label.setPadding(dp(3),dp(12),0,dp(4));card.addView(label);TextView desc=text(descriptions[i],12,MUTED);desc.setPadding(dp(3),0,0,dp(12));card.addView(desc);
            Button apply=button("Apply on TV",()->applyTheme(id,name));apply.setTextColor(BG);apply.setBackgroundTintList(colors(MINT,0xffb2cbff));card.addView(apply,new LinearLayout.LayoutParams(-1,dp(46)));
        }
        c.addView(button("Restore previous TV look",()->applyTheme("original","Previous look")),new LinearLayout.LayoutParams(-1,dp(48)));
        TextView note=text("Your four dock apps stay in the same order. Preview artwork is illustrative.",11,MUTED);note.setPadding(dp(4),dp(12),dp(4),dp(8));c.addView(note);
    }
    private void applyTheme(String id,String name){
        RemoteApi target=api;status.setText("Applying "+name+"…");submit(()->{target.call("/v1/theme",new JSONObject().put("theme",id));prefs.edit().putString("theme",id).apply();ui(()->toast(name+" is now on your TV"));});
    }
    private void apps(){
        LinearLayout c=scroller();TextView title=text("Your apps, one tap away",21,TEXT);title.setPadding(dp(4),dp(12),0,dp(16));c.addView(title);TextView loading=text("Loading TV apps…",14,MUTED);c.addView(loading);RemoteApi target=api;
        network.execute(()->{try{JSONArray list=target.get("/v1/apps").getJSONArray("apps");ui(()->{if(page!=2||api!=target)return;c.removeView(loading);LinearLayout row=null;for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item==null)continue;if(i%2==0){row=new LinearLayout(this);c.addView(row);}String pkg=item.optString("package"),label=item.optString("label",pkg);LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(10),dp(16),dp(10),dp(12));tile.setBackground(shape(CARD,18));tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription("Open "+label);tile.setOnClickListener(v->submit(()->{target.call("/v1/launch",new JSONObject().put("package",pkg));ui(()->{page=0;render();});}));
                    ImageView icon=new ImageView(this);icon.setImageResource(android.R.drawable.sym_def_app_icon);tile.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));TextView name=text(label,14,TEXT);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setPadding(0,dp(10),0,0);tile.addView(name);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(132),1);p.setMargins(dp(4),0,dp(4),dp(8));row.addView(tile,p);
                    network.execute(()->{try{byte[] bytes=target.call("/v1/icon?package="+URLEncoder.encode(pkg,"UTF-8"),null).bytes;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);ui(()->{if(page==2&&api==target)icon.setImageBitmap(bitmap);else if(bitmap!=null)bitmap.recycle();});}catch(Exception ignored){}});
                }if(row!=null&&list.length()%2!=0)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));});
            }catch(Exception e){ui(()->loading.setText(friendly(e)));}});
    }
    private interface Job{void run()throws Exception;}
    private void submit(Job action){
        RemoteApi target=api;if(target==null){devicesDialog();return;}
        try{controls.execute(()->{if(api!=target)return;try{action.run();ui(()->{if(api!=target)return;connected=true;status.setText("");connection.setText("●  "+prefs.getString("name","Your TV")+" · "+target.host);});}catch(Exception e){ui(()->{if(api==target)status.setText(friendly(e));});}});}catch(RejectedExecutionException e){status.setText("Waiting for the TV…");}
    }
    private void key(String action){RemoteApi target=api;if(target==null){devicesDialog();return;}submit(()->target.key(action));}
    private void keyboard(){
        EditText edit=new EditText(this);edit.setTextColor(TEXT);edit.setHintTextColor(MUTED);edit.setHint("Select a text field on the TV, then type here");edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);edit.setMinLines(2);edit.setPadding(dp(20),dp(14),dp(20),dp(14));
        new AlertDialog.Builder(this).setTitle("TV keyboard").setMessage("Open a search box on the TV, then type below. YouTube supports English text through its own keyboard.").setView(edit).setNegativeButton("Cancel",null).setNeutralButton("Delete",(d,w)->key("delete")).setPositiveButton("Send",(d,w)->{String value=edit.getText().toString();if(!value.isEmpty())submit(()->api.call("/v1/text",new JSONObject().put("text",value)));}).show();
    }
    private void checkConnection(){RemoteApi target=api;network.execute(()->{try{JSONObject s=target.get("/v1/status");ui(()->{if(api==null)return;connected=true;connection.setText("●  "+prefs.getString("name","Your TV")+" · "+target.host);status.setText(s.optBoolean("adbReady")?"":s.optString("error","Authorize controls in CouchLink TV"));});}catch(Exception e){ui(()->{if(api!=target)return;connected=false;connection.setText("○  Reconnecting to "+prefs.getString("name","your TV"));status.setText(friendly(e));});}});}
    private void devicesDialog(){
        if(isFinishing()||isDestroyed())return;
        List<NsdServiceInfo> found=new ArrayList<>(devices.values());List<String> names=new ArrayList<>();for(NsdServiceInfo s:found)names.add(s.getServiceName()+" · "+s.getHost().getHostAddress());names.add("Enter TV address manually");if(api!=null)names.add("Forget this connection");
        new AlertDialog.Builder(this).setTitle("Connect to your TV").setItems(names.toArray(new String[0]),(d,index)->{if(index<found.size()){NsdServiceInfo s=found.get(index);pairDialog(s.getHost().getHostAddress()+":"+s.getPort());}else if(index==found.size())pairDialog(prefs.getString("host","")+(prefs.contains("host")?":"+prefs.getInt("port",8787):""));else{RemoteApi old=api;network.execute(()->{try{old.call("/v1/unpair",new JSONObject());}catch(Exception ignored){}});prefs.edit().clear().apply();api=null;connected=false;status.setText("");if(getIntent().getBooleanExtra("replica",false))finish();else render();}}).setNegativeButton("Close",(d,w)->{if(getIntent().getBooleanExtra("replica",false))finish();}).setOnCancelListener(d->{if(getIntent().getBooleanExtra("replica",false))finish();}).show();
    }
    private EditText field(LinearLayout parent,String hint,int input){EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);e.setInputType(input);e.setSingleLine(true);parent.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));return e;}
    private void pairDialog(String selected){
        LinearLayout form=column();form.setPadding(dp(22),0,dp(22),0);EditText address=field(form,"TV address, e.g. 192.168.1.81:8787",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);address.setText(selected);
        EditText code=field(form,"6-digit code on TV",InputType.TYPE_CLASS_NUMBER);EditText fingerprint=field(form,"12-character fingerprint on TV",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if(getIntent().hasExtra("qrAddress")){code.setText(getIntent().getStringExtra("qrCode"));fingerprint.setText(getIntent().getStringExtra("qrPin"));}
        TextView info=text("The fingerprint verifies that you are pairing with your TV. The code can be used once.",12,MUTED);info.setPadding(0,dp(12),0,dp(6));form.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pair with CouchLink TV").setView(form).setNegativeButton("Cancel",(d,w)->{if(getIntent().getBooleanExtra("replica",false))finish();}).setPositiveButton("Pair",null).setOnCancelListener(d->{if(getIntent().getBooleanExtra("replica",false))finish();}).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pin=fingerprint.getText().toString().replace(" ","").replace(":","").trim().toLowerCase(Locale.US);String number=code.getText().toString().trim();
            if(!pin.matches("[0-9a-f]{12}")||!number.matches("[0-9]{6}")){info.setText("Enter all 6 digits and all 12 fingerprint characters shown on the TV.");return;}
            URI uri;try{uri=new URI("https://"+address.getText().toString().trim());if(uri.getHost()==null||uri.getUserInfo()!=null||(uri.getPath()!=null&&!uri.getPath().isEmpty())||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()>65535||uri.getPort()==0)throw new Exception();}catch(Exception e){info.setText("Enter the TV's IP address and port, such as 192.168.1.81:8787");return;}
            String host=uri.getHost();int port=uri.getPort()<0?8787:uri.getPort();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);info.setText("Verifying your TV…");
            network.execute(()->{try{RemoteApi.Reply result=new RemoteApi(host,port,pin,"").call("/v1/pair",new JSONObject().put("code",number));JSONObject paired=result.json();String fullPin=paired.getString("fingerprint");if(!result.certificate.equals(fullPin))throw new Exception("TV identity did not match");String token=paired.getString("token");
                prefs.edit().putString("host",host).putInt("port",port).putString("pin",fullPin).putString("token",token).putString("id",paired.getString("id")).putString("name",paired.getString("name")).remove("relaySecret").apply();api=new RemoteApi(host,port,fullPin,token,paired.getString("id"));
                ui(()->{dialog.dismiss();connected=true;page=0;status.setText("");if(getIntent().getBooleanExtra("replica",false)){finish();return;}render();checkConnection();});
            }catch(Exception e){ui(()->{info.setText(friendly(e));dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);});}});
        }));dialog.show();
    }
    private void discover(){
        nsd=getSystemService(NsdManager.class);discovery=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){}
            public void onServiceFound(NsdServiceInfo service){ui(()->{resolveQueue.add(service);resolveNext();});}
            public void onServiceLost(NsdServiceInfo service){ui(()->devices.remove(service.getServiceName()));}
            public void onDiscoveryStopped(String type){}
            public void onStartDiscoveryFailed(String type,int error){ui(()->{if(api==null)connection.setText("Use TVs to enter the TV address manually");});}
            public void onStopDiscoveryFailed(String type,int error){}
        };try{nsd.discoverServices("_couchlink._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);}catch(Exception ignored){}
    }
    @SuppressWarnings("deprecation") private void resolveNext(){
        if(resolving||resolveQueue.isEmpty()||isDestroyed())return;resolving=true;NsdServiceInfo next=resolveQueue.remove();
        nsd.resolveService(next,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo service,int error){ui(()->{resolving=false;resolveNext();});}
            public void onServiceResolved(NsdServiceInfo service){ui(()->{resolving=false;if(service.getHost()!=null){devices.put(service.getServiceName(),service);if(api==null)connection.setText("Found "+devices.size()+" TV · tap TVs to pair");byte[] id=service.getAttributes().get("id");if(api!=null&&id!=null&&new String(id,java.nio.charset.StandardCharsets.UTF_8).equals(prefs.getString("id",""))){String host=service.getHost().getHostAddress();if(!host.equals(api.host)||service.getPort()!=api.port){prefs.edit().putString("host",host).putInt("port",service.getPort()).apply();restoreApi();checkConnection();}}}resolveNext();});}
        });
    }
    private String friendly(Exception e){if(e instanceof java.net.SocketTimeoutException||e instanceof java.net.ConnectException)return "TV is offline. Keep both devices on the same Wi-Fi and open CouchLink TV.";if(e instanceof javax.net.ssl.SSLException)return "TV certificate could not be verified. Check the fingerprint or pair again.";return e.getMessage()==null?"Could not reach the TV. Try again.":e.getMessage();}
    @Override protected void onResume(){super.onResume();bindLocalNetwork();resumed=true;if(api!=null)checkConnection();if(page==1&&api!=null)handler.post(previewTick);}
    @Override protected void onPause(){resumed=false;for(Button b:repeatButtons)b.setPressed(false);handler.removeCallbacks(previewTick);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);try{if(nsd!=null&&discovery!=null)nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}network.shutdownNow();controls.shutdownNow();frames.shutdownNow();super.onDestroy();}
}
