<template>
  <div class="app-container">
    <el-container>
      <el-aside width="200px" class="sidebar">
        <el-menu :default-active="$route.path" mode="vertical" router>
          <el-menu-item index="/chat">
            <el-icon><MessageChat /></el-icon>
            <span>AI聊天</span>
          </el-menu-item>
          <el-menu-item index="/customer">
            <el-icon><User /></el-icon>
            <span>客户管理</span>
          </el-menu-item>
          <el-menu-item index="/workorder">
            <el-icon><FileText /></el-icon>
            <span>工单管理</span>
          </el-menu-item>
          <el-menu-item index="/knowledge">
            <el-icon><BookOpen /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { MessageChat, User, FileText, BookOpen } from '@element-plus/icons-vue'
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
html, body {
  height: 100%;
}
.app-container {
  height: 100vh;
}
.sidebar {
  background-color: #2a3f5f;
}
.el-menu {
  border-right: none;
  height: 100%;
}
.el-menu-item {
  color: #fff;
}
.el-menu-item:hover, .el-menu-item.is-active {
  background-color: #1f2d4a;
}
</style>