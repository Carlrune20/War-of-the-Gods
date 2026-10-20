# Orders and combat

- [Stances](#stances)
- [Formations and movement](#formations-and-movement)
- [Fighting](#fighting)
- [Archers](#archers)
- [Cavalry](#cavalry)
- [Torches](#torches)
- [Units and loaded chunks](#units-and-loaded-chunks)

## Stances

A unit with no orders guards the spot where it stopped. How it fights then depends on its stance. Set it from the bar
with warriors or archers selected.

| Stance | What a unit does when not ordered to fight |
|---|---|
| Aggressive | Looks for enemies half again as far and chases them up to 40 blocks from its post |
| Defensive (the default) | Fights enemies that come within 10 blocks (archers within their range), and walks back once they are dead or more than 20 blocks from its post |
| Hold | Never leaves its spot: fights only what it can reach from there |
| Passive | Never fights, not even back |

## Formations and movement

Units sent somewhere line up there in the formation picked on the bar, facing the way they went:

- **Box** (the default), **line** (wide) or **wedge**
- warriors in front, archers behind them, and workers at the back

Units going far plan their whole way first, around lakes, cliffs, trees and buildings, and walk it a stretch at a time.
A unit with a wall between it and where it is going plans its way too, however near: round the end of the wall, through
a gap or through a gate of its own team or an ally.

The computer's fighters on an attack go round enemy walls the same way, through any gap, and leave walls they pass
alone. Only where there is no way round, or going round is more than about 64 blocks further than going through, do
they stop at the wall, knock that piece down, and then go on with what they were doing.

A unit that gets stuck, wedged in a block or making no headway for a few seconds, hops out to the nearest open ground
within 8 blocks, off any building, and carries on.

## Fighting

Units fight back when hit. Enemies are:

- units and players of teams that are not allied
- hostile mobs

Ordered to attack, a unit chases its target until one of them dies. Hits from warriors and arrows from archers and towers
all land, however recently the target was last hit, so four archers on one enemy do four times the harm of one.

## Archers

Archers draw their bow as a player does, a second for a full draw. They loose arrows as fast and deadly as a player's
fully drawn bow, trailing crit sparks: 6 damage before upgrades, and up to half again on a crit.

- Ordered to attack, they first spread out in a line facing the target, a few blocks inside their range. If there are
  more than 12, a second row fills the gaps.
- Their arrows fly on past friends.
- Archers shoot at buildings too, taking 5 off with each arrow.

## Cavalry

Cavalry ride into battle. Once a rider has been on the move for over a second, every other arrow misses it and flies on
past, until it stops. Arrows that do hit do three quarters of their damage, and warriors' and players' blows up close
do a quarter more.

The horse is part of the rider, so hitting the horse hurts the rider.

## Torches

Workers put up torches to light the town up. Select workers, press **Torches**, and click two corners of an area, up to
64 blocks each way. They put a torch, for 1 wood, wherever the area is dark enough for mobs. They leave buildings alone.

## Units and loaded chunks

Units keep the ground around them loaded, so they keep walking and fighting with no player nearby, and they are saved
with the game. Each unit keeps the 3x3 chunks around it loaded. That is worth keeping in mind on a small server with many
units.
