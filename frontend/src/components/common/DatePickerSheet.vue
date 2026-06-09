<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  visible: boolean
  title?: string
  initialValue?: string
}>(), {
  title: '选择解封时间',
  initialValue: '',
})

const emit = defineEmits<{
  confirm: [value: string]
  cancel: []
}>()

// ── column data ──
const yr = new Date().getFullYear()
const years = Array.from({ length: 11 }, (_, i) => yr + i)
const months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
const days = computed(() => {
  const year = years[idx.value[0]]
  const month = idx.value[1] + 1
  const max = new Date(year, month, 0).getDate()
  return Array.from({ length: max }, (_, i) => i + 1)
})
const hours = Array.from({ length: 24 }, (_, i) => i)
const minuteStep = Array.from({ length: 60 }, (_, i) => i)

// ── internal indices ──
const idx = ref([0, 0, 0, 0, 0]) // [year, month, day, hour, minute]
const pickerKey = ref(0)

function clampDay(yearIdx: number, monthIdx: number, dayIdx: number) {
  const max = new Date(years[yearIdx], monthIdx + 1, 0).getDate()
  return Math.min(dayIdx, max - 1)
}

function onColumnChange(e: { detail: { value: number[] } }) {
  const [y, m, d, h, min] = e.detail.value
  if (m !== idx.value[1] || y !== idx.value[0]) {
    const clamped = clampDay(y, m, d)
    idx.value = [y, m, clamped, h, min]
    if (clamped !== d) pickerKey.value++
  } else {
    idx.value = [y, m, d, h, min]
  }
}

function buildDateString(): string {
  const [yi, mi, di, hi, mini] = idx.value
  const y = years[yi]
  const m = String(months[mi]).padStart(2, '0')
  const d = String(days.value[di]).padStart(2, '0')
  const hh = String(hours[hi]).padStart(2, '0')
  const mm = String(minuteStep[mini]).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
}

function resolveIndices(): number[] {
  if (props.initialValue) {
    const parsed = new Date(props.initialValue.replace(' ', 'T'))
    if (!isNaN(parsed.getTime())) {
      const yi = years.indexOf(parsed.getFullYear())
      const mi = parsed.getMonth()
      const di = parsed.getDate() - 1
      const hi = parsed.getHours()
      const mini = parsed.getMinutes()
      return [
        yi >= 0 ? yi : 0,
        mi >= 0 ? mi : 0,
        di >= 0 ? di : 0,
        hi >= 0 ? hi : 0,
        mini >= 0 ? mini : 0,
      ]
    }
  }
  // default: now + 1 day
  const def = new Date(Date.now() + 86400000)
  const yi = years.indexOf(def.getFullYear())
  const mi = def.getMonth()
  const di = def.getDate() - 1
  const hi = def.getHours()
  const mini = Math.min(def.getMinutes(), 59)
  return [
    yi >= 0 ? yi : 0,
    mi >= 0 ? mi : 0,
    clampDay(yi >= 0 ? yi : 0, mi >= 0 ? mi : 0, di >= 0 ? di : 0),
    hi >= 0 ? hi : 0,
    mini >= 0 ? mini : 0,
  ]
}

watch(() => props.visible, (v) => {
  if (v) {
    idx.value = resolveIndices()
    nextTick(() => { idx.value = [...idx.value] })
  }
})

function onConfirm() {
  const selected = new Date(buildDateString())
  if (selected.getTime() <= Date.now()) {
    uni.showToast({ title: '请选择未来的解锁时间', icon: 'none' })
    return
  }
  emit('confirm', buildDateString())
}

const onCancel = () => emit('cancel')
</script>

<template>
  <view v-if="visible" class="picker-overlay" @tap="onCancel">
    <view class="picker-sheet" @tap.stop>
      <!-- header -->
      <view class="picker-header">
        <text class="picker-btn picker-btn--cancel" @tap="onCancel">取消</text>
        <text class="picker-title">{{ title }}</text>
        <text class="picker-btn picker-btn--confirm" @tap="onConfirm">确认</text>
      </view>

      <!-- wheel area -->
      <view class="picker-wheel-wrap">
        <picker-view
          :key="pickerKey"
          class="picker-wheel"
          :value="idx"
          indicator-style="height: 88rpx; border-top: 1rpx solid rgba(188,174,152,0.4); border-bottom: 1rpx solid rgba(188,174,152,0.4);"
          @change="onColumnChange"
        >
          <picker-view-column>
            <view class="picker-item" v-for="y in years" :key="y">{{ y }}年</view>
          </picker-view-column>
          <picker-view-column>
            <view class="picker-item" v-for="m in months" :key="m">{{ m }}月</view>
          </picker-view-column>
          <picker-view-column>
            <view class="picker-item" v-for="d in days" :key="d">{{ d }}日</view>
          </picker-view-column>
          <picker-view-column>
            <view class="picker-item" v-for="h in hours" :key="h">{{ h }}时</view>
          </picker-view-column>
          <picker-view-column>
            <view class="picker-item" v-for="m in minuteStep" :key="m">{{ m }}分</view>
          </picker-view-column>
        </picker-view>
      </view>

      <!-- preview -->
      <view class="picker-preview">
        <text class="picker-preview-text">{{ buildDateString() }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.picker-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(48, 46, 41, 0.32);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.picker-sheet {
  background: rgba(252, 249, 244, 0.97);
  border-top: 1rpx solid rgba(188, 174, 152, 0.35);
  backdrop-filter: blur(20rpx);
  padding-bottom: calc(env(safe-area-inset-bottom) + 16rpx);
}

/* ── header ── */
.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 36rpx;
  border-bottom: 1rpx solid rgba(188, 174, 152, 0.18);
}

.picker-title {
  font-family: var(--fb-font-serif, 'Noto Serif SC', 'Songti SC', Georgia, serif);
  font-size: 28rpx;
  font-weight: 400;
  color: var(--fb-ink, #302e29);
  letter-spacing: 0.06em;
}

.picker-btn {
  font-size: 26rpx;
  font-family: var(--fb-font-sans, 'Noto Sans SC', 'PingFang SC', sans-serif);
  letter-spacing: 0.04em;
  cursor: pointer;
}

.picker-btn--cancel {
  color: var(--fb-ink-light, #9e9890);
}

.picker-btn--confirm {
  color: var(--fb-vermilion, #b5352a);
  font-weight: 500;
}

/* ── wheel ── */
.picker-wheel-wrap {
  padding: 16rpx 0;
}

.picker-wheel {
  width: 100%;
  height: 440rpx;
}

.picker-item {
  height: 88rpx;
  font-family: var(--fb-font-serif, 'Noto Serif SC', 'Songti SC', Georgia, serif);
  font-size: 34rpx;
  color: var(--fb-ink-mid, #6b6560);
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.04em;
}

/* ── preview ── */
.picker-preview {
  padding: 20rpx 36rpx 8rpx;
  display: flex;
  justify-content: center;
  align-items: center;
}

.picker-preview-text {
  font-family: var(--fb-font-serif, 'Noto Serif SC', 'Songti SC', Georgia, serif);
  font-size: 30rpx;
  color: var(--fb-ink, #302e29);
  letter-spacing: 0.08em;
  font-weight: 400;
}
</style>
