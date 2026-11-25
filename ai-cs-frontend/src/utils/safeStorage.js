/**
 * localStorage / sessionStorage 的安全封装。
 *
 * 浏览器隐私模式、企业策略或第三方 iframe 禁用站点数据时，直接访问 Storage 会抛
 * SecurityError，导致应用启动白屏。这里统一降级到内存实现：当次会话可用，刷新后丢失，
 * 但不会阻断页面渲染。
 */
const memoryLocal = new Map()
const memorySession = new Map()

const safe = (fn, fallback = null) => {
  try {
    return fn()
  } catch {
    return fallback
  }
}

const storageOf = (kind) => {
  if (typeof window === 'undefined') return null
  try {
    return kind === 'session' ? window.sessionStorage : window.localStorage
  } catch {
    return null
  }
}

const read = (kind, memory, key) => {
  const storage = storageOf(kind)
  const value = safe(() => (storage ? storage.getItem(key) : null), null)
  return value !== null ? value : (memory.has(key) ? memory.get(key) : null)
}

const write = (kind, memory, key, value) => {
  memory.set(key, String(value))
  const storage = storageOf(kind)
  safe(() => storage && storage.setItem(key, String(value)))
}

const remove = (kind, memory, key) => {
  memory.delete(key)
  const storage = storageOf(kind)
  safe(() => storage && storage.removeItem(key))
}

export const getItem = (key) => read('local', memoryLocal, key)
export const setItem = (key, value) => write('local', memoryLocal, key, value)
export const removeItem = (key) => remove('local', memoryLocal, key)

export const sessionGetItem = (key) => read('session', memorySession, key)
export const sessionSetItem = (key, value) => write('session', memorySession, key, value)
export const sessionRemoveItem = (key) => remove('session', memorySession, key)
