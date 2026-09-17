<template>
  <!-- 全站共用：帮助中心弹窗，内容复用 HelpCenterPage 的嵌入模式 -->
  <el-dialog
    title="帮助中心"
    v-model="helpCenter.visible"
    width="920px"
    top="5vh"
    class="help-dialog"
    destroy-on-close
    append-to-body
  >
    <HelpCenterPage embedded />
  </el-dialog>
</template>

<script setup>
/**
 * 帮助中心对话框：挂在 App 上一次，聊天/登录/知识库都调 openHelpCenter。
 */
import HelpCenterPage from '../views/HelpCenterPage.vue'
import { helpCenter } from '../composables/useHelpCenter'
</script>

<style>
/* 弹窗 teleport 到 body，scoped 选不中，用全局类限制高度 */
.help-dialog .el-dialog__body {
  max-height: calc(88vh - 86px);
  overflow-y: auto;
  padding-top: 4px;
}
</style>
