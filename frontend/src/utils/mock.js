const MOCK_FLAG = '__USN_MOCK__'

export function getMockEnabled() {
  if (typeof window === 'undefined') return true
  if (window[MOCK_FLAG] === true) return true
  if (window[MOCK_FLAG] === false) return false
  const flag = import.meta.env.VITE_USE_MOCK
  if (flag === 'false' || flag === '0') return false
  return true
}

export function setMockEnabled(enabled) {
  if (typeof window === 'undefined') return
  window[MOCK_FLAG] = Boolean(enabled)
}
