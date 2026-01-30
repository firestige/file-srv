# AI Copilot 协作规范

## Git Commit 规范

### 规则
- **短消息**（≤ 100 字符）：使用 `git commit -m "message"`
- **长消息**（> 100 字符）：必须使用文件方式提交，避免命令行长度限制

### AI 助手操作流程

#### 短消息提交（≤ 100 字符）
```bash
git commit -m "short message"
```

#### 长消息提交（> 100 字符）
**必须使用以下步骤**：

1. 使用 `create_file` 工具创建临时文件：
```javascript
create_file({
  filePath: "/tmp/commit_msg.txt",
  content: "very long commit message..."
})
```

2. 使用文件提交：
```bash
git commit -F /tmp/commit_msg.txt
```

3. 清理临时文件：
```bash
rm /tmp/commit_msg.txt
```

**或使用项目脚本**（脚本需要预先创建好的文件）：
```bash
.github/scripts/git-commit.sh /tmp/commit_msg.txt
```

### 关键约束

❌ **禁止的操作**：
- 使用命令行参数传递长消息：`script.sh "long message"`
- 使用命令行重定向写文件：`echo "msg" > file`
- 使用 `-m` 提交超过 100 字符的消息

✅ **必须的操作**：
- 使用工具直接写文件（`create_file`）
- 使用 `git commit -F` 读取文件提交
- 避免长内容经过命令行参数传递

### 原理说明

命令行有长度限制（通常几 KB），超长参数会导致：
- 命令行失去响应
- 消息被截断
- 状态跟踪失败

因此必须直接将内容写入文件，而不是通过命令行参数传递。

## 示例

### 错误示例 ❌
```bash
# 问题：长消息通过参数传递
./script.sh "very long message that exceeds 100 characters..."

# 问题：使用重定向
echo "long message" > /tmp/msg.txt
```

### 正确示例 ✅
```javascript
// 1. AI 使用 create_file 工具
create_file({
  filePath: "/tmp/commit_msg.txt",
  content: `feat: implement comprehensive API documentation alignment

- Add GET /files/{fkey}/metadata endpoint
- Add GET /static/{fkey} documentation
- Unify path parameter naming to camelCase
- Fix response status codes`
})

// 2. 使用文件提交
run_in_terminal({
  command: "git commit -F /tmp/commit_msg.txt && rm /tmp/commit_msg.txt"
})
```

## 开发规范

### 提交流程
1. 暂存更改：`git add -A`
2. 判断消息长度：
   - ≤ 100 字符 → `git commit -m "message"`
   - \> 100 字符 → 使用 `create_file` + `git commit -F`
3. 推送：`git push`

### AI 协作检查清单
- [ ] 消息长度是否超过 100 字符？
- [ ] 如果超过，是否使用了 `create_file` 工具？
- [ ] 是否避免了命令行参数传递长内容？
- [ ] 是否避免了使用重定向写文件？

