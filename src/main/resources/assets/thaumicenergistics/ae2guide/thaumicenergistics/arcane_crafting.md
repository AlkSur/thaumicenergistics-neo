---
navigation:
  title: Arcane Crafting
  icon: thaumicenergistics:arcane_assembler
  position: 30
  parent: thaumicenergistics/index.md
item_ids:
  - thaumicenergistics:arcane_crafting_terminal
  - thaumicenergistics:wireless_arcane_crafting_terminal
  - thaumicenergistics:vis_interface
  - thaumicenergistics:vis_connector_card
  - thaumicenergistics:arcane_assembler
  - thaumicenergistics:knowledge_inscriber
  - thaumicenergistics:knowledge_core
  - thaumicenergistics:distillation_encoder
---

# Arcane Crafting

Arcane recipes need **vis**, which essentia in the network cannot replace. Vis reaches ME devices through
Vis Relay Interfaces.

## Vis Relay Interface

The <ItemLink id="thaumicenergistics:vis_interface" /> is a cable part. Place it facing a Thaumcraft vis relay and
every arcane terminal and assembler on the same network can draw vis from it. A memory card binds a device to one
particular interface instead. Hover the part to see whether it faces a relay.

## Arcane Crafting Terminals

<ItemImage id="thaumicenergistics:arcane_crafting_terminal" scale="2" />

The <ItemLink id="thaumicenergistics:arcane_crafting_terminal" /> is a crafting terminal with an arcane worktable.
Put a wand in it: crafting uses the wand's vis, and the terminal refills the wand from the network's relay
interfaces.

The <ItemLink id="thaumicenergistics:wireless_arcane_crafting_terminal" /> does the same anywhere in range. With a
<ItemLink id="thaumicenergistics:vis_connector_card" /> it refills its wand while carried, including in a Curios
slot. Press **I** (default) to open it from your inventory.

## Arcane Assembler

<BlockImage id="thaumicenergistics:arcane_assembler" scale="2" />

The <ItemLink id="thaumicenergistics:arcane_assembler" /> runs arcane crafting jobs for autocrafting, like a
molecular assembler for arcane recipes.

1. Record arcane recipes with the <ItemLink id="thaumicenergistics:knowledge_inscriber" /> onto a
   <ItemLink id="thaumicenergistics:knowledge_core" />, and put the core into the assembler.
2. Connect the assembler to a network with a Vis Relay Interface. It fills its vis buffer from there.
3. Request the item from a terminal as usual.

If the network has no vis source and the buffer cannot cover a job, the assembler refuses it, so the crafting CPU can
use another provider. When it is waiting for vis, its vis bars and Jade show which aspect is short.

Acceleration cards speed it up; each also adds parallel slots, which Neo ECO AE can use (see
[Neo ECO AE](neoecoae.md)).

## Distillation Encoder

The <ItemLink id="thaumicenergistics:distillation_encoder" /> encodes Thaumcraft essentia distillation as an AE2
processing pattern.
