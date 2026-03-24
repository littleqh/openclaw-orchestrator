import { ref, onUnmounted } from 'vue'
import { connectSse } from '../api/index.js'

export function useSse(instanceId, onMessage) {
  const connected = ref(false)
  const error = ref(null)
  let eventSource = null
  let retryCount = 0
  const MAX_RETRIES = 5
  // 重试间隔: 1s, 2s, 4s, 8s, 16s
  const retryIntervals = [1000, 2000, 4000, 8000, 16000]
  let retryTimeout = null

  function connect() {
    if (!instanceId.value) return

    // Close existing connection before creating new one
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }

    error.value = null

    eventSource = connectSse(
      instanceId.value,
      (data) => {
        connected.value = true
        retryCount = 0
        onMessage(data)
      },
      (e) => {
        connected.value = false
        error.value = '连接中断'
        scheduleRetry()
      }
    )
  }

  function scheduleRetry() {
    if (retryCount >= MAX_RETRIES) {
      error.value = '连接失败，请手动重连'
      return
    }

    const delay = retryIntervals[Math.min(retryCount, retryIntervals.length - 1)]
    retryCount++

    retryTimeout = setTimeout(() => {
      connect()
    }, delay)
  }

  function disconnect() {
    if (retryTimeout) {
      clearTimeout(retryTimeout)
      retryTimeout = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    connected.value = false
  }

  function reconnect() {
    disconnect()
    retryCount = 0
    connect()
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    error,
    reconnect,
    disconnect,
    connect
  }
}
