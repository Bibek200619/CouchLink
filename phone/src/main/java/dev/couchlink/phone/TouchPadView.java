package dev.couchlink.phone;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import java.util.function.Consumer;

final class TouchPadView extends View {
    private final Paint paint=new Paint(3);
    private final Consumer<String> key;
    private float x,y,originX,originY;
    private long down,last;
    private boolean touching,moved;
    TouchPadView(Context context,Consumer<String> key){super(context);this.key=key;setFocusable(true);setContentDescription("TV touchpad. Swipe to navigate, tap for OK, hold for menu.");}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xff1d2b48,0xff121b30},null,Shader.TileMode.CLAMP));c.drawRoundRect(0,0,w,h,dp(26),dp(26),paint);paint.setShader(null);
        paint.setColor(0xff2b4168);paint.setStrokeWidth(dp(1));for(float a=dp(22);a<w;a+=dp(22))for(float b=dp(22);b<h;b+=dp(22))c.drawCircle(a,b,dp(1),paint);
        paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xffc0d4ff);paint.setTextSize(dp(18));paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText("Swipe to navigate",w/2,h/2-dp(3),paint);paint.setTextSize(dp(12));paint.setColor(0xff8ca1c5);c.drawText("Tap for OK  ·  Hold for menu",w/2,h/2+dp(22),paint);
        if(touching){paint.setShader(new RadialGradient(x,y,dp(54),0x6682a8ff,0x0082a8ff,Shader.TileMode.CLAMP));c.drawCircle(x,y,dp(54),paint);paint.setShader(null);paint.setColor(0xffa1c0ff);c.drawCircle(x,y,dp(5),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:touching=true;moved=false;originX=x=e.getX();originY=y=e.getY();down=SystemClock.uptimeMillis();last=0;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:float dx=e.getX()-x,dy=e.getY()-y;if(Math.hypot(e.getX()-originX,e.getY()-originY)>dp(12))moved=true;
                if(Math.max(Math.abs(dx),Math.abs(dy))>dp(30)&&SystemClock.uptimeMillis()-last>110){key.accept(Math.abs(dx)>Math.abs(dy)?dx>0?"right":"left":dy>0?"down":"up");performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);x=e.getX();y=e.getY();last=SystemClock.uptimeMillis();}invalidate();return true;
            case MotionEvent.ACTION_UP:touching=false;if(!moved){if(SystemClock.uptimeMillis()-down>=550)key.accept("menu");else performClick();}getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
            case MotionEvent.ACTION_CANCEL:touching=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();key.accept("ok");performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);return true;}
}
