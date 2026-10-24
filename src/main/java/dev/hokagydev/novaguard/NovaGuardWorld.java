package dev.hokagydev.novaguard;

import net.kyori.adventure.text.Component;
import dev.hokagydev.novaguard.model.Claim;
import dev.hokagydev.novaguard.storage.ClaimStore;
import dev.hokagydev.novaguard.locale.LocaleManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NovaGuardWorld extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private ClaimStore store;
    private LocaleManager locale;
    private final Map<UUID,Location> pos1=new ConcurrentHashMap<>(),pos2=new ConcurrentHashMap<>();
    private final Map<UUID,String> lastClaim=new ConcurrentHashMap<>();
    private final Set<UUID> viewing=ConcurrentHashMap.newKeySet();
    private static final Set<String> FLAGS=Set.of("build","pvp","interact","chest-access","mob-damage","explosions","tnt","fire","entry");

    @Override public void onEnable(){
        saveDefaultConfig();
        for(String f:List.of("lang/messages_ru.yml","lang/messages_en.yml"))if(!new java.io.File(getDataFolder(),f).exists())saveResource(f,false);
        locale=new LocaleManager(this);locale.reload();
        try{store=new ClaimStore(this);store.open();}catch(Exception e){getLogger().severe("SQLite: "+e.getMessage());Bukkit.getPluginManager().disablePlugin(this);return;}
        PluginCommand cmd=Objects.requireNonNull(getCommand("novaguard"));cmd.setExecutor(this);cmd.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        getLogger().info("NovaGuardWORLD enabled.");
    }
    @Override public void onDisable(){if(store!=null)store.close();}
    private void send(CommandSender s,String key,String... repl){s.sendMessage(locale.full(key,repl));}
    private boolean player(CommandSender s){if(s instanceof Player)return true;send(s,"player-only");return false;}
    private boolean admin(CommandSender s){if(s.hasPermission("novaguard.admin"))return true;send(s,"no-permission");return false;}
    private boolean manager(Player p,Claim c){return c!=null&&(c.owner.equals(p.getUniqueId())||p.hasPermission("novaguard.admin"));}
    private Claim current(Player p){return store.at(p.getLocation());}
    private boolean member(Player p,Claim c){return c!=null&&c.trusted(p.getUniqueId());}
    private boolean flag(Claim c,String key,boolean def){return c==null||Boolean.parseBoolean(c.flags.getOrDefault(key,Boolean.toString(def)));}
    private boolean can(Player p,Location l,String f){if(p.hasPermission("novaguard.bypass"))return true;Claim c=store.at(l);return c==null||member(p,c)||flag(c,f,false);}
    private void deny(Player p){send(p,"protected");}
    private String joined(String[] a,int start){return String.join(" ",Arrays.copyOfRange(a,start,a.length));}

    @Override public boolean onCommand(CommandSender s,Command cmd,String label,String[] a){
        if(a.length==0||a[0].equalsIgnoreCase("help")){send(s,"help");return true;}
        String sub=a[0].toLowerCase(Locale.ROOT);
        if(sub.equals("reload")){if(!admin(s))return true;reloadConfig();locale.reload();send(s,"reloaded");return true;}
        if(!player(s))return true;Player p=(Player)s;
        switch(sub){
            case "wand"->{if(!p.hasPermission("novaguard.claim")){send(p,"no-permission");break;}Material m=Material.matchMaterial(getConfig().getString("settings.wand-material","GOLDEN_AXE"));if(m==null)m=Material.GOLDEN_AXE;p.getInventory().addItem(new ItemStack(m));send(p,"wand");}
            case "pos1","pos2"->{Location l=p.getLocation().getBlock().getLocation();(sub.equals("pos1")?pos1:pos2).put(p.getUniqueId(),l);send(p,"point-set","point",sub.equals("pos1")?"1":"2");}
            case "claim","create"->create(p,a);
            case "auto"->{int r=getConfig().getInt("settings.default-auto-claim-radius",8);if(a.length>1)try{r=Integer.parseInt(a[1]);}catch(NumberFormatException ex){send(p,"invalid-radius");break;}if(r<1||r>64){send(p,"invalid-radius");break;}Location l=p.getLocation().getBlock().getLocation();pos1.put(p.getUniqueId(),l.clone().add(-r,-3,-r));pos2.put(p.getUniqueId(),l.clone().add(r,3,r));send(p,"auto-ready");}
            case "list"->{List<Claim> cs=store.byOwner(p.getUniqueId());send(p,"list-header","count",Integer.toString(cs.size()));for(Claim c:cs)send(p,"list-entry","name",c.name,"world",c.world);}
            case "info"->{Claim c=a.length>1?store.get(a[1],p.getWorld().getName()):current(p);if(c==null){send(p,"no-claim");break;}send(p,"info","name",c.name,"owner",Optional.ofNullable(Bukkit.getOfflinePlayer(c.owner).getName()).orElse(c.owner.toString()),"world",c.world,"members",Integer.toString(c.members.size()),"priority",Integer.toString(c.priority));}
            case "delete"->{if(a.length<2){send(p,"usage-delete");break;}Claim c=store.get(a[1],p.getWorld().getName());if(c==null){send(p,"not-found");break;}if(!manager(p,c)){send(p,"not-owner");break;}store.delete(c.id);send(p,"deleted","name",c.name);}
            case "trust","untrust"->{if(a.length<2){send(p,"usage-player");break;}Claim c=current(p);if(!manager(p,c)){send(p,"not-owner");break;}UUID id=Bukkit.getOfflinePlayer(a[1]).getUniqueId();if(id.equals(c.owner)){send(p,"owner-trust");break;}if(sub.equals("trust"))c.members.add(id);else c.members.remove(id);store.save(c);send(p,sub.equals("trust")?"trusted":"untrusted","player",a[1]);}
            case "flag"->{if(a.length<4){send(p,"usage-flag");break;}Claim c=store.get(a[1],p.getWorld().getName());if(!manager(p,c)){send(p,"not-owner");break;}String f=a[2].toLowerCase(Locale.ROOT),v=a[3].toLowerCase(Locale.ROOT);if(!FLAGS.contains(f)||!Set.of("allow","deny").contains(v)){send(p,"invalid-flag");break;}c.flags.put(f,Boolean.toString(v.equals("allow")));store.save(c);send(p,"flag-set","flag",f,"value",v);}
            case "flags"->{send(p,"flags-list","flags",String.join(", ",FLAGS));}
            case "priority"->{if(a.length<2){send(p,"usage-priority");break;}Claim c=current(p);if(!manager(p,c)){send(p,"not-owner");break;}try{c.priority=Integer.parseInt(a[1]);}catch(NumberFormatException ex){send(p,"invalid-number");break;}store.save(c);send(p,"priority-set","value",Integer.toString(c.priority));}
            case "greeting","farewell"->{Claim c=current(p);if(!manager(p,c)){send(p,"not-owner");break;}String v=a.length>1?joined(a,1):"";if(sub.equals("greeting"))c.greeting=v;else c.farewell=v;store.save(c);send(p,"message-set");}
            case "view"->toggleView(p);
            default->send(p,"unknown-command");
        }return true;
    }
    private void create(Player p,String[] a){
        if(!p.hasPermission("novaguard.claim")){send(p,"no-permission");return;}if(a.length<2){send(p,"usage-claim");return;}
        Location one=pos1.get(p.getUniqueId()),two=pos2.get(p.getUniqueId());if(one==null||two==null){send(p,"need-selection");return;}
        if(one.getWorld()==null||two.getWorld()==null||!one.getWorld().equals(two.getWorld())){send(p,"different-worlds");return;}
        int minX=Math.min(one.getBlockX(),two.getBlockX()),maxX=Math.max(one.getBlockX(),two.getBlockX()),minY=Math.min(one.getBlockY(),two.getBlockY()),maxY=Math.max(one.getBlockY(),two.getBlockY()),minZ=Math.min(one.getBlockZ(),two.getBlockZ()),maxZ=Math.max(one.getBlockZ(),two.getBlockZ());
        Claim c=new Claim(UUID.randomUUID().toString(),a[1].toLowerCase(Locale.ROOT),p.getUniqueId(),one.getWorld().getName(),minX,minY,minZ,maxX,maxY,maxZ);
        if(!c.name.matches("[a-z0-9_-]{1,32}")){send(p,"invalid-name");return;}
        if(c.volume()>getConfig().getLong("settings.max-claim-area",100000)){send(p,"too-large","size",Long.toString(c.volume()));return;}
        if(store.byOwner(p.getUniqueId()).size()>=getConfig().getInt("settings.max-claims-per-player",5)&&!p.hasPermission("novaguard.admin")){send(p,"claim-limit");return;}
        if(store.get(c.name,c.world)!=null){send(p,"name-exists");return;}
        boolean intersects=store.overlaps(c,null);
        store.save(c);
        if(intersects)send(p,"overlap-warning");
        send(p,"claim-created","name",c.name);
    }
    private void toggleView(Player p){
        if(viewing.remove(p.getUniqueId())){send(p,"view-off");return;}viewing.add(p.getUniqueId());send(p,"view-on");
        new BukkitRunnable(){int ticks=0;public void run(){if(!p.isOnline()||!viewing.contains(p.getUniqueId())||ticks++>=getConfig().getInt("settings.particle-view-seconds",10)*2){viewing.remove(p.getUniqueId());cancel();return;}Claim c=current(p);if(c==null)return;World w=p.getWorld();int step=Math.max(1,getConfig().getInt("settings.particle-view-spacing",2));for(int x=c.minX;x<=c.maxX;x+=step)for(int z:new int[]{c.minZ,c.maxZ})for(int y:new int[]{c.minY,c.maxY})w.spawnParticle(Particle.HAPPY_VILLAGER,x+.5,y+.5,z+.5,1,0,0,0,0);for(int z=c.minZ;z<=c.maxZ;z+=step)for(int x:new int[]{c.minX,c.maxX})for(int y:new int[]{c.minY,c.maxY})w.spawnParticle(Particle.HAPPY_VILLAGER,x+.5,y+.5,z+.5,1,0,0,0,0);}}.runTaskTimer(this,0,10);
    }

    @EventHandler public void selection(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||e.getClickedBlock()==null)return;Material wand=Material.matchMaterial(getConfig().getString("settings.wand-material","GOLDEN_AXE"));if(wand==null||e.getItem()==null||e.getItem().getType()!=wand||!e.getPlayer().hasPermission("novaguard.claim"))return;if(e.getAction()==Action.LEFT_CLICK_BLOCK){pos1.put(e.getPlayer().getUniqueId(),e.getClickedBlock().getLocation());send(e.getPlayer(),"point-set","point","1");e.setCancelled(true);}else if(e.getAction()==Action.RIGHT_CLICK_BLOCK){pos2.put(e.getPlayer().getUniqueId(),e.getClickedBlock().getLocation());send(e.getPlayer(),"point-set","point","2");e.setCancelled(true);}}
    @EventHandler public void breakBlock(BlockBreakEvent e){if(!can(e.getPlayer(),e.getBlock().getLocation(),"build")){e.setCancelled(true);deny(e.getPlayer());}}
    @EventHandler public void place(BlockPlaceEvent e){if(!can(e.getPlayer(),e.getBlock().getLocation(),"build")){e.setCancelled(true);deny(e.getPlayer());}}
    @EventHandler public void interact(PlayerInteractEvent e){if(e.getClickedBlock()==null)return;Claim c=store.at(e.getClickedBlock().getLocation());if(c==null||member(e.getPlayer(),c)||e.getPlayer().hasPermission("novaguard.bypass"))return;Material m=e.getClickedBlock().getType();String n=m.name();boolean container=getConfig().getBoolean("settings.protect-containers",true)&&m.isInteractable()&&(n.contains("CHEST")||n.contains("BARREL")||n.contains("FURNACE")||n.contains("HOPPER")||n.contains("SHULKER")||n.contains("DISPENSER")||n.contains("DROPPER"));String f=container?"chest-access":"interact";if(!flag(c,f,false)){e.setCancelled(true);deny(e.getPlayer());}}
    @EventHandler public void bucketEmpty(PlayerBucketEmptyEvent e){protectBucket(e.getPlayer(),e.getBlock(),e);}
    @EventHandler public void bucketFill(PlayerBucketFillEvent e){protectBucket(e.getPlayer(),e.getBlock(),e);}
    private void protectBucket(Player p,Block b,Cancellable e){if(!can(p,b.getLocation(),"interact")||!can(p,b.getRelative(0,1,0).getLocation(),"interact")){e.setCancelled(true);deny(p);}}
    @EventHandler public void pvp(EntityDamageByEntityEvent e){
        if(e.getEntity() instanceof Player victim){
            Claim c=store.at(victim.getLocation());
            EntityType source=e.getDamager().getType();
            if(c!=null&&!flag(c,"explosions",false)&&Set.of(EntityType.CREEPER,EntityType.TNT,EntityType.TNT_MINECART,EntityType.FIREBALL,EntityType.SMALL_FIREBALL,EntityType.WITHER_SKULL).contains(source)){e.setCancelled(true);return;}
            Player attacker=null;
            if(e.getDamager() instanceof Player p)attacker=p;
            else if(e.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player p)attacker=p;
            if(attacker!=null&&!attacker.hasPermission("novaguard.bypass")&&c!=null&&!member(attacker,c)&&!flag(c,"pvp",true))e.setCancelled(true);
            else if(attacker==null&&e.getDamager() instanceof LivingEntity&&c!=null&&!flag(c,"mob-damage",true))e.setCancelled(true);
        } else if(e.getEntity() instanceof Animals||e.getEntity() instanceof Villager){
            if(e.getDamager() instanceof Player p&&!can(p,e.getEntity().getLocation(),"mob-damage"))e.setCancelled(true);
        }
    }
    @EventHandler public void entityInteract(PlayerInteractEntityEvent e){if(!getConfig().getBoolean("settings.protect-entity-interactions",true))return;if(!can(e.getPlayer(),e.getRightClicked().getLocation(),"interact")){e.setCancelled(true);deny(e.getPlayer());}}
    @EventHandler public void explode(EntityExplodeEvent e){if(!getConfig().getBoolean("settings.deny-explosions-in-claims",true))return;boolean tnt=e.getEntityType()==EntityType.TNT||e.getEntityType()==EntityType.TNT_MINECART;e.blockList().removeIf(b->{Claim c=store.at(b.getLocation());return c!=null&&!flag(c,tnt?"tnt":"explosions",false);});}
    @EventHandler public void blockExplode(BlockExplodeEvent e){if(!getConfig().getBoolean("settings.deny-explosions-in-claims",true))return;e.blockList().removeIf(b->{Claim c=store.at(b.getLocation());return c!=null&&!flag(c,"explosions",false);});}
    @EventHandler public void burn(BlockBurnEvent e){Claim c=store.at(e.getBlock().getLocation());if(c!=null&&!flag(c,"fire",false)&&getConfig().getBoolean("settings.deny-fire-spread-in-claims",true))e.setCancelled(true);}
    @EventHandler public void ignite(BlockIgniteEvent e){Claim c=store.at(e.getBlock().getLocation());if(c!=null&&!flag(c,"fire",false)&&getConfig().getBoolean("settings.deny-fire-spread-in-claims",true))e.setCancelled(true);}
    @EventHandler public void pistonExtend(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->!sameAllowed(b,b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler public void pistonRetract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->!sameAllowed(b,b.getRelative(e.getDirection().getOppositeFace()))))e.setCancelled(true);}
    private boolean sameAllowed(Block from,Block to){Claim a=store.at(from.getLocation()),b=store.at(to.getLocation());return a==b||a==null&&b==null;}
    @EventHandler public void fluid(BlockFromToEvent e){Claim from=store.at(e.getBlock().getLocation()),to=store.at(e.getToBlock().getLocation());if(from!=to&&((from!=null&&!flag(from,"interact",false))||(to!=null&&!flag(to,"interact",false))))e.setCancelled(true);}
    @EventHandler public void spread(BlockSpreadEvent e){Claim c=store.at(e.getBlock().getLocation());if(c!=null&&!flag(c,"fire",false)&&e.getSource().getType()==Material.FIRE)e.setCancelled(true);}
    @EventHandler public void hanging(HangingBreakByEntityEvent e){if(e.getRemover() instanceof Player p&&!can(p,e.getEntity().getLocation(),"build"))e.setCancelled(true);}
    @EventHandler public void vehicle(VehicleDestroyEvent e){if(e.getAttacker() instanceof Player p&&!can(p,e.getVehicle().getLocation(),"build"))e.setCancelled(true);}
    @EventHandler public void dispenser(BlockDispenseEvent e){
        Claim c=store.at(e.getBlock().getLocation());
        if(c!=null&&!flag(c,"interact",false))e.setCancelled(true);
    }
    @EventHandler public void entityBlockChange(EntityChangeBlockEvent e){
        Claim c=store.at(e.getBlock().getLocation());
        if(c!=null&&!flag(c,"build",false))e.setCancelled(true);
    }
    @EventHandler public void hopperTransfer(org.bukkit.event.inventory.InventoryMoveItemEvent e){
        org.bukkit.Location source=inventoryLocation(e.getSource()),dest=inventoryLocation(e.getDestination());
        Claim a=source==null?null:store.at(source),b=dest==null?null:store.at(dest);
        if((a!=null&&!flag(a,"chest-access",false))||(b!=null&&!flag(b,"chest-access",false)))e.setCancelled(true);
    }
    private org.bukkit.Location inventoryLocation(org.bukkit.inventory.Inventory inv){
        org.bukkit.inventory.InventoryHolder holder=inv.getHolder();
        if(holder instanceof org.bukkit.block.BlockState state)return state.getLocation();
        return null;
    }
    @EventHandler public void move(PlayerMoveEvent e){if(e.getTo()==null||e.getFrom().getBlockX()==e.getTo().getBlockX()&&e.getFrom().getBlockY()==e.getTo().getBlockY()&&e.getFrom().getBlockZ()==e.getTo().getBlockZ())return;Claim old=store.at(e.getFrom()),now=store.at(e.getTo());if(old==now)return;UUID id=e.getPlayer().getUniqueId();if(now!=null&&!member(e.getPlayer(),now)&&!flag(now,"entry",true)&&!e.getPlayer().hasPermission("novaguard.bypass")){e.setTo(e.getFrom());return;}if(old!=null&&!old.farewell.isBlank())e.getPlayer().sendMessage(locale.parse(old.farewell));if(now!=null&&!now.greeting.isBlank())e.getPlayer().sendMessage(locale.parse(now.greeting));}
    @EventHandler public void quit(PlayerQuitEvent e){viewing.remove(e.getPlayer().getUniqueId());pos1.remove(e.getPlayer().getUniqueId());pos2.remove(e.getPlayer().getUniqueId());}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a){List<String> opts=s.hasPermission("novaguard.admin")?List.of("help","wand","pos1","pos2","claim","create","auto","trust","untrust","info","list","delete","flag","flags","priority","greeting","farewell","view","reload"):List.of("help","wand","pos1","pos2","claim","create","auto","trust","untrust","info","list","delete","flag","flags","priority","greeting","farewell","view");if(a.length==1)return opts.stream().filter(x->x.startsWith(a[0].toLowerCase())).toList();if(a.length==3&&a[0].equalsIgnoreCase("flag"))return FLAGS.stream().filter(x->x.startsWith(a[2].toLowerCase())).toList();if(a.length==4&&a[0].equalsIgnoreCase("flag"))return List.of("allow","deny");return Collections.emptyList();}
}
