<template>
  <img :src="src" :alt="alt" :class="imgClass" @error="onError" />
</template>

<script setup>
import { ref, watch } from 'vue'
import { DEFAULT_AVATAR, resolveAvatar } from '../utils/avatar'

const props = defineProps({
  url: { type: String, default: '' },
  alt: { type: String, default: '' },
  imgClass: { type: String, default: '' }
})

const src = ref(resolveAvatar(props.url))

watch(() => props.url, (value) => {
  src.value = resolveAvatar(value)
})

const onError = () => {
  if (src.value !== DEFAULT_AVATAR) {
    src.value = DEFAULT_AVATAR
  }
}
</script>
