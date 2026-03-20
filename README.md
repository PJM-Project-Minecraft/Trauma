# Trauma Mod

A realistic medical simulation mod for Minecraft that adds advanced injury, bleeding, and medical treatment systems.

## Overview

Trauma is a sophisticated medical mod that introduces realistic injury mechanics, blood loss simulation, and comprehensive medical treatment options. Players must manage bleeding, fractures, dislocations, and blood volume using various medical items and techniques.

## Features

### 🩸 **Blood System**
- Realistic blood volume tracking (default: 5000ml)
- Multiple levels of bleeding (Light, Heavy, Severe)
- Blood loss affects player health and consciousness
- Blood transfusion system with blood bags

### 🏥 **Injury System**
- **Bleeding injuries** with progressive severity levels
- **Broken legs** that affect movement
- **Dislocated joints** requiring manual reduction
- Visual injury indicators on player models

### 💊 **Medical Items**
- **Bandage** - Reduces bleeding by 1 level (3s use time)
- **Tourniquet** - Instantly stops all bleeding (2s use time)
- **Blood Bag** - Restores 1000ml blood volume (5s use time)
- **Splint** - Treats broken legs (2s use time)

### 🎮 **Interactive Medical Minigames**
- Bandage application minigame with shaking hands simulation
- Dislocation reduction minigame requiring precision
- Realistic medical treatment timing and interactions

### 🎨 **Visual Effects**
- Blood spray particles and effects
- Injury layer rendering on all living entities
- Suppression arm visual indicators
- Comprehensive HUD overlays for medical status

### ⚙️ **Advanced Features**
- Ragdoll physics integration using JBullet
- TaCZ (Timeless and Guns Zero) compatibility
- Configurable settings for common and client options
- Key bindings for medical actions
- Multiplayer support with proper networking

## Installation

1. **Requirements:**
   - Minecraft 1.21.1
   - NeoForge 21.1.219 or higher
   - Java 21

2. **Installation Steps:**
   - Download the latest Trauma mod JAR
   - Place in your `mods` folder
   - Launch Minecraft with NeoForge

## Controls

| Key | Action |
|-----|--------|
| Suppress Bleeding Key | Hold to suppress bleeding temporarily |
| Fix Dislocation Key | Use during dislocation minigame |

## Commands

- `/trauma heal <player>` - Heals target player and removes bleeding
- `/trauma damage <player> <amount>` - Deals specified blood loss to target player

## Configuration

The mod includes two configuration files:
- `bloodybits-common.toml` - Common gameplay settings
- `bloodybits-client.toml` - Client-side visual and UI settings

## Compatibility

- **TaCZ Integration**: Automatic compatibility with Timeless and Guns Zero mod
- **GeckoLib**: Uses GeckoLib for advanced entity animations
- **Multiplayer**: Fully compatible with multiplayer servers

## Screenshots

*(Add screenshots here showing the medical interface, injury effects, and gameplay)*

## Contributing

This mod is currently in development (v0.1.0). Bug reports and suggestions are welcome!

## License

All Rights Reserved

## Credits

- **Author**: Liko
- **Special Thanks**: GeckoLib team for animation framework
- **Physics**: JBullet physics engine integration

---

**Warning**: This mod contains realistic medical simulations including blood and injuries. User discretion advised.

For support and updates, please check the official mod page.
