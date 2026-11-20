---
navigation:
  title: 奥术合成
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

# 奥术合成

奥术配方需要 **魔力**，网络中的源质不能代替。魔力通过魔力中继接口进入 ME 设备。

## 魔力中继接口

<ItemLink id="thaumicenergistics:vis_interface" /> 是线缆部件。让它朝向神秘时代的魔力中继器，同一网络上的所有奥术终端和装配器都能从中取用魔力。
用内存卡可以把设备绑定到某一个接口。鼠标悬停在部件上可查看它是否朝向中继器。

## 奥术合成终端

<ItemImage id="thaumicenergistics:arcane_crafting_terminal" scale="2" />

<ItemLink id="thaumicenergistics:arcane_crafting_terminal" /> 是带奥术工作台的合成终端。放入法杖后，合成消耗法杖中的魔力，终端会从网络的中继接口为法杖补充魔力。

<ItemLink id="thaumicenergistics:wireless_arcane_crafting_terminal" /> 在无线范围内的任何地方都能这样用。装上
<ItemLink id="thaumicenergistics:vis_connector_card" /> 后，随身携带（包括饰品栏）时也会为法杖补充魔力。默认按 **I** 从背包中打开。

## 奥术装配器

<BlockImage id="thaumicenergistics:arcane_assembler" scale="2" />

<ItemLink id="thaumicenergistics:arcane_assembler" /> 为自动合成执行奥术合成任务，相当于奥术配方的分子装配室。

1. 用 <ItemLink id="thaumicenergistics:knowledge_inscriber" /> 把奥术配方记录到 <ItemLink id="thaumicenergistics:knowledge_core" /> 上，再把核心放进装配器。
2. 把装配器接入带有魔力中继接口的网络，它会从那里充满自己的魔力缓存。
3. 照常在终端中下单。

如果网络没有任何魔力来源、缓存也不够完成任务，装配器会拒绝该任务，合成 CPU 会改找其他供应者。等待魔力时，界面的魔力条和 Jade 会显示缺的是哪种要素。

加速卡可以提速，每张卡还会增加并行槽位，供 Neo ECO AE 使用（见 [Neo ECO AE](neoecoae.md)）。

## 蒸馏编码台

<ItemLink id="thaumicenergistics:distillation_encoder" /> 把神秘时代的源质蒸馏编码成 AE2 处理样板。
