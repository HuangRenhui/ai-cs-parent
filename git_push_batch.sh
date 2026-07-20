#!/bin/bash
# ============================================================================
#  按模块功能点分批 git commit & push（含详细功能点明细）
#  在项目根目录执行: bash git_push_batch.sh
#  分支: main
# ============================================================================

set -e

REPO="d:/projectManagement/ai-cs-parent"
BRANCH="main"

echo "========================================"
echo "  Module-based Git Push (10 commits)"
echo "  Branch: $BRANCH"
echo "========================================"

# ============================================================================
# [1/10] ai-cs-common: 公共模块优化
# ============================================================================
echo ""
echo "####################################################################"
echo "[1/10] ai-cs-common: 公共模块优化"
echo "####################################################################"

cd "$REPO"
git add ai-cs-common/
git commit -m "feat(common): public module optimization

=== 功能点明细 ===

[新增文件]
- dto/LoginDTO.java          - 登录请求/响应数据传输对象
- dto/StatisticsDTO.java     - 统计数据传输对象（小时/日报）
- dto/SysConfigDTO.java      - 系统配置数据传输对象
- security/JwtContext.java   - JWT上下文持有者（ThreadLocal）
- security/NoAuth.java       - 免认证注解（跳过JWT校验）
- security/RequirePermission.java - 权限校验注解（RBAC权限控制）
- util/JwtUtil.java          - JWT工具类（生成/解析/刷新Token）

[修改文件]
- pom.xml                    - 新增jjwt、spring-security依赖
- config/MyMetaObjectHandler.java   - 自动填充优化（createBy/updateBy）
- constant/RedisKeyConst.java       - 新增会话、统计、缓存Redis Key常量
- dto/ChatDTO.java                 - 多模态消息字段扩展（图片/音频/视频URL）
- dto/IntentDTO.java               - 意图识别字段扩展（置信度/实体提取）
- dto/WorkOrderDTO.java            - 工单DTO字段扩展（流程实例ID/分类标签）
- entity/BaseEntity.java           - 基础实体扩展（createBy/updateBy字段）
- enums/MsgTypeEnum.java           - 消息类型枚举扩展（IMAGE/AUDIO/VIDEO）
- exception/GlobalExceptionHandler.java - 全局异常处理器增强（JWT异常/权限异常）
- result/Result.java               - 统一返回结果增强（链式调用/错误码体系）"

git push origin "$BRANCH"
echo "[1/10] Done."

# ============================================================================
# [2/10] ai-cs-base-service: 基础服务RBAC权限体系
# ============================================================================
echo ""
echo "####################################################################"
echo "[2/10] ai-cs-base-service: 基础服务RBAC权限体系"
echo "####################################################################"

cd "$REPO"
git add ai-cs-base-service/
git commit -m "feat(base-service): RBAC auth system

=== 功能点明细 ===

[新增文件]
- config/SecurityConfig.java              - Spring Security安全配置（JWT过滤器/URL权限）
- controller/AuthController.java          - 认证控制器（登录/登出/刷新Token/验证码）
- controller/LogController.java           - 操作日志控制器（日志查询/导出/清理）
- controller/MenuController.java          - 菜单管理控制器（树形菜单CRUD/权限绑定）
- controller/RoleController.java          - 角色管理控制器（角色CRUD/权限分配）
- controller/StatisticsController.java    - 数据统计控制器（工单/会话/客户统计）
- controller/SysConfigController.java     - 系统配置控制器（配置项CRUD/缓存刷新）
- controller/UserController.java          - 用户管理控制器（用户CRUD/角色分配/密码重置）
- entity/Menu.java                        - 菜单实体（树形结构/权限标识/路由信息）
- entity/OperationLog.java                - 操作日志实体（操作人/IP/模块/内容/耗时）
- entity/Role.java                        - 角色实体（角色名/权限列表/状态）
- entity/Statistics.java                  - 统计实体（日期/类型/指标值/维度）
- entity/SysConfig.java                   - 系统配置实体（配置键/配置值/描述/分组）
- entity/User.java                        - 用户实体（用户名/密码/手机/邮箱/状态）
- mapper/MenuMapper.java                  - 菜单Mapper（MyBatis-Plus BaseMapper）
- mapper/OperationLogMapper.java          - 操作日志Mapper
- mapper/RoleMapper.java                  - 角色Mapper
- mapper/StatisticsMapper.java            - 统计Mapper（聚合查询/upsert）
- mapper/SysConfigMapper.java             - 系统配置Mapper
- mapper/UserMapper.java                  - 用户Mapper
- service/MenuService.java                - 菜单服务（树构建/权限校验/缓存）
- service/OperationLogService.java        - 操作日志服务（异步记录/AOP切面）
- service/RoleService.java                - 角色服务（权限树/批量分配）
- service/StatisticsService.java          - 统计服务（维度聚合/趋势分析）
- service/SysConfigService.java           - 系统配置服务（缓存读写/刷新）
- service/UserService.java                - 用户服务（密码加密/角色绑定）

[修改文件]
- pom.xml                                 - 新增spring-security、knife4j、actuator、prometheus依赖
- BaseServiceApplication.java             - 启用@EnableAsync、@EnableScheduling
- controller/CustomerController.java      - 添加权限注解、Swagger文档注解
- mapper/CustomerMapper.java              - 新增按条件分页查询方法
- service/CustomerService.java            - 扩展客户标签/分组/批量导入
- application.yml                         - 新增knife4j、management监控、security配置"

git push origin "$BRANCH"
echo "[2/10] Done."

# ============================================================================
# [3/10] ai-cs-knowledge: 知识库多模态增强
# ============================================================================
echo ""
echo "####################################################################"
echo "[3/10] ai-cs-knowledge: 知识库多模态增强"
echo "####################################################################"

cd "$REPO"
git add ai-cs-knowledge/
git commit -m "feat(knowledge): multimodal knowledge enhancement

=== 功能点明细 ===

[新增文件]
- db/migration/add_knowledge_enhance_tables.sql - 知识增强表结构（图/向量/版本/标签）
- service/GraphEvolutionService.java       - 知识图谱演化服务（节点/关系自动演化）
- service/GraphRagService.java             - 图增强RAG检索（知识图谱+向量混合检索）
- service/GraphReasoningEngine.java        - 图推理引擎（多跳推理/逻辑链推导）
- service/HybridRetrievalService.java      - 混合检索服务（全文+向量+图谱三路融合）
- service/ImageDeduplicationService.java   - 图片去重服务（感知哈希/pHash去重）
- service/ImageHotlinkService.java         - 图片防盗链服务（Referer校验/Token签名）
- service/ImageStatsService.java           - 图片统计服务（访问量/存储量/带宽统计）
- service/ImageStorageService.java         - 图片存储服务（本地/MinIO/OSS多后端）
- service/ImageVectorService.java          - 图片向量化服务（CLIP/ViT特征提取）
- service/ImageVersionService.java         - 图片版本管理服务（版本回滚/差异对比）
- service/KnowledgeAutoClusterService.java - 知识自动聚类服务（语义聚类/标签自动生成）
- service/KnowledgeGraphService.java       - 知识图谱服务（实体抽取/关系构建/Neo4j同步）
- service/MediaModerationService.java      - 媒体审核服务（敏感内容检测/合规校验）
- service/MediaOcrService.java             - 媒体OCR服务（图片文字识别/表格提取）
- service/MediaTagService.java             - 媒体标签服务（自动打标/标签推荐）
- service/MixedModalityChatService.java    - 混合模态对话服务（图文混合问答）
- service/Model3DKnowledgeService.java     - 3D模型知识服务（3D模型向量化/检索）
- service/MultilingualGraphFusionService.java - 多语言图谱融合服务（跨语言实体对齐）
- service/MultimodalKnowledgeService.java  - 多模态知识服务（图文音视统一入库）
- service/MultimodalSearchService.java     - 多模态搜索服务（以图搜图/以文搜图/跨模态）
- service/Neo4jIntegrationService.java     - Neo4j集成服务（Cypher查询/图算法/索引）
- service/RealtimeStreamService.java       - 实时流处理服务（知识变更事件/消息队列）
- service/TemporalKnowledgeGraphService.java - 时序知识图谱服务（时间维度/演化追踪）
- service/VideoKeyFrameService.java        - 视频关键帧服务（关键帧提取/场景检测）
- service/VisionLLMClient.java             - 视觉大模型客户端（图片理解/描述生成）

[修改文件]
- pom.xml                                  - 新增Neo4j、MinIO、消息队列依赖
- KnowledgeApplication.java                - 启用异步、定时任务
- config/ImageProperties.java              - 图片配置扩展（多后端/防盗链/审核）
- config/LangChainConfig.java              - LangChain配置扩展（多模态模型/图数据库）
- controller/ImageController.java          - 图片接口扩展（上传/搜索/版本/统计）
- entity/DocumentVersion.java              - 文档版本实体扩展（向量版本/图谱版本）
- entity/ImageMetadata.java                - 图片元数据扩展（向量/标签/审核状态）
- entity/KnowledgeFaq.java                 - FAQ实体扩展（多模态答案/关联图谱节点）
- mapper/KnowledgeFaqMapper.java           - FAQ Mapper扩展（向量检索/图谱查询）
- service/DocumentVersionService.java      - 文档版本服务扩展（向量化版本管理）
- service/FileUploadService.java           - 文件上传服务扩展（多模态文件/审核流水线）
- service/ImageProcessService.java         - 图片处理服务扩展（向量化/标签/审核）
- service/KnowledgeFaqService.java         - FAQ服务扩展（多模态答案/相似问）
- service/RagSearchService.java            - RAG检索服务扩展（混合检索/重排序）
- util/EmbeddingClient.java                - 嵌入客户端扩展（多模态嵌入模型）
- util/LlmClient.java                      - LLM客户端扩展（视觉模型/多模态对话）
- util/MilvusUtil.java                     - Milvus工具扩展（多模态向量集合）
- application.yml                          - 新增Neo4j/MinIO/消息队列/多模态模型配置"

git push origin "$BRANCH"
echo "[3/10] Done."

# ============================================================================
# [4/10] ai-cs-agent: AI代理多模态对话
# ============================================================================
echo ""
echo "####################################################################"
echo "[4/10] ai-cs-agent: AI代理多模态对话"
echo "####################################################################"

cd "$REPO"
git add ai-cs-agent/
git commit -m "feat(agent): multimodal chat and tool integration

=== 功能点明细 ===

[新增文件]
- service/AgentToolService.java       - Agent工具服务（Function Call注册/执行/结果解析）
- service/MultimodalChatService.java  - 多模态对话服务（图片/音频/视频理解、回退策略）

[修改文件]
- pom.xml                             - 新增knife4j API文档依赖
- AiAgentApplication.java             - 启用异步、注册Agent工具
- controller/AiChatController.java    - 新增多模态对话接口、Swagger文档注解
- enums/AiToolEnum.java               - 新增工具枚举（查询知识库/创建工单/查询订单/转人工）
- service/AiAgentService.java         - 集成多模态对话、Agent工具编排
- util/LlmUtil.java                   - 扩展多模态模型支持（视觉/音频输入）
- application.yml                     - 新增knife4j文档、多模态模型配置"

git push origin "$BRANCH"
echo "[4/10] Done."

# ============================================================================
# [5/10] ai-cs-workorder: 工单Flowable流程引擎
# ============================================================================
echo ""
echo "####################################################################"
echo "[5/10] ai-cs-workorder: 工单Flowable流程引擎"
echo "####################################################################"

cd "$REPO"
git add ai-cs-workorder/
git commit -m "feat(workorder): Flowable workflow engine integration

=== 功能点明细 ===

[新增文件]
- config/FlowableConfig.java               - Flowable引擎配置（数据源/字体/自动部署）
- controller/WorkOrderFlowController.java  - 流程控制器（7个REST端点）
                                              1. 启动流程 2. 审批任务 3. 查询待办
                                              4. 查询已办 5. 查询流程状态 6. 取消流程
                                              7. 流程历史
- service/WorkOrderFlowService.java        - 流程服务（流程定义/实例/任务管理）
- service/WorkOrderProcessService.java     - 工单流程处理服务
                                              - 自动分类（根据内容智能分类）
                                              - 自动回复（匹配FAQ自动应答）
                                              - 工单完结（满意度评价/归档）
- resources/processes/workOrderProcess.bpmn20.xml - BPMN流程定义
                                              节点: 提交 -> 自动分类 -> 自动回复 -> 人工处理 -> 审核 -> 完结
                                              分支: 4个条件网关（自动/人工/紧急/普通）

[修改文件]
- pom.xml                                  - 新增flowable-spring-boot-starter、knife4j依赖
- WorkOrderApplication.java                - 排除Flowable自动部署冲突
- controller/WorkOrderController.java      - 新增流程启动/查询接口、Swagger文档
- mapper/WorkOrderMapper.java              - 新增流程相关查询方法
- service/WorkOrderService.java            - 集成流程引擎、工单状态流转
- application.yml                          - 新增flowable引擎配置、knife4j文档"

git push origin "$BRANCH"
echo "[5/10] Done."

# ============================================================================
# [6/10] ai-cs-job: 定时任务实现
# ============================================================================
echo ""
echo "####################################################################"
echo "[6/10] ai-cs-job: 定时任务实现"
echo "####################################################################"

cd "$REPO"
git add ai-cs-job/
git commit -m "feat(job): scheduled tasks implementation

=== 功能点明细 ===

[新增文件]
- task/SessionCleanTask.java     - 会话清理任务（cron: 每天凌晨2点）
                                    - 清理7天前过期会话
                                    - 清理关联消息记录
                                    - 清理会话缓存
- task/StatisticsTask.java       - 数据统计任务
                                    - 每小时: 工单量/会话量/响应时长聚合
                                    - 每天凌晨1点: 日报生成（upsert策略）
- task/CacheWarmupTask.java      - 缓存预热任务（cron: 每天凌晨3点）
                                    - 预加载FAQ列表到Redis
                                    - 预加载系统配置到Redis
                                    - 预加载工单状态统计
- task/HealthCheckTask.java      - 健康检查任务（cron: 每5分钟）
                                    - 数据库连通性检查
                                    - Redis连通性检查
                                    - 连续3次失败触发告警
                                    - 恢复后发送恢复通知

[修改文件]
- pom.xml                        - 新增spring-boot-starter-jdbc、redis、mysql、commons-pool2依赖"

git push origin "$BRANCH"
echo "[6/10] Done."

# ============================================================================
# [7/10] ai-cs-frontend: 前端移动端响应式适配
# ============================================================================
echo ""
echo "####################################################################"
echo "[7/10] ai-cs-frontend: 前端移动端响应式适配"
echo "####################################################################"

cd "$REPO"
git add ai-cs-frontend/
git commit -m "feat(frontend): mobile responsive layout

=== 功能点明细 ===

[新增文件]
- src/views/DashboardPage.vue    - 仪表盘页面
                                    - 数据概览卡片（工单/会话/满意度/在线用户）
                                    - 趋势图表（ECharts折线图/柱状图）
                                    - 响应式网格: xs=24, sm=12, md=6
                                    - 移动端卡片堆叠布局
- src/views/LoginPage.vue        - 登录页面
                                    - 表单校验（用户名/密码/验证码）
                                    - 记住密码功能
                                    - 移动端全屏居中布局

[修改文件]
- index.html                     - 视口meta标签（viewport-fit=cover安全区域）
                                    - PWA相关meta标签
                                    - iOS安全区域CSS变量
- package.json                   - 新增echarts、element-plus依赖版本更新
- src/App.vue                    - 响应式导航布局
                                    - 桌面端: 侧边栏固定导航（宽度220px）
                                    - 平板端(<1024px): 可折叠侧边栏
                                    - 手机端(<768px): 底部Tab导航 + 抽屉菜单
                                    - 汉堡菜单按钮（移动端显示）
                                    - 面包屑导航
- src/router/index.js            - 新增登录页、仪表盘路由
                                    - 路由懒加载优化
- src/utils/request.js           - Axios请求拦截器
                                    - JWT Token自动携带
                                    - 401自动跳转登录
                                    - 响应错误统一提示
- src/views/ChatPage.vue         - 移动端紧凑布局
                                    - 消息气泡自适应宽度
                                    - 输入区域固定在底部
                                    - 键盘弹起适配
- src/views/WorkOrderPage.vue    - 搜索区域flex-wrap换行
                                    - 表格水平滚动（移动端）
                                    - 筛选条件折叠（移动端）
                                    - 卡片列表模式（手机端替代表格）"

git push origin "$BRANCH"
echo "[7/10] Done."

# ============================================================================
# [8/10] ai-cs-gateway + ai-cs-websocket: 网关监控与WebSocket优化
# ============================================================================
echo ""
echo "####################################################################"
echo "[8/10] ai-cs-gateway + ai-cs-websocket: 网关监控与WebSocket优化"
echo "####################################################################"

cd "$REPO"
git add ai-cs-gateway/ ai-cs-websocket/
git commit -m "feat(gateway,websocket): monitoring and WebSocket optimization

=== 功能点明细 ===

[新增文件]
- ai-cs-gateway/filter/JwtAuthFilter.java      - JWT认证过滤器
                                                  - Token解析与校验
                                                  - 用户信息注入请求头
                                                  - 免认证路径白名单
- ai-cs-gateway/filter/RequestLogFilter.java    - 请求日志过滤器
                                                  - 请求路径/方法/耗时记录
                                                  - 响应状态码记录

[修改文件]
- ai-cs-gateway/pom.xml                         - 新增spring-boot-starter-actuator依赖
- ai-cs-gateway/GatewayApplication.java         - 启用@EnableScheduling
- ai-cs-gateway/application.yml                 - 新增actuator路由配置
                                                  - management端点配置（health/info/metrics/prometheus）
                                                  - 网关CORS跨域配置
- ai-cs-websocket/WebSocketApplication.java     - 启用异步支持
- ai-cs-websocket/config/WebSocketConfig.java   - WebSocket配置优化
                                                  - 心跳检测机制（30s间隔）
                                                  - 最大连接数限制（1000）
                                                  - 消息大小限制（64KB）
- ai-cs-websocket/endpoint/ChatWebSocket.java   - WebSocket端点增强
                                                  - 用户认证（Token校验）
                                                  - 会话绑定（userId -> session）
                                                  - 离线消息缓存与推送
                                                  - 连接数实时统计"

git push origin "$BRANCH"
echo "[8/10] Done."

# ============================================================================
# [9/10] docs: 部署文档与数据库脚本
# ============================================================================
echo ""
echo "####################################################################"
echo "[9/10] docs: 部署文档与数据库脚本"
echo "####################################################################"

cd "$REPO"
git add docs/database/ docs/deployment/
git commit -m "docs: deployment guide and database init scripts

=== 功能点明细 ===

[新增文件]
- docs/database/init_security.sql              - 安全模块初始化SQL
                                                  - 默认用户/角色/菜单数据
                                                  - 默认权限关联数据
                                                  - 系统配置初始数据
- docs/deployment/production-guide.md           - 生产环境部署指南
                                                  - 环境要求（JDK17/MySQL8/Redis7/Neo4j5/MinIO）
                                                  - Docker Compose一键部署
                                                  - Nginx反向代理配置（HTTPS/WebSocket代理）
                                                  - 健康检查端点说明
                                                  - 日志收集方案（ELK）
                                                  - 监控方案（Prometheus + Grafana）
                                                  - 备份策略（MySQL定时备份脚本）
                                                  - 扩容建议（无状态服务水平扩展）"

git push origin "$BRANCH"
echo "[9/10] Done."

# ============================================================================
# [10/10] docs + pom.xml: 功能开发清单 + 根POM优化
# ============================================================================
echo ""
echo "####################################################################"
echo "[10/10] docs + pom.xml: 功能开发清单 + 根POM优化"
echo "####################################################################"

cd "$REPO"
git add docs/ pom.xml
git commit -m "docs: feature checklist and root pom optimization

=== 功能点明细 ===

[修改文件]
- docs/功能开发清单.md                           - 功能开发清单全面梳理
                                                  - 47个功能点状态确认（全部已完成）
                                                  - 新增项目现状总览表
                                                  - 修正定时任务/多模态/Flowable等状态标记
                                                  - 优先级建议调整
                                                  - 开发计划建议更新
- pom.xml                                        - 根POM优化
                                                  - 新增micrometer-registry-prometheus版本管理
                                                  - 新增flowable-spring-boot-starter版本管理
                                                  - 依赖版本统一管理（spring-cloud/spring-boot/flowable）
                                                  - 插件版本锁定"

git push origin "$BRANCH"
echo "[10/10] Done."

# ============================================================================
# 完成
# ============================================================================
echo ""
echo "========================================"
echo "  All 10 commits pushed successfully!"
echo "  Branch: $BRANCH"
echo "========================================"
