<template>
  <div class="page-card not-found">
    <h2>页面不存在</h2>
    <p>当前地址没有对应功能，请从左侧菜单进入工作台或其他模块。</p>
    <el-button type="primary" @click="$router.push('/dashboard')">返回工作台</el-button>
  </div>
</template>

<style scoped>
.not-found { text-align: center; padding: 64px 20px; }
.not-found h2 { margin-bottom: 12px; }
.not-found p { color: #6b7280; margin-bottom: 24px; }
</style>
