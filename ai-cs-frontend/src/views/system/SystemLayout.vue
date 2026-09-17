<template>
  <!-- 系统管理：与运维/开放接入同一套下划线标签 + 单卡片 -->
  <div class="system-layout">
    <SectionSwitch :model-value="active" :options="tabOptions" @change="go" />
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionSwitch from '../../components/SectionSwitch.vue'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)
/**
 * 标签名与侧栏二级菜单保持一致，便于对照。
 * 本组只放「账号与权限体系 + 平台配置」，全部为超管专属；
 * 操作审计与流量分析属观测类，已归到运维。
 */
const tabOptions = [
  { value: '/system/user', label: '用户' },
  { value: '/system/role', label: '角色' },
  { value: '/system/menu', label: '菜单' },
  { value: '/system/config', label: '配置' },
  { value: '/system/tenant', label: '多租户' },
  { value: '/system/skill-group', label: '技能组' }
]
const go = (path) => router.push(path)
</script>
