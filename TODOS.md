# 1.21.10 和 1.21.11 适配任务

## 已完成

- [x] 创建 `versions/1.21.10/gradle.properties`
- [x] 创建 `versions/1.21.11/gradle.properties`
- [x] 创建 `versions/mapping-1.21.1-1.21.10.txt`
- [x] 创建 `versions/mapping-1.21.10-1.21.11.txt`
- [x] 复制 `SyncmaticaPayload.java` 到 1.21.10 和 1.21.11 版本目录
- [x] 修改 `common.gradle` 支持 Modrinth Maven（用于 1.21.11 依赖）
- [x] 升级 Gradle 到 9.2.1
- [x] 升级 Fabric Loom 到 1.14-SNAPSHOT
- [x] 修复 Gradle 9 兼容性问题（`sourceCompatibility`、`archivesBaseName`）

## 待完成

### 1. 修复 Fabric API 版本差异

**问题**: `net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback` 在 1.19+ 已移除，改为 `v2`

**文件**: `src/main/java/cn/net/rms/syncmatica_r/SyncmaticaFabric.java`

**修复方案**:
```java
//#if MC >= 11900
//$$ import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//#else
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//#endif

// 在 registerCommands() 方法中:
//#if MC >= 11900
//$$ CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SyncmaticaCommand.register(dispatcher));
//#else
CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> SyncmaticaCommand.register(dispatcher));
//#endif
```

### 2. 修复 MinecraftClient.disconnect() 方法签名变化

**问题**: `MixinMinecraftClient.java:17` 警告 `Cannot find target method "disconnect()V"`

**文件**: `src/main/java/cn/net/rms/syncmatica_r/mixin/MixinMinecraftClient.java`

**修复方案**: 需要检查 1.21.10 中 `MinecraftClient.disconnect()` 的新签名，可能需要添加预处理器指令

### 3. 测试构建

完成上述修复后，运行以下命令测试：

```bash
# 测试 1.21.10
./gradlew :1.21.10:build

# 测试 1.21.11
./gradlew :1.21.11:build

# 测试所有版本
./gradlew build
```

### 4. 验证旧版本兼容性

确保 Gradle 9.2.1 和 Loom 1.14 不会破坏旧版本（1.17.1, 1.20.1, 1.21.1）的构建：

```bash
./gradlew :1.17.1:build
./gradlew :1.20.1:build
./gradlew :1.21.1:build
```

## 注意事项

1. **mainProject 是 1.17.1**：所有预处理器指令应该以 1.17.1 为基准编写，新版本的代码用 `//$$ ` 注释
2. **Modrinth Maven**：1.21.11 使用 Modrinth Maven 获取 Litematica/MaLiLib 依赖（CurseForge 尚未更新）
3. **Loom 版本要求**：1.21.11 的 Litematica 是用 Loom 1.14.10 构建的，必须使用 Loom 1.14+
