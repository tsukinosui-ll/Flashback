<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import type { AudioItem } from '../../types'

const props = defineProps<{
  audio: AudioItem
}>()

const isPlaying = ref(false)
let audioContext: UniApp.InnerAudioContext | null = null

const resolveUrl = (url: string): string => {
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080'
  return `${base}${url.startsWith('/') ? '' : '/'}${url}`
}

const formatDuration = (seconds: number): string => {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

const togglePlay = () => {
  if (isPlaying.value && audioContext) {
    audioContext.stop()
    audioContext.destroy()
    audioContext = null
    isPlaying.value = false
    return
  }

  audioContext = uni.createInnerAudioContext()
  audioContext.src = resolveUrl(props.audio.url)

  audioContext.onEnded(() => {
    audioContext?.destroy()
    audioContext = null
    isPlaying.value = false
  })

  audioContext.onError(() => {
    audioContext?.destroy()
    audioContext = null
    isPlaying.value = false
  })

  audioContext.play()
  isPlaying.value = true
}

onUnmounted(() => {
  if (audioContext) {
    audioContext.destroy()
    audioContext = null
  }
})
</script>

<template>
  <view class="audio-player" @tap="togglePlay">
    <view class="audio-player__icon" :class="{ 'audio-player__icon--playing': isPlaying }">
      <view v-if="isPlaying" class="audio-player__bars">
        <view class="audio-player__bar" />
        <view class="audio-player__bar" />
        <view class="audio-player__bar" />
      </view>
      <text v-else class="audio-player__play-symbol">▶</text>
    </view>
    <text class="audio-player__duration">{{ formatDuration(audio.duration) }}</text>
    <text class="audio-player__hint">{{ isPlaying ? '播放中...' : '点击收听' }}</text>
  </view>
</template>

<style scoped>
.audio-player {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  padding: 14rpx 20rpx;
  background: rgba(245, 240, 232, 0.6);
  border: 1rpx solid rgba(192, 182, 165, 0.25);
  border-radius: 6rpx;
  margin: 8rpx 0;
}

.audio-player__icon {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(181, 53, 42, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.audio-player__icon--playing {
  background: rgba(181, 53, 42, 0.15);
}

.audio-player__play-symbol {
  font-size: 16rpx;
  color: #b5352a;
}

.audio-player__bars {
  display: flex;
  align-items: center;
  gap: 3rpx;
  height: 20rpx;
}

.audio-player__bar {
  width: 4rpx;
  background: #b5352a;
  border-radius: 2rpx;
  animation: bar-play 0.6s ease-in-out infinite alternate;
}

.audio-player__bar:nth-child(1) { height: 60%; animation-delay: 0s; }
.audio-player__bar:nth-child(2) { height: 100%; animation-delay: 0.2s; }
.audio-player__bar:nth-child(3) { height: 40%; animation-delay: 0.4s; }

@keyframes bar-play {
  0% { opacity: 0.4; }
  100% { opacity: 1; }
}

.audio-player__duration {
  font-family: 'Noto Sans SC', 'PingFang SC', sans-serif;
  font-size: 24rpx;
  color: #302e29;
  letter-spacing: 0.03em;
  flex-shrink: 0;
}

.audio-player__hint {
  font-family: 'Noto Sans SC', 'PingFang SC', sans-serif;
  font-size: 20rpx;
  color: #9e9890;
  letter-spacing: 0.06em;
  flex-shrink: 0;
}
</style>
