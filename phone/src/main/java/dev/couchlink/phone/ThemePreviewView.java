package dev.couchlink.phone;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class ThemePreviewView extends View {
    private final String theme;
    private final Paint paint=new Paint(3);
    ThemePreviewView(Context c,String theme){super(c);this.theme=theme;setContentDescription(theme+" TV home preview");}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight();int base=theme.equals("modern")?0xff111f3b:0xff050913;
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{theme.equals("cinematic")?0xff233976:base,base},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(0,0,w,h,20,20,paint);paint.setShader(null);
        paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));paint.setColor(0xfff2f6ff);paint.setTextSize(h*.18f);canvas.drawText("08:24",w*.07f,h*.28f,paint);
        paint.setTextSize(h*.038f);paint.setColor(0xff96acd3);canvas.drawText("YOUR EVENING STARTS HERE",w*.075f,h*.355f,paint);
        for(int i=0;i<3;i++){paint.setColor(0xff263857);canvas.drawRoundRect(w*(.64f+i*.10f),h*.16f,w*(.72f+i*.10f),h*.27f,12,12,paint);}
        if(theme.equals("cinematic")){paint.setColor(0xffe4eeff);paint.setTextSize(h*.115f);canvas.drawText("Movie night",w*.07f,h*.59f,paint);}
        float top=theme.equals("cinematic")?h*.69f:h*.58f;paint.setColor(0xff17243d);canvas.drawRoundRect(w*.055f,top-h*.025f,w*.945f,top+h*.23f,18,18,paint);
        int[] colors=theme.equals("modern")?new int[]{0xffda6b6b,0xff5681d4,0xff9d6ecd,0xff569caa}:new int[]{0xfff2f3f5,0xff1b1c28,0xff0f79b9,0xff383b95};String[] names={"YouTube","NETFLIX","prime","Hotstar"};
        for(int i=0;i<4;i++){float left=w*(.075f+i*.22f);paint.setColor(colors[i]);canvas.drawRoundRect(left,top,left+w*.19f,top+h*.18f,12,12,paint);paint.setColor(i==0&&!theme.equals("modern")?0xffcf2330:0xffeaf2ff);paint.setTextSize(h*.042f);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));canvas.drawText(names[i],left+w*.022f,top+h*.105f,paint);}
    }
}
