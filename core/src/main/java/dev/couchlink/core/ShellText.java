package dev.couchlink.core;

import java.util.*;

/** Literal text fallback for TV apps with custom keyboards and no InputConnection. */
public final class ShellText {
    private ShellText(){}
    public static String quote(String text){return "'"+text.replace("'","'\\''")+"'";}
    public static List<String> commands(String text){
        if(text.isEmpty()||text.length()>2000)throw new IllegalArgumentException("Enter between 1 and 2,000 characters");
        for(int i=0;i<text.length();i++)if(text.charAt(i)<32||text.charAt(i)>126)throw new IllegalArgumentException("This app uses its own keyboard. Use plain English here, or select an Android text field for Unicode input.");
        List<String> result=new ArrayList<>();String[] parts=text.split("%",-1);
        for(int i=0;i<parts.length;i++){
            if(!parts[i].isEmpty())result.add("input text "+quote(parts[i]));
            // Android's input command expands %s. Send percent separately to preserve literal text.
            if(i<parts.length-1)result.add("input text '%'");
        }
        return result;
    }
}
