# 安装与依赖

## 支持版本

- Minecraft `1.21.1`
- NeoForge `21.1.233` 或更高的 21.1.x
- Iron's Spells 'n Spellbooks `1.21.1-3.16.2` 或更高版本
- Curios API `9.5.1+1.21.1`

## 安装步骤

1. 从 [Releases](https://github.com/MKUXQQ/iron-magic-duel/releases/latest) 下载 `iron_magic_duel-9.19.jar`。
2. 将同一个 JAR 放入客户端与服务器的 `mods` 文件夹。
3. 确认客户端和服务器只保留一个 Iron Magic Duel JAR，且版本完全相同。

9.19 SHA-256：

```text
045A84ECCAB96006C189451109DD228BD554C6F7C7AC1B56DDADF7B296CC9AAF
```

## 从源码构建

```powershell
.\gradlew.bat clean check jar
```

产物位于 `build/libs`。
