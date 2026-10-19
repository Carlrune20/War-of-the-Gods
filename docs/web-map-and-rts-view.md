# Web map and in-game view

There are two ways to command your army, and they work the same way and go through the same commands. You can use them
side by side.

- **The web map** runs in a browser. It is drawn from a live 3D view of the game world.
- **The in-game RTS view** runs inside Minecraft. Press **V** on the game map.

- [The web map](#the-web-map)
- [Map controls](#map-controls)
- [Commanding on the web map](#commanding-on-the-web-map)
- [The in-game RTS view](#the-in-game-rts-view)

## The web map

The mod runs a small web server (port 8124 by default) with a live 3D map of the game. The map is drawn the way the beta
map editor draws worlds: every block as a flat-coloured box, seen from above at an angle through an RTS camera.

It shows:

- the terrain and the map border
- buildings and building sites
- every player and unit on the map, in their team's colour. Units wear a grey helmet and workers a straw hat.
- every mob. Hostile ones are dark green with red eyes, and animals are sandy brown.
- each team's resources, members, units and alliances

Zoomed far out, units and players turn into dots in their team's colour and hostile mobs into faint dots. Animals are
left out. Players on a team command their units here.

**Getting a link.** Type `/rts web` in game and click the link. Each link works once and for 10 minutes, and logs that
browser in for 72 hours. Restarting the server logs everyone out.

**Loading.** A new map is read in the background after a new game starts (or a server start), and it fills in on the
page as it goes. After that, changes to the world show up within a few seconds. Units, players and buildings update
every server tick, 20 times a second.

**Requirements.** The page needs a browser with WebGL 2 (any current Chrome, Edge, Firefox or Safari). Zoomed all the way
out on a large map it draws a lot of blocks, which older phones may find slow.

## Map controls

| Control | What it does |
|---|---|
| Drag (left or middle button, or one finger) | Pan |
| Right-drag | Tilt and turn the view |
| Wheel or pinch | Zoom |
| Two fingers twisting | Turn the view |
| WASD or the arrow keys | Pan |
| Q / E, or the ⟲ ⟳ buttons | Turn a quarter round |
| Top | Look straight down with north up, like a paper map |
| Follow me / Whole map | Keep yourself in the middle, or show the whole map |

## Commanding on the web map

| Control | What it does |
|---|---|
| Town center | Select your town center |
| Click one of your buildings | Select it and see its health. A site shows how far it is built. A town center, barracks, archery range or stables shows its Train buttons and training progress: each click queues one more, shift-click 5, up to 10 waiting; they train one at a time. Level takes it up a level (on the town center, Advance takes the town to the next age). An armory or storehouse shows the upgrades it researches. |
| Farm / House / Barracks / ... | With workers selected: a see-through copy follows the pointer, pale where it fits and red where it does not (in the in-game view it shows the building's own blocks). R or Rotate turns it a quarter round, click places it, and right-click or Esc cancels. Shift-click places more, and the workers build them one after another in the order they were placed. Shift-right-click a building site to add it to their list. |
| Wall | With workers selected: click where the wall starts, or one of your towers, then where it ends (or another tower). A see-through wall follows the pointer, red where it does not fit, and the bar shows its pieces and cost. Shift-click to go on with the next wall from where this one ended. |
| Make gate | With one of your finished wall segments selected: turn it into a gate at once, for gold. |
| Build, train and upgrade buttons | Square icons: a building's picture, a unit's face, an item for an upgrade. Hover one to see its name, cost and what it does. |
| The queue and research icons on a building | Click a unit waiting or in training, or what the building researches or its level-up, to cancel it and get back what it cost. A number on a Train button shows how many of that unit are waiting. |
| Delete | Kill your selected units and destroy your selected buildings or building sites, at once. |
| Right-click one of your building sites or farms, or a gold mine | Send the selected workers to build or work it, or to mine gold. Sent to an unbuilt piece of wall, they go on to build the rest of that wall after it. |
| Right-click one of your damaged buildings | Have the selected workers repair it for free, a third as fast as they would build it. |
| Right-click a tree | Send the selected workers to chop it. |
| Torches | With workers selected: click two corners of an area for them to light up against mobs. |
| Click one of your units | Select it. Shift-click adds or removes it. |
| Shift-drag, or Box then drag | Select your units inside the box, or with none in it your buildings. |
| Double-click one of your units | Select all of your units in view. |
| Shift-click / double-click one of your buildings | Add it to the selected buildings or take it out, or select all your buildings of that kind in view. Train buttons then share the units out, each going to the building with the shortest queue. |
| The worker, warrior and archer counts on the bar | How many of each are selected. Click or ctrl-click one to keep only those, and shift-click to drop them. |
| N idle | Go to the next worker with nothing to do. Shift-click selects them all. |
| Hover or select a tower | A ring on the ground shows how far it shoots. |
| Ctrl + 1 … 0 (or Alt + number, since browsers keep Ctrl + number for tabs) | Make the selected units and buildings control group 1 … 0. |
| Shift + number | Add the selection to that control group. |
| Number | Select that control group. Press it again to move the camera to it. The groups also show on the bar. |
| Right-click with only buildings selected | Set a rally point: units they train walk there. Right-click one of your units that is in a control group and new units join that group instead. Clear rally on the bar removes it. |
| Right-click a player of your team, such as yourself | The selected units follow them, keeping the formation they were in, with a block of space between you and the front row. |
| All / Clear / Esc | Select all your units, or none. |
| Right-click the ground | Send the selected units there, in the formation picked on the bar. |
| Formation / Stance | With warriors or archers selected: how they line up where they are sent (box, line, wedge), and how they fight on their own (aggressive, defensive, hold, passive). See [Orders and combat](orders-and-combat.md). |
| Right-click an enemy unit, player or building, or any mob | Attack it (workers stay out of it). Shift-right-click more enemies to have them attacked one after another. |
| Right-drag, with warriors or archers selected | A red box: every enemy unit and player in it (or with none, the hostile mobs) is attacked. The enemies are shared out by their health, so a strong one draws more attackers than a weak one, and every one draws at least one while there are units enough. Each unit then goes on to the rest, nearest first. Shift adds them after the enemies already ordered. Ctrl and a right drag still turns the view. |
| Space, or Attack move on the bar, then click the ground | With warriors or archers selected: they go there and fight any enemy or enemy building they meet on the way, going on once it is down. |
| Hover a unit or building | A tooltip says what it is and what it is doing, or what a right-click would do with the selected units, such as Attack Blue warrior 3. |
| Stop | Stop and guard where they stand. |
| On a touch screen | Tap your units to select them, then tap the ground or an enemy to give the order. |

## The in-game RTS view

Players with the mod can command from inside Minecraft too. Press **V** on the game map (rebind it under Controls, War of
the Gods). The camera lifts above the world, looking down at an angle like the web map. The mouse is free, and the same
panels, buttons and tooltips as on the web map are drawn over the real world. No clouds are drawn while the view is open.

| Control | What it does |
|---|---|
| WASD or the arrow keys, or the mouse at the edge of the window | Move the camera (hold shift for faster). |
| Q / E | Turn a quarter round. |
| Wheel / Ctrl + wheel | Zoom / tilt. |
| Middle-drag / shift + middle-drag | Turn and tilt the camera / move it. Right-click always gives orders. |
| Click, shift-click, double-click, drag a box | Select your units, as on the web map. |
| Delete | Kill your selected units and destroy your selected buildings, as on the web map. |
| 1 … 0, Ctrl/Shift + number | Control groups, as on the web map (Ctrl + number makes a group). |
| Right-click with buildings selected / a player of your team | Rally point / follow them, as on the web map. |
| Right-click | Move, attack (enemies and mobs), build or work, as on the web map. Shift-right-click enemies to attack them in turn. |
| Right-drag / Space then click | With warriors or archers selected: a red box of enemies to attack, shared out by their health / an attack move, as on the web map. |
| Click one of your buildings | Its controls in the bar, such as Train. |
| H / Space | Go to your town center / to the selected units. Space starts an attack move instead when warriors or archers are selected. |
| Minimap (bottom right) | Click or drag to move the camera there. Right-click to send the selected units there. It shows the map as far as your client has seen it, with buildings, units and hostile mobs, and the part in view outlined. |
| Torches | With workers selected: click two corners of an area for them to light up. |
| Wall / Make gate | With workers selected: click where a wall starts and where it ends, or two of your towers. With a wall segment selected, Make gate. As on the web map. |
| B | The team menu, in the view or in person: join or leave a team. Ops click a player to move them, or fill or shuffle the teams. |
| T or / | Chat, without leaving the view. |
| V, or Esc with nothing selected | Back to playing in person. |

**Your body while you command.** Your body stays where you stood, in your skin, armor and gear, and everyone sees it: in
the view, in person and on the web map.

- Enemy units and players can attack it, and what hurts it hurts you, through your armor. The team panel shows your
  health.
- If it dies, you die there, drop what you carry there, and get the death screen.
- Pressing V again puts you back into it, wherever it was pushed.

Meanwhile the real you floats unseen and unhurt over the spot the camera looks at. That keeps the world there loaded for
you, and you can see in the dark.

The view only shows what your client has loaded, so the camera stays within your render distance of the spot it looks
at. The web map shows the whole map. Hostile mobs go after real players and units, but leave a commander's body alone for
now.
