---
navigation:
  title: Essentia Storage
  icon: thaumicenergistics:essentia_cell_1k
  position: 10
  parent: thaumicenergistics/index.md
item_ids:
  - thaumicenergistics:essentia_cell_1k
  - thaumicenergistics:essentia_cell_4k
  - thaumicenergistics:essentia_cell_16k
  - thaumicenergistics:essentia_cell_64k
  - thaumicenergistics:essentia_cell_256k
  - thaumicenergistics:essentia_cell_1m
  - thaumicenergistics:essentia_cell_4m
  - thaumicenergistics:essentia_cell_16m
  - thaumicenergistics:essentia_cell_64m
  - thaumicenergistics:essentia_cell_256m
  - thaumicenergistics:essentia_cell_creative
  - thaumicenergistics:essentia_cell_workbench
  - thaumicenergistics:essentia_level_emitter
---

# Essentia Storage

<ItemImage id="thaumicenergistics:essentia_cell_1k" scale="2" />

Essentia in the ME network is a resource type of its own: each aspect is one key, counted in units of essentia.
Terminals show it next to items and fluids, and the usual AE2 tools (partitions, priorities, storage buses,
crafting) work with it.

## Cells

Essentia cells go into any ME Drive or ME Chest. Like item cells they come in sizes from 1k to 256M, and each
aspect stored costs a type. Essentia cells only accept essentia; item and fluid cells never take it.

The <ItemLink id="thaumicenergistics:essentia_cell_creative" /> supplies unlimited amounts of the aspects in its
partition, and destroys any of them inserted into it.

## Cell workbench

The <ItemLink id="thaumicenergistics:essentia_cell_workbench" /> edits an essentia cell's partition. Put a jar or
phial of essentia on a filter slot to use its aspect; hover the buttons to see what each one does.

## Watching levels

The <ItemLink id="thaumicenergistics:essentia_level_emitter" /> emits redstone when an aspect in the network is
above or below a threshold, like AE2's level emitter.

## Storing essentia in other blocks

A storage bus on a Thaumcraft jar or other essentia container exposes it to the network. Modpacks can exclude
block entity types with the block entity tag `thaumicenergistics:essentia_store_blacklist`.
