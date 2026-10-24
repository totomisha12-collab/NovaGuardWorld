package dev.hokagydev.novaguard.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.regex.Pattern;

public final class LocaleManager {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private static final Pattern HEX=Pattern.compile("&#([A-Fa-f0-9]{6})");
    public LocaleManager(JavaPlugin plugin){this.plugin=plugin;}
    public void reload(){
        String locale=plugin.getConfig().getString("settings.locale","ru").toLowerCase();
        if(!locale.equals("ru")&&!locale.equals("en"))locale="ru";
        File folder=new File(plugin.getDataFolder(),"lang");
        if(!folder.exists())folder.mkdirs();
        File file=new File(folder,"messages_"+locale+".yml");
        if(!file.exists())plugin.saveResource("lang/messages_"+locale+".yml",false);
        messages=YamlConfiguration.loadConfiguration(file);
    }
    public Component component(String key, String... replacements){
        String raw=messages==null?null:messages.getString(key);
        if(raw==null)raw="<gray>["+key+"]</gray>";
        for(int i=0;i+1<replacements.length;i+=2)raw=raw.replace("{"+replacements[i]+"}",replacements[i+1]);
        return parse(raw);
    }
    public Component parse(String raw){
        var matcher=HEX.matcher(raw); StringBuffer out=new StringBuffer();
        while(matcher.find())matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement("<#"+matcher.group(1)+">"));
        matcher.appendTail(out); raw=legacyToMini(out.toString());
        try{return MiniMessage.miniMessage().deserialize(raw);}
        catch(Exception ex){return LegacyComponentSerializer.legacyAmpersand().deserialize(ChatColor.translateAlternateColorCodes('&',raw));}
    }

    private String legacyToMini(String input){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)&([0-9a-fk-or])").matcher(input);
        StringBuffer out=new StringBuffer();
        java.util.Map<String,String> codes=java.util.Map.ofEntries(
            java.util.Map.entry("0","black"),java.util.Map.entry("1","dark_blue"),java.util.Map.entry("2","dark_green"),java.util.Map.entry("3","dark_aqua"),
            java.util.Map.entry("4","dark_red"),java.util.Map.entry("5","dark_purple"),java.util.Map.entry("6","gold"),java.util.Map.entry("7","gray"),
            java.util.Map.entry("8","dark_gray"),java.util.Map.entry("9","blue"),java.util.Map.entry("a","green"),java.util.Map.entry("b","aqua"),
            java.util.Map.entry("c","red"),java.util.Map.entry("d","light_purple"),java.util.Map.entry("e","yellow"),java.util.Map.entry("f","white"),
            java.util.Map.entry("k","obfuscated"),java.util.Map.entry("l","bold"),java.util.Map.entry("m","strikethrough"),java.util.Map.entry("n","underlined"),
            java.util.Map.entry("o","italic"),java.util.Map.entry("r","reset"));
        while(m.find()){String tag=codes.get(m.group(1).toLowerCase());m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement("<"+tag+">"));}
        m.appendTail(out);return out.toString();
    }
    public Component full(String key,String... replacements){
        return Component.empty().append(component("prefix")).append(component(key,replacements));
    }
}
