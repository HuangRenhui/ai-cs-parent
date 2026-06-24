# Rerank功能快速参考

## 🚀 快速启动

### 1. 环境准备
```bash
# 启动Ollama
ollama serve

# 拉取BGE-Reranker模型
ollama pull bge-reranker:latest

# 验证API
curl http://localhost:11434/api/rerank -d '{
  "model": "bge-reranker:latest",
  "query": "测试",
  "documents": ["文档1", "文档2"]
}'
```

### 2. 配置检查
```yaml
# application.yml
rag:
  ollama:
    rerank-model: bge-reranker:latest  # ✓ 已配置
  retrieve:
    top-k: 5            # 初始检索数量
    rerank-top-k: 3     # 重排后保留
    min-score: 0.6      # 最低阈值
```

### 3. 启动服务
```bash
cd ai-cs-knowledge
mvn spring-boot:run
```

---

## 🔧 核心文件

| 文件 | 路径 | 说明 |
|------|------|------|
| OllamaScoringModel | `config/OllamaScoringModel.java` | ScoringModel实现 |
| LangChainConfig | `config/LangChainConfig.java` | Spring Bean配置 |
| RagProperties | `config/RagProperties.java` | 配置属性类 |
| application.yml | `resources/application.yml` | 应用配置 |

---

## ⚙️ 参数调优

### 推荐配置

```yaml
# 通用场景
top-k: 5
rerank-top-k: 3
min-score: 0.6

# 高精度场景
top-k: 10
rerank-top-k: 5
min-score: 0.7

# 高性能场景
top-k: 3
rerank-top-k: 2
min-score: 0.5
```

### 调优原则

- `top-k` ≥ 2 × `rerank-top-k`
- `min-score` 范围：0.5-0.8
- 根据AB测试结果调整

---

## ⚠️ 常见问题

### Q1: API调用失败
```bash
# 检查Ollama状态
ollama list

# 重新拉取模型
ollama pull bge-reranker:latest

# 测试连通性
curl http://localhost:11434/api/tags
```

### Q2: 重排效果不明显
- ✅ 提高 `min-score` 到 0.7-0.8
- ✅ 增大 `top-k` 到 10
- ✅ 检查Embedding模型质量

### Q3: 响应速度慢
- ✅ 减小 `top-k` 值
- ✅ 启用Redis缓存
- ✅ 考虑本地ONNX模型

---

## 📝 待完善事项

### 高优先级
- [ ] 实现真实的Ollama API响应解析
- [ ] 添加单元测试
- [ ] 性能监控指标

### 中优先级
- [ ] 缓存热门问答结果
- [ ] 异步处理支持
- [ ] 错误日志优化

### 低优先级
- [ ] 支持多模型切换
- [ ] GPU加速
- [ ] 自训练模型

---

## 🔗 相关文档

- **详细使用：** [RAG_USAGE_GUIDE.md](RAG_USAGE_GUIDE.md) - "Rerank重排功能详解"章节
- **扩展开发：** [SCORING_MODEL_GUIDE.md](SCORING_MODEL_GUIDE.md)
- **实现总结：** [RERANK_IMPLEMENTATION_SUMMARY.md](RERANK_IMPLEMENTATION_SUMMARY.md)

---

## 💡 快速提示

### 查看实时日志
```bash
# 查看Rerank调用日志
tail -f logs/application.log | grep "Rerank"
```

### 测试接口
```bash
# 访问Swagger UI
http://localhost:8083/doc.html

# 测试FAQ搜索接口
POST /api/faq/search?question=你的问题
```

### 性能监控
```java
// 在代码中添加耗时统计
long start = System.currentTimeMillis();
// ... Rerank逻辑 ...
log.info("Rerank耗时: {}ms", System.currentTimeMillis() - start);
```

---

**最后更新：** 2026-06-24  
**版本：** v1.0
