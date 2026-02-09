# TaskContext 重构进度跟踪

## 总体目标
重构 TaskContext 为分层架构，并实现 Spring MVC 风格的注解参数注入

---

## 任务清单

### ✅ Phase 1: 基础架构重构（已完成）

#### 1.1 分层 Context 设计 ✅
- [x] ExecutionInfoContext - 任务执行信息
- [x] PluginOutputsContext - 插件间数据传递  
- [x] FileMetadataContext - 元数据更新记录
- [x] DerivedFilesContext - 衍生文件管理
- [x] PluginParamsContext - 插件参数访问（List<CallbackConfig> + currentIndex）

#### 1.2 注解系统定义 ✅
- [x] @SharedPlugin - 插件标记注解
- [x] @PluginExecute - 执行方法标记
- [x] @PluginParam - 插件参数注入
- [x] @LocalFile - 本地文件路径注入
- [x] @TaskInfo - 任务信息注入
- [x] @PluginOutput - 前序插件输出注入

#### 1.3 简化 API ✅
- [x] 旧 API: `context.getPluginParam("pluginName", "paramKey")`
- [x] 新 API: `context.getPluginParam("paramKey")`
- [x] 保留旧 API 兼容性（标记 @Deprecated）

#### 1.4 插件调用接口 ✅
- [x] PluginInvoker 接口定义
- [x] PluginMethodInvoker 基础框架实现
- [x] DefaultPluginRegistry 集成 PluginInvoker
- [x] DefaultCallbackChainRunner 使用 PluginInvoker

---

### ⏳ Phase 2: 注解注入功能（未完成）

#### 2.1 PluginMethodInvoker 完整实现 ❌
- [x] 构造函数：找到 @PluginExecute 方法
- [ ] resolvePluginParam() - 解析 @PluginParam 注入
- [ ] resolveLocalFile() - 解析 @LocalFile 注入
- [ ] resolveTaskInfo() - 解析 @TaskInfo 注入
- [ ] resolvePluginOutput() - 解析 @PluginOutput 注入
- [ ] 类型转换逻辑（String -> Integer/Boolean/Long/Path/File）
- [ ] 必填参数校验（required = true）
- [ ] 默认值处理（defaultValue）

#### 2.2 错误处理 ❌
- [ ] 参数缺失异常
- [ ] 类型转换异常
- [ ] 方法调用异常
- [ ] 友好的错误信息

#### 2.3 测试更新 ❌
- [ ] 更新测试插件使用注解注入
- [ ] 编写注解注入的单元测试
- [ ] 验证不同注解组合的测试

---

### ⏳ Phase 3: 示例和文档（部分完成）

#### 3.1 Example 插件更新 ⚠️
- [ ] ThumbnailPlugin 使用注解注入
- [ ] RenamePlugin 使用注解注入
- [ ] HashVerifyPlugin 使用注解注入
- [ ] 移除 pom.xml 中的编译排除配置

#### 3.2 文档完善 ⚠️
- [x] plugin-injection-guide.md（已创建但需要更新）
- [ ] 注解使用示例
- [ ] 最佳实践文档
- [ ] 迁移指南（旧 API -> 新 API）

---

### 📊 Phase 4: 验证和优化（未开始）

#### 4.1 集成测试 ❌
- [ ] 端到端测试（注解注入场景）
- [ ] 性能测试（反射调用开销）
- [ ] 边界场景测试

#### 4.2 性能优化 ❌
- [ ] 方法缓存（避免重复反射查找）
- [ ] 参数解析器缓存
- [ ] 考虑编译期注解处理器（APT）替代运行期反射

#### 4.3 兼容性处理 ❌
- [ ] 验证所有现有插件正常工作
- [ ] 提供平滑迁移路径
- [ ] 标记废弃 API 的清理计划

---

## 当前状态总结

### ✅ 已完成（~40%）
- 分层 Context 架构
- 注解定义
- 简化的 API
- 基础框架集成

### 🚧 进行中（0%）
- 无

### ⏳ 待完成（~60%）
- 注解注入核心逻辑实现（最重要）
- 测试插件和 Example 插件更新
- 完整的测试覆盖
- 文档完善

---

## 下一步行动

**优先级 P0（核心功能）：**
1. 实现 PluginMethodInvoker 的参数解析逻辑
2. 实现类型转换和校验
3. 更新测试插件验证功能

**优先级 P1（完善）：**
4. 更新 Example 插件
5. 编写完整的单元测试
6. 完善文档和示例

**优先级 P2（优化）：**
7. 性能优化
8. 错误处理增强
9. 兼容性验证

---

## 估算
- Phase 1: 已完成 ✅
- Phase 2: 约需 4-6 小时
- Phase 3: 约需 2-3 小时  
- Phase 4: 约需 2-3 小时

**总体完成度：约 40%**
