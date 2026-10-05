# DualDex Plus

**DualDex Plus** is a companion Pokédex for dual-screen Android handhelds such as the **AYN Thor**. While you play on the top screen, it reads the Pokémon you're facing and shows its types, weaknesses, resistances, base stats, and how your team matches up against it on the bottom screen.

It's a fork of [DualScreenDex](https://github.com/enrique-paulino/DualScreenDex) by Enrique Paulino. **v1.0** was the first release of the fork; the latest is **[v1.1.1](../../releases/latest)**. It installs as its own app (`io.github.dualdexplus`), so it can sit next to the original.

---

## Demo

[<img src="https://img.youtube.com/vi/TUgP3m9KfhI/hqdefault.jpg" alt="DualDex Plus demo on YouTube" width="480">](https://youtu.be/TUgP3m9KfhI)

Recorded on a real AYN Thor. Click the picture to watch it on YouTube.

---

## Screenshots

All taken on the bottom screen of a real AYN Thor.

| FireRed · scanned from the battle on the top screen | FireRed · browsing with the battle tab |
|:---:|:---:|
| <img src="docs/screenshots/firered_scan_mewtwo.png" alt="FireRed scan" width="280"> | <img src="docs/screenshots/firered_raichu_scan.png" alt="FireRed" width="280"> |
| **Crystal** | **Emerald** |
| <img src="docs/screenshots/crystal_typhlosion.png" alt="Crystal" width="280"> | <img src="docs/screenshots/emerald_zigzagoon.png" alt="Emerald" width="280"> |
| **Violet** | **National Dex · regional forms** |
| <img src="docs/screenshots/violet_floragato.png" alt="Violet" width="280"> | <img src="docs/screenshots/national_alolan_vulpix.png" alt="Alolan Vulpix" width="280"> |
| **Team builder (Legends: Arceus)** | **Pokédex list (FireRed)** |
| <img src="docs/screenshots/team_builder_arceus.png" alt="Team builder" width="280"> | <img src="docs/screenshots/firered_list.png" alt="List" width="280"> |
| **Settings · FireRed or LeafGreen** | **Settings · Gen 1 versions and palettes** |
| <img src="docs/screenshots/settings_versions.png" alt="Versions" width="280"> | <img src="docs/screenshots/settings_gen1_palettes.png" alt="Gen 1 palettes" width="280"> |

### Gen 1 screen palettes

| Game Boy | Pocket | Light |
|:---:|:---:|:---:|
| <img src="docs/screenshots/gen1_gameboy.png" alt="Game Boy" width="280"> | <img src="docs/screenshots/gen1_pocket_mew.png" alt="Pocket" width="280"> | <img src="docs/screenshots/gen1_light.png" alt="Light" width="280"> |
| **Game Boy Color (Yellow)** | **Super Game Boy** | |
| <img src="docs/screenshots/gen1_gbc_yellow.png" alt="Game Boy Color" width="280"> | <img src="docs/screenshots/gen1_sgb.png" alt="Super Game Boy" width="280"> | |

---

## v1.1.1 (bug fixes)
* **Scanner only reads battles.** Names are matched only when a level ("Lv5", ":L5") is on screen, so menus and save screens no longer show a Pokémon (your player name "Link" was being read as Klink).
* **Only Pokémon in your dex.** The scanner now follows the game and Pokédex you pick in the app, so in FireRed it only matches the 386 Pokémon in FireRed's dex.

---

## New in v1.1

<img src="docs/screenshots/v1.1/both_screens_lillipup.png" alt="A ROM hack on the top screen, DualDex Plus customised to match on the bottom" width="360">

*A GBC-style ROM hack on the top screen, and a custom game in DualDex Plus below it: Gold/Silver colours, Silver sprites, 4-colour sprites and dithering, so Lillipup looks like the game's own sprite.*

### In action
Litten is opened from the team strip; the **BATTLE Lillipup** button jumps back to the opponent from the game on the top screen.

<img src="docs/screenshots/v1.1/litten_to_opponent.gif" alt="Litten, then back to the opponent" width="360">

*Recorded on an AYN Thor.*

The custom game's settings, with the game it matches:

<img src="docs/screenshots/v1.1/custom_game_both_screens.webp" alt="Custom game settings with the ROM hack on the top screen" width="360">

### Your own games
In Settings, under **My games**, tap **+ Add game** to save your current setup under a name, for example a ROM hack you're playing.
* **What it saves:** the base game and version, Dex Version, sprites, palette, 4-colour sprites, dithering and LCD effect.
* **Teams:** it has its own teams, starting as a copy of the ones you already had.
* **Saving changes:** anything you change while it's selected is saved into it.
* **Renaming and deleting:** hold its tile to rename or delete it.

### Any game's sprites, in any game
* **Sprites picker:** Settings → **Sprites** keeps the game's theme and dex but shows another game's sprites (Red/Blue to Black/White) or the 3D models.
* **Every Pokémon animates:** Crystal and Emerald idle animations first, then animated 2D sprites for the rest.
* **Pixel art in retro themes:** Game Boy and Game Boy Color themes never show smooth 3D renders. Newer Pokémon get animated pixel sprites, recoloured into the palette.

### Game Boy Color look
* **4-colour sprites:** each sprite keeps just 4 colours (outline, highlight and its two main colours), like real Game Boy Color sprites. A whole animation shares the same 4.
* **Dithering:** checkerboard blends between those colours, like GBC-style ROM hacks. It's a filter, so the app is no bigger.

### Teams
* **Suggested teammates** from the current game's Pokédex. Each one shows which of your weaknesses it resists and which gaps it hits; **+ Add** puts it in the next empty slot.

### Settings
* **Sprite animations switch:** one switch turns all sprite animations off, for every game. Use it if a lower-powered handheld (an RG DS, for example) slows down running a game and the animations together.
* **App Screen:** for devices whose main display is the bottom screen, **App Screen: MAIN** keeps the app on the main display, and **Scan Screen** picks which screen the game is read from.

### Games
* **Partner Pikachu and Partner Eevee** in Let's Go, with their own stats.

### Scanning
* **Scanned cards stay closed while you browse.** If you close a scanned card it doesn't reopen during that battle; the Pokémon's name button takes you back.
* **FireRed / LeafGreen fonts:** look-alike letters (M/W/N/H, O/D) and gender symbols stuck to the name are now read correctly.

### Fixes
* The search box no longer carries old text into the team picker or another game's dex.
* Fixed two crashes, one when the app starts up and one when opening Settings.

### v1.1 screenshots

| My games | Sprites picker |
|:---:|:---:|
| <img src="docs/screenshots/v1.1/my_games.png" alt="My games" width="280"> | <img src="docs/screenshots/v1.1/sprites_picker.png" alt="Sprites picker" width="280"> |
| **Crystal sprites in another game** | **Pixel Rowlet in a GBC theme** |
| <img src="docs/screenshots/v1.1/crystal_sprites_any_game.png" alt="Crystal sprites" width="280"> | <img src="docs/screenshots/v1.1/rowlet_pixel_sprite.png" alt="Rowlet" width="280"> |
| **4-colour sprites and dithering** | **Herdier, 4 colours** |
| <img src="docs/screenshots/v1.1/settings_sprites_4colour_dithering.png" alt="Settings" width="280"> | <img src="docs/screenshots/v1.1/herdier_4colour.png" alt="Herdier" width="280"> |
| **Suggested teammates** | **Sprite animations switch** |
| <img src="docs/screenshots/v1.1/suggested_teammates.png" alt="Suggested teammates" width="280"> | <img src="docs/screenshots/v1.1/sprite_animations_toggle.png" alt="Sprite animations" width="280"> |
| **App Screen** | **Card stays closed, battle button** |
| <img src="docs/screenshots/v1.1/app_screen.png" alt="App Screen" width="280"> | <img src="docs/screenshots/v1.1/battle_tab_card_stays_closed.png" alt="Battle tab" width="280"> |

---

## What's in v1.0

### Built for the bottom screen
* **Opens on the second screen by itself.** If it's launched on the main screen of a dual-screen device, it moves to the other one so it never covers your game.
* **Full screen,** with no status bar or navigation bar (swipe from an edge to show them briefly).
* **Wide layout for landscape bottom screens** (Thor, Retroid Pocket Duo, RG DS):
  * the Pokémon card and its data side by side;
  * "Your team vs this" across the bottom.
  * Everything fits on one screen with no scrolling. The sprite grows or shrinks to fill the card, and the badges and stats adjust to how many weaknesses a Pokémon has.
* **Gestures, no floating buttons:**
  * swipe left and right to go through the Pokédex, even after a search or a scan;
  * swipe down to close a card or the team builder;
  * when a Pokémon is detected, its name appears as a single button to jump to it.

### Games
* **Pick the game you're playing** in Settings → Games:
  * Red / Blue / Yellow
  * Gold / Silver / Crystal
  * Ruby / Sapphire / Emerald
  * FireRed / LeafGreen
  * Let's Go Pikachu / Eevee
  * Sword / Shield
  * Brilliant Diamond / Shining Pearl
  * Legends: Arceus
  * Scarlet / Violet
  * Legends: Z-A
  * the latest games (National Dex)
* **Exact version:** Gold vs Silver vs Crystal, Sword vs Shield and so on. The version changes the colours and the sprites.
* **Each game's own Pokédex order and numbers,** for example Hisui #001 Rowlet or Johto #001 Chikorita, including DLC dexes. You can limit the list to Pokémon in that game, or show all 1025.
* **Match dex to game:** the type chart, dex, forms and sprites all follow the selected game.
* **Megas, Primals and regional forms** with their own types and stats, only in games where they exist.

### Looks
* **Themes that look like the games:** Game Boy text boxes, GBA panels, Switch-era menus. Gen 1 and Gen 2 use real 8×8 pixel text.
* **Hardware-accurate palettes:**
  * Gen 1 stays within 4 colours, with a choice of Game Boy, Pocket, Light, Game Boy Color (per version) and Super Game Boy palettes.
  * Gen 2 stays within the Game Boy Color's 56 colours.
* **Optional LCD line effect** for Game Boy, Game Boy Color and Game Boy Advance games.
* **Sprites for every generation.** The right game's sprites, animated 3D models, or animated 2D sprites. Everything is bundled and works offline:
  * Crystal and Emerald idle animations;
  * animated 3D models;
  * Black/White-style animated 2D sprites.

### Teams
* **A team for every game,** with as many teams as you like: rename, delete, switch, and copy or paste in Showdown format.
* **"Your team vs this"** on every card. Each team member shows how hard it hits and how hard it gets hit, best counter first. Tap a member to open it.
* **Team analysis:** shared weaknesses, and types your team can't hit super-effectively.

### Scanning
* **Game Language: English / Japanese.** Japanese uses ML Kit's on-device Japanese model and matches katakana names. Based on [upstream PR #4](https://github.com/enrique-paulino/DualScreenDex/pull/4).
* From the original app:
  * scan the top or bottom screen, left or right side;
  * the scanner sleeps when the app is in the background.

---

## Installation

1. Download the APK from the [Releases](../../releases) page and install it.
2. Turn on **DualDex Plus** in Android's **Accessibility** settings (the app shows a banner that takes you there).
   * This permission is only used to read the game screen for on-device text recognition. No images are saved or sent anywhere, and the app has no internet permission.
3. Open Settings → Games and pick the game you're playing.

---

## Made with Claude Code

This fork was built with **[Claude Code](https://claude.com/claude-code)**, Anthropic's AI coding agent, working with the project owner. Claude Code did the following:
* wrote the features above;
* tested layouts on emulators and on a real AYN Thor;
* recorded the demo.

---

## Credits and assets

* Original app: [DualScreenDex](https://github.com/enrique-paulino/DualScreenDex) by Enrique Paulino (MIT).
* Japanese OCR: [upstream PR #4](https://github.com/enrique-paulino/DualScreenDex/pull/4).
* Pokédex data, base stats, game dexes and 3D renders: [pokemondb.net](https://pokemondb.net).
* Animated 3D models, Black/White-style 2D sprites and their animations: the community sprite collection hosted by [Pokémon Showdown](https://play.pokemonshowdown.com/sprites/).
* Super Game Boy palette values: the [pret/pokered](https://github.com/pret/pokered) disassembly.
* Classic game sprites (Gen 1–5) and box icons: community sprite packs.
* Pixel font: [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) by CodeMan38, SIL Open Font License (see `app/src/main/assets/licenses/`).

## Tech stack

* Kotlin, XML layouts, Material Design
* Google ML Kit on-device text recognition (Latin + Japanese)
* AccessibilityService screen capture and a broadcast receiver

## License

MIT, see [LICENSE](LICENSE).

*DualDex Plus is an unofficial, free fan project. It is not affiliated with, endorsed or supported by Nintendo, Game Freak, Creatures or The Pokémon Company. Pokémon names, sprites and related media are trademarks and copyrights of their respective owners.*
