# Atelier

Plugin para **Paper 1.21.1** que añade items, bloques, muebles, armaduras, efectos de poción, menús, glyphs y resource pack propios.
**Lee las configs y assets de Oraxen e ItemsAdder**, así que puedes reutilizar tus texturas y archivos.

![Paper](https://img.shields.io/badge/Paper-1.21.1-blue) ![Java](https://img.shields.io/badge/Java-21-orange) ![License](https://img.shields.io/badge/license-MIT-green)

## 1) Conseguir el .jar (sin instalar nada)
1. Sube este proyecto a GitHub (ver abajo).
2. Pestaña **Actions → Build → último run → Artifacts → `Atelier-jar`** (descarga el .jar).
3. Para versiones fijas: crea un tag `v0.4.0` y GitHub publica el .jar en **Releases** automáticamente.

Compilar en tu PC (JDK 21 + Maven): `mvn package` → `target/Atelier-0.4.0.jar`

## 2) Instalar
1. Copia el .jar a `plugins/` y arranca el servidor (se crea `plugins/Atelier/` con ejemplos).
2. Abre el puerto **8082** y pon tu IP/dominio en `config.yml` → `pack.host.public-url`.
3. Migrar: copia tus carpetas a `plugins/Atelier/import/oraxen/` (`items glyphs recipes pack`) o `import/itemsadder/` (`contents`).
4. `/atelier reload` y `/atelier items`.

> No uses Atelier a la vez que Oraxen/ItemsAdder activos.

## 2b) La forma MÁS fácil de añadir items: carpeta `drop/`
Suelta un PNG en `plugins/Atelier/drop/`, ejecuta `/atelier reload` y ya es un item (el nombre del archivo es el id):

| Nombre del PNG | Resultado |
|---|---|
| `ruby_sword.png` `_pickaxe` `_axe` `_shovel` `_hoe` | herramienta |
| `ruby_helmet.png` `_chestplate` `_leggings` `_boots` | armadura (set `ruby`) |
| `ruby_block.png` | bloque colocable |
| `ruby_furniture.png` | mueble |
| cualquier otro | item normal |

Para stats, efectos, bonus de set, etc. usa la **página Atelier Configurator** (genera el YAML) o edita `items/*.yml`.

## 3) Comandos (`/atelier` o `/at`, permiso `atelier.admin`)
| Comando | Qué hace |
|---|---|
| `give <jugador\|@p\|@a> <id> [n]` | Da un item |
| `items [filtro]` | **Menú con todos los items**; clic = tomar, shift+clic = 64 |
| `menu <id> [jugador]` | Abre un menú de `menus/*.yml` |
| `create <id>` | **Convierte el item de tu mano** en item Atelier (se guarda en `items/ingame.yml`) |
| `delete <id>` | Borra un item creado con `create` |
| `reload` | Recarga todo y regenera el pack |
| `check` | Muestra errores de tus configs |
| `pack [send]`, `list`, `glyphs`, `info` | Utilidades |

## 4) Formato de items (compatible con Oraxen)
```yaml
ruby_sword:
  displayname: "<red>Espada de Rubí"
  material: DIAMOND_SWORD
  Unbreakable: true
  Enchantments: { sharpness: 5 }
  AttributeModifiers:
    - { attribute: ATTACK_SPEED, amount: 0.6, operation: 0, slot: HAND }
  Effects:                       # NUEVO
    - { effect: wither, level: 1, duration: 5, target: ENEMY, trigger: HIT }
    - { effect: speed, level: 2, duration: 10, target: SELF, trigger: HOLD }
  Pack:
    generate_model: true
    parent_model: item/handheld
    textures: [default/ruby]
```
**Efectos:** `target` = `SELF` (a ti) / `ENEMY` (al enemigo). `trigger` = `HIT` (al golpear), `USE` (clic derecho, al enemigo que miras), `HOLD` (en mano), `WEAR` (puesto).

**Armaduras:**
```yaml
ruby_helmet:
  material: LEATHER_HELMET
  Armor:
    set: ruby
    color: "#C8143C"          # tinte de cuero: se ve puesto sin mods
    defense: 3
    toughness: 1
    bonus:                    # bonus de set por piezas puestas
      - { pieces: 2, effect: speed, level: 1 }
      - { pieces: 4, effect: resistance, level: 1 }
```
**HUD / interfaz** (`huds/*.yml`): texto en la actionbar con `%health% %food% %armor% %level% %bar_health% %bar_food%` y `:glyphs:`.

**Menús** (`menus/*.yml`): ver `menus/example.yml`. Acciones: `command`, `console`, `message`, `open`, `close`, `sound`, `give`.

Otras mecánicas (`mechanics.block`, `mechanics.furniture`), glyphs y recetas: ver los ejemplos en `items/`, `glyphs/`, `recipes/`.

## 4b) Mecánicas (mismas claves que Oraxen)
Se ponen dentro de `mechanics:` del item. Todas son configurables y combinables.

| Mecánica | Qué hace | Opciones |
|---|---|---|
| `durability` | Durabilidad propia (la barra vanilla se escala) | `value` |
| `mining` | Rompe un área (martillo) | `radius`, `depth` |
| `smelting` | Las menas caen fundidas | `enabled` |
| `efficiency` | Más velocidad de minado (haste en mano) | `amount` |
| `lifeleech` | Te curas al golpear | `amount` |
| `bleeding` | Sangrado (daño con el tiempo) | `chance`, `duration`, `interval`, `damage_per_interval` |
| `thor` | Clic derecho: rayos donde miras | `lightning_bolts_amount`, `random_location_variation`, `delay` (ms) |
| `soulbound` | El item no se pierde al morir | `lose_chance` |
| `armor_effects` | Efectos mientras lo llevas puesto | `<efecto>: {amplifier}` |
| `commands` | Ejecuta comandos al clic derecho | `permission`, `cooldown`, `one_usage`, `console`, `player` |
| `custom` | Acciones: `[console] [player] [message] [sound] [effect] [heal] [feed]` | `event`, `cooldown`, `one_usage`, `conditions`, `actions` |
| `backpack` | Mochila con inventario guardado en el item | `rows`, `title` |
| `hat` / `components.equippable.slot` | Clic derecho para ponerte cualquier item en cabeza/pecho/piernas/pies | `slot` |
| `components.food` + `consumable` | Item comestible | `nutrition`, `saturation`, `can_always_eat`, `consume_seconds` |
| `Effects:` (propio) | Efectos de poción a ti o al enemigo | `trigger`: HIT, USE, HOLD, WEAR, **EAT** |

Ejemplos de cada una en `items/example.yml`.

## 5) Subir a GitHub
```bash
cd atelier
git init -b main
git add .
git commit -m "Atelier 0.4.0"
git remote add origin https://github.com/TU_USUARIO/atelier.git   # crea el repo vacío en github.com/new
git push -u origin main
```
(El `git add .` incluye `.github/workflows`, que es lo que compila el .jar solo.)

## Estado
- Probado: sintaxis. **No probado en servidor**; si Actions falla, copia el error y corrígelo.
- Armadura puesta con modelo propio (cuerpo) requiere OptiFine/CIT o shaders: aún no incluido; el tinte de cuero sí funciona.
- Dureza personalizada de bloques y mecánicas avanzadas de Oraxen/ItemsAdder: pendientes.
