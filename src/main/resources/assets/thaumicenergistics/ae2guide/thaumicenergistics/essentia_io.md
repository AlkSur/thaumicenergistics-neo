---
navigation:
  title: Moving Essentia
  icon: thaumicenergistics:essentia_provider
  position: 20
  parent: thaumicenergistics/index.md
item_ids:
  - thaumicenergistics:essentia_upgrade_card
  - thaumicenergistics:essentia_provider
  - thaumicenergistics:infusion_provider
  - thaumicenergistics:essentia_provider_connection
  - thaumicenergistics:wireless_connector
  - thaumicenergistics:essentia_vibration_chamber
  - thaumicenergistics:infusion_monitor
---

# Moving Essentia

## Essentia Access Card

<ItemLink id="thaumicenergistics:essentia_upgrade_card" /> goes into an AE2 import bus, export bus or interface
(and, when ExtendedAE is installed, its extended buses and interfaces). The device then moves essentia between the
network and the Thaumcraft block it faces. Modpacks can add hosts with the item tag
`thaumicenergistics:essentia_card_hosts`.

## Essentia Provider

<BlockImage id="thaumicenergistics:essentia_provider" scale="2" />

The <ItemLink id="thaumicenergistics:essentia_provider" /> makes the network's essentia available to Thaumcraft
tubes. It never pushes essentia on its own: devices on the tube pull what they need, and essentia that flows into
the provider is stored in the network.

The <ItemLink id="thaumicenergistics:infusion_provider" /> does the same for an infusion altar: place it within the
matrix's reach (like an essentia jar) and the altar draws its essentia straight from the network.

## Wireless receivers

An <ItemLink id="thaumicenergistics:essentia_provider_connection" /> extends a provider to tubes elsewhere. Each
provider serves up to 8 receivers within 32 blocks in the same dimension (server config can change both).

Link them with the <ItemLink id="thaumicenergistics:wireless_connector" />:

1. Sneak-right-click an unlinked receiver to select it.
2. Sneak-right-click the provider to bind them.
3. Sneak-right-click a linked receiver to disconnect it; a plain right-click shows either side's links.

## Power from essentia

The <ItemLink id="thaumicenergistics:essentia_vibration_chamber" /> burns essentia for AE power. It pulls ignis and
potentia from the network by itself; other aspects can be piped in and burn with their own value.

## Infusion Monitor

The <ItemLink id="thaumicenergistics:infusion_monitor" /> sits next to an infusion altar, takes a Thaumonomicon and
shows the ritual's instability risk and any missing essentia.
