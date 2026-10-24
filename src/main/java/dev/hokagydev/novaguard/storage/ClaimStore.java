package dev.hokagydev.novaguard.storage;

import org.bukkit.Location;
import dev.hokagydev.novaguard.model.Claim;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.*;
import java.util.*;

public final class ClaimStore {
    private final JavaPlugin plugin;
    private Connection connection;
    private final Map<String,Claim> cache = new LinkedHashMap<>();
    public ClaimStore(JavaPlugin plugin) { this.plugin=plugin; }

    public synchronized void open() throws SQLException {
        File f=new File(plugin.getDataFolder(),plugin.getConfig().getString("database.file","claims.db"));
        plugin.getDataFolder().mkdirs();
        connection=DriverManager.getConnection("jdbc:sqlite:"+f.getAbsolutePath());
        try(Statement s=connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=5000");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS claims (id TEXT PRIMARY KEY, name TEXT NOT NULL, owner TEXT NOT NULL, world TEXT NOT NULL, min_x INTEGER, min_y INTEGER, min_z INTEGER, max_x INTEGER, max_y INTEGER, max_z INTEGER, priority INTEGER NOT NULL DEFAULT 0, greeting TEXT NOT NULL DEFAULT '', farewell TEXT NOT NULL DEFAULT '', UNIQUE(name, world))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS members (claim_id TEXT NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(claim_id,uuid), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS flags (claim_id TEXT NOT NULL, flag TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(claim_id,flag), FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
        }
        ensureColumn("priority","INTEGER NOT NULL DEFAULT 0"); ensureColumn("greeting","TEXT NOT NULL DEFAULT ''"); ensureColumn("farewell","TEXT NOT NULL DEFAULT ''");
        loadCache();
    }
    private void ensureColumn(String name,String type) throws SQLException {
        boolean found=false;
        try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info(claims)")) {
            while(r.next()) if(name.equalsIgnoreCase(r.getString("name"))) found=true;
        }
        if(!found) try(Statement s=connection.createStatement()){s.executeUpdate("ALTER TABLE claims ADD COLUMN "+name+" "+type);}
    }
    private void loadCache() throws SQLException {
        cache.clear();
        try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT * FROM claims")) {
            while(r.next()) {
                Claim c=new Claim(r.getString("id"),r.getString("name"),UUID.fromString(r.getString("owner")),r.getString("world"),r.getInt("min_x"),r.getInt("min_y"),r.getInt("min_z"),r.getInt("max_x"),r.getInt("max_y"),r.getInt("max_z"));
                c.priority=r.getInt("priority"); c.greeting=r.getString("greeting"); c.farewell=r.getString("farewell");
                cache.put(c.id,c);
            }
        }
        for(Claim c:cache.values()) {
            try(PreparedStatement p=connection.prepareStatement("SELECT uuid FROM members WHERE claim_id=?")) {
                p.setString(1,c.id); try(ResultSet r=p.executeQuery()){while(r.next())c.members.add(UUID.fromString(r.getString(1)));}
            }
            try(PreparedStatement p=connection.prepareStatement("SELECT flag,value FROM flags WHERE claim_id=?")) {
                p.setString(1,c.id); try(ResultSet r=p.executeQuery()){while(r.next())c.flags.put(r.getString(1),r.getString(2));}
            }
        }
    }
    public synchronized void save(Claim c) {
        try {
            connection.setAutoCommit(false);
            try(PreparedStatement p=connection.prepareStatement("INSERT INTO claims(id,name,owner,world,min_x,min_y,min_z,max_x,max_y,max_z,priority,greeting,farewell) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,owner=excluded.owner,world=excluded.world,min_x=excluded.min_x,min_y=excluded.min_y,min_z=excluded.min_z,max_x=excluded.max_x,max_y=excluded.max_y,max_z=excluded.max_z,priority=excluded.priority,greeting=excluded.greeting,farewell=excluded.farewell")) {
                p.setString(1,c.id);p.setString(2,c.name);p.setString(3,c.owner.toString());p.setString(4,c.world);
                p.setInt(5,c.minX);p.setInt(6,c.minY);p.setInt(7,c.minZ);p.setInt(8,c.maxX);p.setInt(9,c.maxY);p.setInt(10,c.maxZ);
                p.setInt(11,c.priority);p.setString(12,c.greeting);p.setString(13,c.farewell);p.executeUpdate();
            }
            try(PreparedStatement d=connection.prepareStatement("DELETE FROM members WHERE claim_id=?")){d.setString(1,c.id);d.executeUpdate();}
            try(PreparedStatement p=connection.prepareStatement("INSERT OR IGNORE INTO members(claim_id,uuid) VALUES(?,?)")){for(UUID u:c.members){p.setString(1,c.id);p.setString(2,u.toString());p.addBatch();}p.executeBatch();}
            try(PreparedStatement d=connection.prepareStatement("DELETE FROM flags WHERE claim_id=?")){d.setString(1,c.id);d.executeUpdate();}
            try(PreparedStatement p=connection.prepareStatement("INSERT INTO flags(claim_id,flag,value) VALUES(?,?,?)")){for(var e:c.flags.entrySet()){p.setString(1,c.id);p.setString(2,e.getKey());p.setString(3,e.getValue());p.addBatch();}p.executeBatch();}
            connection.commit(); cache.put(c.id,c);
        } catch(SQLException e){try{connection.rollback();}catch(SQLException ignored){}plugin.getLogger().severe("SQLite save failed: "+e.getMessage());}
        finally{try{connection.setAutoCommit(true);}catch(SQLException ignored){}}
    }
    public synchronized List<Claim> all(){return new ArrayList<>(cache.values());}
    public synchronized Claim at(Location l) {
        return cache.values().stream().filter(c->c.contains(l)).max(Comparator.comparingInt((Claim c)->c.priority).thenComparingLong(c->-c.volume())).orElse(null);
    }
    public synchronized Claim get(String name,String world){return cache.values().stream().filter(c->c.name.equalsIgnoreCase(name)&&c.world.equals(world)).findFirst().orElse(null);}
    public synchronized List<Claim> byOwner(UUID owner){return cache.values().stream().filter(c->c.owner.equals(owner)).toList();}
    public synchronized boolean overlaps(Claim candidate,String ignoreId){return cache.values().stream().anyMatch(c->!c.id.equals(ignoreId)&&c.overlaps(candidate));}
    public synchronized void delete(String id){try(PreparedStatement p=connection.prepareStatement("DELETE FROM claims WHERE id=?")){p.setString(1,id);p.executeUpdate();cache.remove(id);}catch(SQLException e){plugin.getLogger().severe("SQLite delete failed: "+e.getMessage());}}
    public synchronized void close(){if(connection!=null)try{connection.close();}catch(SQLException ignored){}}
}
