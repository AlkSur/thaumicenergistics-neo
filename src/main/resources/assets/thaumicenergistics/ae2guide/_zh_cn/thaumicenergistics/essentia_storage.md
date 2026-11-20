---
navigation:
  title: 源质存储
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

# 源质存储

<ItemImage id="thaumicenergistics:essentia_cell_1k" scale="2" />

ME 网络里的源质是一种独立的资源类型：每种要素是一个键，以源质单位计数。终端里它和物品、流体并列显示，
分区、优先级、存储总线、自动合成等 AE2 功能都适用。

## 元件

源质元件放进任意 ME 驱动器或 ME 箱子。和物品元件一样有 1k 到 256M 多种容量，每种要素占一个类型。
源质元件只接受源质；物品和流体元件不会接收源质。

<ItemLink id="thaumicenergistics:essentia_cell_creative" /> 能无限提供分区里的要素，存入的这些要素会被销毁。

## 元件工作台

<ItemLink id="thaumicenergistics:essentia_cell_workbench" /> 用来编辑源质元件的分区。把装有源质的罐子或小瓶放到过滤槽上即可使用其要素；
鼠标悬停在按钮上可查看各自作用。

## 监测存量

<ItemLink id="thaumicenergistics:essentia_level_emitter" /> 在网络中某种要素高于或低于阈值时输出红石信号，用法同 AE2 的标准发信器。

## 其他方块里的源质

存储总线对准神秘时代的源质罐等容器，就能把其中的源质接入网络。整合包可以用方块实体类型标签
`thaumicenergistics:essentia_store_blacklist` 排除特定方块。
