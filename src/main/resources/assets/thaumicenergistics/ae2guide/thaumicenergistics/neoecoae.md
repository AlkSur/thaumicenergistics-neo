---
navigation:
  title: Neo ECO AE
  icon: thaumicenergistics:arcane_assembler
  position: 40
  parent: thaumicenergistics/index.md
---

# Neo ECO AE

These notes apply when Neo ECO AE Extension (21.2.10 or newer) is installed. Without it nothing here changes.

## Storage

Neo ECO AE's own matrices do not accept essentia. Thaumic Energistics adds **essentia storage matrices** for ECO
drives in three tiers, with the same capacity, per-type cost and idle drain as ECO's matrices of that tier:

| Matrix | Controller tier | Capacity |
|---|---|---|
| ECO - LE4 Storage Matrix (Essentia) | L4 or higher | 16M bytes |
| ECO - LE6 Storage Matrix (Essentia) | L6 or higher | 64M bytes |
| ECO - LE9 Storage Matrix (Essentia) | L9 | 256M bytes |

Each matrix holds up to 63 aspects; one unit of essentia uses one byte. Craft a matrix from an
ECO Storage Matrix Housing (Essentia) and the ECO cell component of its size; sneak-use an empty matrix to take it
apart again. Partition it in the essentia cell workbench like any essentia cell. Matrices cannot go into AE2's ME
drive or ME chest, and the storage host's statistics list essentia as its own line.

## Parallel arcane crafting

ECO's crafting system can hand batches to an [Arcane Assembler](arcane_crafting.md). The assembler offers
`1 + 2 × acceleration cards` parallel slots (at most 9; server config `assembler.parallel_per_speed_card` and
`assembler.max_parallel`). A batch runs as one job: it needs the vis for every craft in it, and outputs come out in
full stacks.

Arcane recipes are always crafted by an Arcane Assembler; plain crafting workers cannot run them.

## Arcane Crafting Chamber

The **Arcane Crafting Chamber** is an arcane assembler built for ECO crafting CPUs. It comes in three tiers:

| Chamber | Knowledge cores | Batch size |
|---|---|---|
| Arcane Crafting Chamber (L4) | 9 | 32 |
| Arcane Crafting Chamber (L6) | 18 | 128 |
| Arcane Crafting Chamber (L9) | 27 | 512 |

- Put knowledge cores in its slots; it offers every recipe on them (the same recipe on two cores counts once).
- Connect it to a network with a Vis Relay Interface. It draws vis from there like the
  [Arcane Assembler](arcane_crafting.md), and a memory card can bind it to one interface.
- An ECO crafting CPU hands it a whole batch of identical crafts at once. The batch pays the vis and power of every
  craft in it and finishes in one cycle (10, 6 or 4 ticks by tier). A normal AE2 CPU sends it one craft at a time.
- With no vis source and too little stored vis it refuses jobs, like the assembler. Its screen and Jade show what
  it is waiting for.
- Batch sizes are in the server config section `arcane_crafting_chamber`.

Craft the L4 chamber from an Arcane Assembler, ECO crafting casings, crafting workers, an L4 parallel core and a
Thaumic Crystal Processor; upgrade it to L6 and L9 with ECO's parallel cores of that tier.

## Research gate

Arcane patterns stay locked behind the requesting player's research. This also holds for ECO big orders: an order
whose plan needs an arcane recipe the player has not researched is refused.
