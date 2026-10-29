package dev.couchlink.tv;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import java.util.concurrent.*;

public final class PhoneIme extends InputMethodService {
    private static volatile PhoneIme current;
    @Override public void onCreate(){super.onCreate();current=this;}
    @Override public void onDestroy(){if(current==this)current=null;super.onDestroy();}
    @Override public View onCreateInputView(){
        TextView v=new TextView(this);v.setText("CouchLink keyboard  ·  Type on your phone");v.setTextColor(Color.rgb(168,240,198));v.setTextSize(18);v.setPadding(32,20,32,20);v.setBackgroundColor(Color.rgb(20,32,33));return v;
    }
    static boolean commit(String text)throws Exception{
        CompletableFuture<Boolean> result=new CompletableFuture<>();
        new Handler(Looper.getMainLooper()).post(()->{PhoneIme ime=current;InputConnection input=ime==null?null:ime.getCurrentInputConnection();result.complete(input!=null&&input.commitText(text,1));});
        return result.get(2,TimeUnit.SECONDS);
    }
}
