# AnvilFix
Fixes and rebalances anvils.


(Requires the Fabric API)

## Why I Made These Changes

I've been increasingly frustrated with the balance of Survival mode, especially when it comes to anvil enchanting/repairing/renaming. With the recent controversy around the villager rebalance, I thought I'd take the opportunity to try my hand at addressing the issues surrounding anvil mechanics.

## Changes Implemented

### General

- Removed "Too Expensive!".
- Removed XP cost accumulation.
- Made enchanting, repairing, and renaming independent of each other in terms of XP usage.

### Enchanting

- Rethought the enchanting cost system. Instead of relying on frustrating XP accumulation and other obscure factors, costs are now based solely on the enchantments you want to apply.
- Each enchantment has a base cost that is then multiplied by its level. For example, applying Sharpness I costs 2 XP, Sharpness II costs 4 XP, and Sharpness V costs 10 XP.

### Repairing

- Introduced a straightforward linear cost of 2 XP * material amount.
- The removal of "Too Expensive!" means you can now repair your equipment as much as you want without it eventually becoming disposable.

### Renaming

- Renaming now has a flat cost of 2 XP.

### Optional: Mending Rebalance

- I know this change is probably gonna be controversial, so it's an optional feature (mendingWorksWithUnbreaking gamerule, false by default)
- I've always felt like Mending is Mojang's band-aid fix to the anvil repairing issue, while also being overpowered and obtainable too early in the game. With the anvil issues resolved, a rebalance seems appropriate.
- Mending and Unbreaking are now incompatible, providing players with two choices:
  - Choose Unbreaking if you want extended equipment durability, but need to manually repair it through an anvil.
  - Choose Mending for automatic equipment repair via XP, but with standard uses.
- Buffed Mending: Mending now works on unequipped items as well. The hierarchy of repair priority is as follows: Equipped items take priority over unequipped ones, and damaged items are prioritized over those with less damage. Damaged unequipped items are given priority over nearly fully repaired equipped items.

## Disclaimer

This is my first public mod, so it will probably have some bugs. XP costs are still being tweaked and I haven't tested this in multiplayer, but it should work. You can always report any bugs at the GitHub issue tracker.
