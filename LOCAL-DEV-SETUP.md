# 本地开发环境配置指南

本项目使用 **JDK 17** 进行开发和构建。

## 🚀 快速开始（推荐）

项目已配置 **Maven Toolchains Plugin**，你只需配置一次 `toolchains.xml`，Maven 会自动使用 JDK 17。

### 1. 创建 toolchains.xml 配置文件

```powershell
# Windows PowerShell
# 复制模板到用户目录
Copy-Item toolchains.xml.example $env:USERPROFILE\.m2\toolchains.xml

# 如果 .m2 目录不存在，先创建
New-Item -ItemType Directory -Force -Path $env:USERPROFILE\.m2
```

```bash
# macOS/Linux
# 复制模板到用户目录
cp toolchains.xml.example ~/.m2/toolchains.xml

# 如果 .m2 目录不存在，先创建
mkdir -p ~/.m2
```

### 2. 配置你的 JDK 17 路径

编辑 `~/.m2/toolchains.xml`（或 `%USERPROFILE%\.m2\toolchains.xml`），修改 `<jdkHome>` 为你的本地路径：

```xml
<jdkHome>C:\Users\YOUR_USERNAME\.jdks\azul-17.0.17</jdkHome>
```

**常见 JDK 安装路径：**
- Windows: `C:\Users\{用户名}\.jdks\azul-17.0.17`
- macOS: `/Users/{用户名}/.jdks/azul-17.0.17` 或 `/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`
- Linux: `/home/{用户名}/.jdks/azul-17.0.17` 或 `/usr/lib/jvm/java-17-openjdk`

### 3. 验证配置

```bash
# 编译项目，Maven 会自动使用 toolchains 配置的 JDK
mvn clean compile

# 查看使用的 JDK 信息（添加 -X 调试输出）
mvn -X clean compile | Select-String "toolchain"
```

### 4. 正常使用 Maven

配置完成后，所有 Maven 命令都会自动使用 JDK 17：

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package

# 安装到本地仓库
mvn clean install
```

## 📋 多设备同步

`~/.m2/toolchains.xml` 是用户级配置文件，你可以：
- 通过云同步工具同步 `.m2` 目录
- 手动复制到其他设备
- 使用 Git 私有仓库管理（注意不要提交到公开仓库）

每台设备只需修改 `<jdkHome>` 为对应的本地路径即可。

## 🔍 工作原理

1. **项目配置**（pom.xml）
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-toolchains-plugin</artifactId>
       <!-- 声明项目需要 JDK 17 -->
   </plugin>
   ```

2. **用户配置**（~/.m2/toolchains.xml）
   ```xml
   <toolchain>
       <provides><version>17</version></provides>
       <configuration>
           <jdkHome>你的JDK17路径</jdkHome>
       </configuration>
   </toolchain>
   ```

3. **自动匹配**
   - Maven 读取项目要求（JDK 17）
   - 从 toolchains.xml 查找匹配的 JDK
   - 自动使用配置的 JDK 进行编译

## ⚠️ 注意事项

- ✅ `toolchains.xml` 在用户目录，**不会被 git 追踪**
- ✅ **不会泄露本地路径信息**到远程仓库
- ✅ 配置一次，**所有使用 toolchains 的项目**都生效
- ⚠️ 如果没有配置 toolchains.xml，Maven 会使用 `JAVA_HOME` 环境变量

## 🐛 故障排查

### Q: 编译报错 "No toolchain matched"
**A:** 检查 toolchains.xml：
```bash
# 查看 toolchains.xml 是否存在
ls ~/.m2/toolchains.xml  # macOS/Linux
dir $env:USERPROFILE\.m2\toolchains.xml  # Windows

# 验证 JDK 路径是否正确
java -version  # 查看当前 Java 版本
```

### Q: 如何确认 Maven 使用了正确的 JDK？
**A:** 添加 `-X` 参数查看调试信息：
```bash
mvn -X clean compile 2>&1 | Select-String "toolchain"
# 应该看到类似：[DEBUG] Toolchain (jdk): JDK[C:\Users\xxx\.jdks\azul-17.0.17]
```

### Q: 我有多个 JDK 版本怎么办？
**A:** 在 toolchains.xml 中添加多个 `<toolchain>` 配置，Maven 会根据项目要求自动选择。

### Q: 其他项目还能用其他 JDK 吗？
**A:** 可以！toolchains 支持多版本：
- 本项目声明需要 JDK 17，自动用 JDK 17
- 其他项目声明需要 JDK 11，自动用 JDK 11
- 没声明的项目，使用 `JAVA_HOME` 环境变量

## 📚 参考文档

- [Maven Toolchains 官方文档](https://maven.apache.org/guides/mini/guide-using-toolchains.html)
- [maven-toolchains-plugin 文档](https://maven.apache.org/plugins/maven-toolchains-plugin/)
