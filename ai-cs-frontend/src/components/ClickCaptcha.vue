<template>
  <!-- 点选验证弹窗：先看提示字序，再在图上依次点中，通过后才放行登录 -->
  <el-dialog
    class="captcha-dialog"
    :model-value="modelValue"
    title="请完成安全验证"
    width="380px"
    align-center
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @opened="refresh"
    @update:model-value="onVisible"
  >
    <div class="captcha-body" :class="{ shake: shaking }">
      <div class="captcha-stage">
        <canvas
          ref="canvasRef"
          class="captcha-canvas"
          @click="onCanvasClick"
        />
        <!-- 点击序号圆点，叠在图上方便对照点没点对 -->
        <span
          v-for="(pt, idx) in clicks"
          :key="idx"
          class="captcha-dot"
          :style="{ left: pt.viewX + 'px', top: pt.viewY + 'px' }"
        >{{ idx + 1 }}</span>
        <button type="button" class="captcha-refresh" title="换一张" @click="refresh">
          <el-icon :size="16"><RefreshRight /></el-icon>
        </button>
      </div>
      <p class="captcha-hint">
        请依次点击【<span class="captcha-words">{{ targetText }}</span>】
      </p>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 点选图片验证码：在花草底图上打散汉字，用户按提示顺序点中即可。
 * 演示环境本地出题校验，不走后端；失败自动换图，避免反复点同一张。
 */
import { computed, nextTick, ref } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'

defineProps({
  /** 是否弹出验证窗 */
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'success', 'cancel'])

/** 候选字尽量笔画差异大，避免点选时认错 */
const CHAR_POOL = '地山河风云月星雨花林田园春夏秋冬诚信智慧客服系统安然顺畅通达明德'.split('')
const COLORS = ['#c62828', '#2e7d32', '#1565c0', '#6a1b9a', '#ef6c00', '#00838f', '#ad1457']
const CANVAS_W = 340
const CANVAS_H = 176
const TARGET_COUNT = 3
const DECOY_COUNT = 4

const canvasRef = ref(null)
const clicks = ref([])
const shaking = ref(false)
const placed = ref([])
const targets = ref([])

const targetText = computed(() => targets.value.join(','))

/** 弹窗开关：打开就出题，关掉视为取消本次登录 */
const onVisible = (val) => {
  emit('update:modelValue', val)
  if (!val) emit('cancel')
}

/** 从字库无放回抽 n 个字 */
const pickChars = (n) => {
  const bag = [...CHAR_POOL]
  const out = []
  while (out.length < n && bag.length) {
    const i = Math.floor(Math.random() * bag.length)
    out.push(bag.splice(i, 1)[0])
  }
  return out
}

/** 两点距离，用来避免汉字叠在一起点不准 */
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y)

/**
 * 给每个字找一个不重叠的落点，靠近边缘会点不到所以留边距。
 */
const placeChars = (items) => {
  const result = []
  items.forEach((item) => {
    let pos = { x: CANVAS_W / 2, y: CANVAS_H / 2 }
    for (let tryCount = 0; tryCount < 50; tryCount++) {
      const next = {
        x: 32 + Math.random() * (CANVAS_W - 64),
        y: 36 + Math.random() * (CANVAS_H - 64)
      }
      // 字与字太近会互相挡住，换位置再试
      if (result.every((p) => dist(p, next) >= 52)) {
        pos = next
        break
      }
    }
    result.push({
      ...item,
      ...pos,
      rot: (Math.random() - 0.5) * 0.85,
      size: 26 + Math.random() * 6,
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
      hitR: 30
    })
  })
  return result
}

/** 画一朵简单花，让底图看起来像实景验证码而不是纯色块 */
const drawFlower = (ctx, x, y, r, color) => {
  ctx.save()
  ctx.translate(x, y)
  const petals = 8
  for (let i = 0; i < petals; i++) {
    ctx.save()
    ctx.rotate((Math.PI * 2 * i) / petals)
    ctx.beginPath()
    ctx.ellipse(0, -r * 0.52, r * 0.26, r * 0.52, 0, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.globalAlpha = 0.9
    ctx.fill()
    ctx.restore()
  }
  ctx.beginPath()
  ctx.arc(0, 0, r * 0.26, 0, Math.PI * 2)
  ctx.fillStyle = '#f6e27a'
  ctx.globalAlpha = 1
  ctx.fill()
  ctx.restore()
}

/** 绘制淡色底图 + 散落汉字 */
const paint = () => {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  canvas.width = CANVAS_W * dpr
  canvas.height = CANVAS_H * dpr
  canvas.style.width = `${CANVAS_W}px`
  canvas.style.height = `${CANVAS_H}px`
  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  const bg = ctx.createLinearGradient(0, 0, CANVAS_W, CANVAS_H)
  bg.addColorStop(0, '#d7e9fb')
  bg.addColorStop(0.45, '#f3efe4')
  bg.addColorStop(1, '#e4efd6')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, CANVAS_W, CANVAS_H)

  drawFlower(ctx, 78, 96, 42, '#e6c84a')
  drawFlower(ctx, 168, 58, 34, '#e28aa8')
  drawFlower(ctx, 268, 108, 38, '#d9a0c4')
  ctx.fillStyle = 'rgba(76, 140, 70, 0.18)'
  ctx.beginPath()
  ctx.ellipse(120, 150, 70, 18, -0.2, 0, Math.PI * 2)
  ctx.fill()
  ctx.beginPath()
  ctx.ellipse(250, 40, 50, 14, 0.3, 0, Math.PI * 2)
  ctx.fill()

  ctx.save()
  ctx.font = '12px "Microsoft YaHei", sans-serif'
  ctx.fillStyle = 'rgba(255,255,255,0.35)'
  ctx.rotate(-0.4)
  for (let i = 0; i < 6; i++) {
    ctx.fillText('演示', -20 + i * 90, 80 + (i % 2) * 40)
  }
  ctx.restore()

  placed.value.forEach((item) => {
    ctx.save()
    ctx.translate(item.x, item.y)
    ctx.rotate(item.rot)
    ctx.font = `700 ${item.size}px "Microsoft YaHei", "PingFang SC", sans-serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.lineWidth = 3
    ctx.strokeStyle = 'rgba(255,255,255,0.65)'
    ctx.strokeText(item.char, 0, 0)
    ctx.fillStyle = item.color
    ctx.fillText(item.char, 0, 0)
    ctx.restore()
  })
}

/** 换一张新题，清空已点序号 */
const refresh = () => {
  clicks.value = []
  const picked = pickChars(TARGET_COUNT + DECOY_COUNT)
  targets.value = picked.slice(0, TARGET_COUNT)
  const items = picked.map((char, idx) => ({
    char,
    isTarget: idx < TARGET_COUNT,
    targetIndex: idx < TARGET_COUNT ? idx : -1
  }))
  placed.value = placeChars(items)
  nextTick(paint)
}

/** 把点击换算成画布坐标，再按顺序校验三个目标字 */
const onCanvasClick = (event) => {
  if (clicks.value.length >= TARGET_COUNT) return
  const canvas = canvasRef.value
  const rect = canvas.getBoundingClientRect()
  const x = ((event.clientX - rect.left) / rect.width) * CANVAS_W
  const y = ((event.clientY - rect.top) / rect.height) * CANVAS_H
  clicks.value = [
    ...clicks.value,
    {
      x,
      y,
      viewX: event.clientX - rect.left,
      viewY: event.clientY - rect.top
    }
  ]
  if (clicks.value.length === TARGET_COUNT) {
    validate()
  }
}

/** 三个点都要落在对应目标字的点击范围内，顺序必须一致 */
const validate = () => {
  const ok = clicks.value.every((pt, idx) => {
    const target = placed.value.find((item) => item.targetIndex === idx)
    return target && dist(pt, target) <= target.hitR
  })
  if (ok) {
    emit('success')
    emit('update:modelValue', false)
    return
  }
  // 点错了先晃一下，再换图，避免用户对着错误点位继续点
  shaking.value = true
  window.setTimeout(() => {
    shaking.value = false
    refresh()
  }, 420)
}
</script>

<style scoped>
.captcha-body {
  user-select: none;
}
.captcha-stage {
  position: relative;
  width: 340px;
  margin: 0 auto;
  border-radius: 8px;
  overflow: hidden;
  background: #f4f6f8;
}
.captcha-canvas {
  display: block;
  width: 340px;
  height: 176px;
  cursor: pointer;
}
.captcha-refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  color: #667085;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(28, 43, 74, 0.12);
}
.captcha-refresh:hover {
  color: #2f6bff;
}
.captcha-dot {
  position: absolute;
  width: 22px;
  height: 22px;
  margin-left: -11px;
  margin-top: -11px;
  border-radius: 50%;
  background: #2f6bff;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  box-shadow: 0 0 0 2px #fff;
}
.captcha-hint {
  margin: 12px 0 0;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e7edf5;
  border-radius: 8px;
  color: #667085;
  font-size: 13px;
  background: #f8fafc;
}
.captcha-words {
  color: #2f6bff;
  font-weight: 700;
  margin: 0 2px;
}
.shake {
  animation: captcha-shake 0.4s ease;
}
@keyframes captcha-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  75% { transform: translateX(6px); }
}
</style>
