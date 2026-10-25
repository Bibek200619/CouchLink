package dev.couchlink.ui;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

public final class MediaImport {
    public interface Transport{JSONObject call(String path,JSONObject body)throws Exception;}
    public static String send(Context context,Uri uri,String type,Transport api,Consumer<String> progress)throws Exception{
        File temp=File.createTempFile("couchlink-import-",".tmp",context.getCacheDir());
        try{
            if(type.equals("image")){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}if(options.outWidth<1||options.outHeight<1)throw new IOException("Choose a supported photo");options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1600);options.inJustDecodeBounds=false;Bitmap bitmap;try(InputStream in=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new IOException("Could not open that photo");try(OutputStream out=new FileOutputStream(temp)){bitmap.compress(Bitmap.CompressFormat.JPEG,88,out);}bitmap.recycle();
            }else try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>32*1024*1024)throw new IOException("Choose a video smaller than 32 MB");out.write(buffer,0,n);}}
            JSONObject begin=api.call("/v1/studio/import/begin",new JSONObject().put("type",type).put("size",temp.length()).put("title",type.equals("image")?"Your photo":"Your video"));String id=begin.getString("id");
            try(InputStream in=new FileInputStream(temp)){byte[] buffer=new byte[8192];int n,offset=0,last=0;while((n=in.read(buffer))!=-1){api.call("/v1/studio/import/chunk",new JSONObject().put("id",id).put("offset",offset).put("data",Base64.encodeToString(Arrays.copyOf(buffer,n),Base64.NO_WRAP)));offset+=n;int percent=(int)(100L*offset/temp.length());if(percent>=last+10){progress.accept("Importing · "+percent+"%");last=percent;}}}
            return api.call("/v1/studio/import/finish",new JSONObject().put("id",id)).getString("media");
        }finally{temp.delete();}
    }
}
