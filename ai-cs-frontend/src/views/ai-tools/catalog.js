/**
 * 运营侧 AI 工具清单：先铺页面，点卡片进入对应工作台。
 * 写入/删除仍走占位提示，等后端接通后再换接口。
 */
export const AI_TOOLS = [
  {
    code: 'entry',
    name: '智能录入',
    category: '录入',
    summary: '粘贴一段文字，识别姓名、手机、问题后写入客户、工单或知识库。',
    hint: '适合从聊天记录、邮件原文快速建档。',
    icon: 'EditPen',
    color: '#2f6bff'
  },
  {
    code: 'import',
    name: '智能导入',
    category: '导入',
    summary: '上传 CSV / 表格，自动对列并预览，确认后再批量写入。',
    hint: '先看预览，不对再改列映射，避免写错字段。',
    icon: 'Upload',
    color: '#12b76a'
  },
  {
    code: 'batch-delete',
    name: '批量清理',
    category: '清理',
    summary: '用一句话描述要删的数据，核对名单后再批量删除。',
    hint: '删除前必须勾选确认，避免误清生产数据。',
    icon: 'Delete',
    color: '#f04438'
  },
  {
    code: 'faq-draft',
    name: '知识起草',
    category: '知识',
    summary: '把对话或说明整理成 FAQ 问句和答案草稿。',
    hint: '草稿可再人工改，不直接对外发布。',
    icon: 'Reading',
    color: '#f79009'
  },
  {
    code: 'ticket-fill',
    name: '工单补全',
    category: '工单',
    summary: '粘贴投诉或咨询原文，自动填工单类型、内容和联系方式。',
    hint: '高风险字段仍要坐席核对后再建单。',
    icon: 'Tickets',
    color: '#7a5af8'
  }
]

/** 按编码取工具，没有则返回空 */
export const findAiTool = (code) => AI_TOOLS.find((t) => t.code === code) || null
