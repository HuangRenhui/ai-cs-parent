ai-cs-parent  【父工程，统一版本、依赖管理】
├─ ai-cs-gateway        网关服务（入口、路由、鉴权、限流）
├─ ai-cs-base-service   基础服务（用户、坐席、会话、客户档案）
├─ ai-cs-knowledge      知识库服务（FAQ、文档向量化、Milvus检索）
├─ ai-cs-ai-agent       智能体核心服务（LLM调用、意图识别、FunctionCall、多轮对话）
├─ ai-cs-workorder     工单服务（工单创建、流转、派单）
├─ ai-cs-websocket     长连接服务（WebSocket 实时聊天）
├─ ai-cs-job            定时任务服务（数据统计、会话清理、报表）
├─ ai-cs-common：工具类、常量、统一返回体、实体、枚举、异常
├─ai-cs-api：各服务间 Feign 调用接口
└─ai-cs-frontend：前端应用（Vue3 + ElementPlus）
