package dev.couchlink.tv;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&context.getSharedPreferences("remote",0).getBoolean("enabled",false))context.startForegroundService(new Intent(context,RemoteService.class));
    }
}
