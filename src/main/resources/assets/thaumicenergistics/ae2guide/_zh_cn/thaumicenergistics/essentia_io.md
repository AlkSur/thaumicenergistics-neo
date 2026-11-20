---
navigation:
  title: 源质输送
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

# 源质输送

## 源质访问卡

<ItemLink id="thaumicenergistics:essentia_upgrade_card" /> 可装进 AE2 的输入总线、输出总线或接口（装有 ExtendedAE 时也包括它的扩展总线和接口），
之后该设备会在网络和所对的神秘时代方块之间搬运源质。整合包可通过物品标签 `thaumicenergistics:essentia_card_hosts` 添加可装卡的设备。

## 源质供应器

<BlockImage id="thaumicenergistics:essentia_provider" scale="2" />

<ItemLink id="thaumicenergistics:essentia_provider" /> 让源质管道可以取用网络中的源质。它不会主动推送：管道上的设备按需抽取，
流入供应器的源质则存进网络。

<ItemLink id="thaumicenergistics:infusion_provider" /> 对注魔祭坛起同样作用：像源质罐一样放在注魔矩阵的范围内，祭坛就会直接从网络抽取源质。

## 无线接收器

<ItemLink id="thaumicenergistics:essentia_provider_connection" /> 把供应器延伸到别处的管道。每台供应器最多服务 8 个同一维度、32 格内的接收器（两项都可在服务端配置中修改）。

用 <ItemLink id="thaumicenergistics:wireless_connector" /> 绑定：

1. 潜行右键未连接的接收器以选中它。
2. 潜行右键供应器完成绑定。
3. 潜行右键已连接的接收器即可断开；普通右键可查看任一端的连接。

## 用源质发电

<ItemLink id="thaumicenergistics:essentia_vibration_chamber" /> 燃烧源质产生 AE 能量。它会自行从网络抽取火与能量要素；其他要素可以用管道送入，按各自的数值燃烧。

## 韵魔观测器

<ItemLink id="thaumicenergistics:infusion_monitor" /> 放在注魔祭坛旁并放入魔导手册，显示仪式的不稳定风险和缺少的源质。
