import { reactive } from 'vue'

/**
 * 帮助中心弹窗状态：全站共用，避免聊天、登录、知识库各挂一套。
 */
export const helpCenter = reactive({
  /** 弹窗是否打开 */
  visible: false
})

/** 在当前页打开帮助中心，不新开标签、不离开当前路由 */
export function openHelpCenter() {
  helpCenter.visible = true
}
