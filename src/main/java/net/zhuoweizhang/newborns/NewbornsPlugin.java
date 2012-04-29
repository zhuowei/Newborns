package net.zhuoweizhang.newborns;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
public class NewbornsPlugin extends JavaPlugin implements Listener {

	//public Set<String> bornSet;

	public List<Baby> babyList;

	public Map<String, Baby> babyCarrierMap;

	public long tickBabyInterval = 20; //tick every second

	public long maximumBabySurvivalTime = 20 * 60 * 5; // 5 minutes

	private Random rand = new Random();

	public void onDisable() {
	}

	public void onEnable() {
		//File bornFile = new File(getDataFolder(), "born.yml");
		//YamlConfiguration bornConfig = YamlConfiguration.loadConfiguration(bornFile);
		//bornSet = new HashSet<String>();
		//bornSet.addAll(bornConfig.getStringList("players"));
		FileConfiguration config = getConfig();
		config.options().copyDefaults(true);
		maximumBabySurvivalTime = config.getLong("baby-survival-seconds") * 20;
		saveConfig();
		babyList = new ArrayList<Baby>();
		babyCarrierMap = new HashMap<String, Baby>();
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new TickBabiesTask(), tickBabyInterval, tickBabyInterval);
		getServer().getPluginManager().registerEvents(this, this);
	}

	public boolean hasBeenBorn(String username) {
		//return bornSet.contains(username);
		return getServer().getOfflinePlayer(username).isWhitelisted();
	}

	public void addToBornList(String username) {
		getServer().getOfflinePlayer(username).setWhitelisted(true);
	}

	public void haveChildren(Player parent1, Player parent2) {
		boolean firstCarryBaby = rand.nextBoolean();
		Baby baby = new Baby();
		if (firstCarryBaby) {
			baby.parents[0] = parent1.getName();
			baby.parents[1] = parent2.getName();
			babyCarrierMap.put(parent1.getName(), baby);
		} else {
			baby.parents[0] = parent2.getName();
			baby.parents[1] = parent1.getName();
			babyCarrierMap.put(parent2.getName(), baby);
		}
		babyList.add(baby);
		broadcastBabyBirth(baby);
	}

	protected void broadcastBabyBirth(Baby baby) {
		getServer().broadcastMessage("[Newborns] " + ChatColor.GREEN + 
			baby.parents[0] + " and " + baby.parents[1] + " slept together! " + baby.parents[0] + " is holding the baby!");
	}

	protected void broadcastBabyDeathByTime(Baby baby) {
		getServer().broadcastMessage("[Newborns] " + ChatColor.GREEN + 
			baby.parents[0] + " and " + baby.parents[1] + "'s baby died of malnutrition.");
	}

	protected void broadcastBabyDeathByDrop(Baby baby) {
		getServer().broadcastMessage("[Newborns] " + ChatColor.GREEN + 
			baby.parents[0] + " dropped the baby.");
	}

	protected void broadcastBabyGrow(Baby baby, String name) {
		getServer().broadcastMessage("[Newborns]" + ChatColor.GREEN + 
			baby.parents[0] + " and " + baby.parents[1] + "'s baby has grown up and became " + name + ". ");
	}

	protected void tickBabies() {
		for (int i = babyList.size() - 1; i >= 0; --i) {
			Baby baby = babyList.get(i);
			baby.ticksSinceBirth += tickBabyInterval;
			if (baby.ticksSinceBirth > maximumBabySurvivalTime) {
				babyList.remove(i);
				if (babyCarrierMap.get(baby.parents[0]) != null) {
					babyCarrierMap.remove(baby.parents[0]);
				}
				broadcastBabyDeathByTime(baby);
			}
		}
	}

	public Baby selectNextBirthBaby() {
		if (babyList.size() < 1) return null;
		Baby baby = null;
		long babyAge = -1;
		for (Baby b : babyList) { //Find the oldest baby
			if (b.ticksSinceBirth > babyAge) {
				baby = b;
				babyAge = b.ticksSinceBirth;
			}
		}
		return baby;
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerBedEnter(PlayerBedEnterEvent e) {
		if (e.isCancelled()) return;
		Player player = e.getPlayer();
		if (babyCarrierMap.get(player.getName()) != null) return;
		Block bed = e.getBed();
		Location bedLocation = bed.getLocation();
		List<Player> players = bed.getWorld().getPlayers();
		for (Player p: players) {
			if (p.equals(player) || !p.isSleeping() || babyCarrierMap.get(p.getName()) != null) continue;
			Location pLocation = p.getLocation();
			if (Math.floor(pLocation.getY()) == Math.floor(bedLocation.getY()) && pLocation.distanceSquared(bedLocation) <= 4) {
				haveChildren(player, p);
			}
		}
	}

	/*@EventHandler
	public void onPlayerPreLogin(PlayerPreLoginEvent event) {
		System.out.println(event.toString());
		if (event.getResult() == PlayerPreLoginEvent.Result.KICK_WHITELIST && babyList.size() > 0) {
			System.out.println("Prelogin");
			Baby baby = selectNextBirthBaby();
			System.out.println(baby.toString());
			babyList.remove(baby);
			if (babyCarrierMap.get(baby.parents[0]) != null) {
				babyCarrierMap.remove(baby.parents[0]);
			}
			broadcastBabyGrow(baby, event.getName());
			event.setResult(PlayerPreLoginEvent.Result.ALLOWED);
			needsTeleportingMap.put(event.getName(), baby);
			addToBornList(event.getName());
		}
	}*/

	@EventHandler
	public void onPlayerLoginEvent(PlayerLoginEvent event) {
		//System.out.println("Login");

		if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST && babyList.size() > 0) {
			System.out.println("[Newborns] Whitelisting player as baby");
			Baby baby = selectNextBirthBaby();
			babyList.remove(baby);
			if (babyCarrierMap.get(baby.parents[0]) != null) {
				babyCarrierMap.remove(baby.parents[0]);
			}
			broadcastBabyGrow(baby, event.getPlayer().getName());
			event.setResult(PlayerLoginEvent.Result.ALLOWED);
			addToBornList(event.getPlayer().getName());
			Player parent = getServer().getPlayer(baby.parents[0]);
			if (parent != null) {
				event.getPlayer().teleport(parent);
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Baby baby = babyCarrierMap.get(event.getPlayer().getName());
		if (baby != null) {
			babyList.remove(baby);
			babyCarrierMap.remove(baby.parents[0]);
			broadcastBabyDeathByDrop(baby);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDeath(EntityDeathEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			Baby baby = babyCarrierMap.get(player.getName());
			if (baby != null) {
				babyList.remove(baby);
				babyCarrierMap.remove(baby.parents[0]);
				broadcastBabyDeathByDrop(baby);
			}
		}
	}

	@EventHandler
	public void onServerListPing(ServerListPingEvent event) {
		int slotsAvailable = event.getNumPlayers() + babyList.size();
		event.setMaxPlayers(slotsAvailable > 0 ? slotsAvailable: 1);
	}

	private class TickBabiesTask implements Runnable {
		public void run() {
			tickBabies();
		}
	}
}

