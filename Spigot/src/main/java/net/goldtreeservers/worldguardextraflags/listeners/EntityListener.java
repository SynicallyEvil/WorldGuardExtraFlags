package net.goldtreeservers.worldguardextraflags.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;
import net.goldtreeservers.worldguardextraflags.flags.helpers.ForcedStateFlag;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.world.PortalCreateEvent;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag.State;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.goldtreeservers.worldguardextraflags.WorldGuardExtraFlagsPlugin;
import net.goldtreeservers.worldguardextraflags.flags.Flags;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class EntityListener implements Listener
{
	private final WorldGuardPlugin worldGuardPlugin;
	private final RegionContainer regionContainer;
	private final SessionManager sessionManager;

	private final Set<UUID> skipNextDamage = new HashSet<>();

	@EventHandler(ignoreCancelled = true)
	public void onEntityKnockback(EntityDamageByEntityEvent event){
		if (!(event.getDamager() instanceof Player damager)) return;
		if (!(event.getEntity() instanceof LivingEntity entity)) return;

		if (skipNextDamage.remove(entity.getUniqueId())) return;

		ItemStack item = damager.getInventory().getItemInMainHand();
		if (item == null || item.getType() == Material.AIR || !item.getEnchantments().containsKey(Enchantment.WIND_BURST))
			return;

		LocalPlayer localPlayer = this.worldGuardPlugin.wrapPlayer(damager);
		ApplicableRegionSet regions = this.regionContainer.createQuery().getApplicableRegions(localPlayer.getLocation());
		Boolean allowKnockback = regions.queryValue(localPlayer, Flags.ALLOW_KNOCKBACK);
		if (allowKnockback != null && !allowKnockback) {
			event.setCancelled(true);

			skipNextDamage.add(entity.getUniqueId());
			entity.damage(event.getDamage(), damager);
		}
	}

	@EventHandler
	public void onPlayerVelocity(PlayerVelocityEvent event){
		Player player = event.getPlayer();
		LocalPlayer localPlayer = this.worldGuardPlugin.wrapPlayer(player);

		ApplicableRegionSet regions = this.regionContainer.createQuery().getApplicableRegions(localPlayer.getLocation());
		Boolean allowKnockback = regions.queryValue(localPlayer, Flags.ALLOW_KNOCKBACK);

		if (allowKnockback != null && !allowKnockback) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPortalCreateEvent(PortalCreateEvent event)
	{
		LocalPlayer localPlayer;
		if (event.getEntity() instanceof Player player)
		{
			localPlayer = this.worldGuardPlugin.wrapPlayer(player);
			if (this.sessionManager.hasBypass(localPlayer, localPlayer.getWorld()))
			{
				return;
			}
		}
		else
		{
			localPlayer = null;
		}

		for (BlockState block : event.getBlocks())
		{
			if (this.regionContainer.createQuery().queryState(BukkitAdapter.adapt(block.getLocation()), localPlayer, Flags.NETHER_PORTALS) == State.DENY)
			{
				event.setCancelled(true);
				break;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityToggleGlideEvent(EntityToggleGlideEvent event)
	{
		Entity entity = event.getEntity();
		if (entity instanceof Player player)
		{
			LocalPlayer localPlayer = this.worldGuardPlugin.wrapPlayer(player);
			if (this.sessionManager.hasBypass(localPlayer, localPlayer.getWorld()))
			{
				return;
			}

			ForcedStateFlag.ForcedState state = this.regionContainer.createQuery().queryValue(localPlayer.getLocation(), localPlayer, Flags.GLIDE);
			switch(state)
			{
				case ALLOW:
					break;
				case DENY:
				{
					if (!event.isGliding())
					{
						return;
					}

					event.setCancelled(true);

					//Prevent the player from being allowed to glide by spamming space
					player.teleport(player.getLocation());

					break;
				}
				case FORCE:
				{
					if (event.isGliding())
					{
						return;
					}

					event.setCancelled(true);

					break;
				}
			}
		}
	}
}
