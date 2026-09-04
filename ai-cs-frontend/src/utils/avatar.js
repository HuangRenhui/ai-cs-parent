export const DEFAULT_AVATAR = '/default-avatar.svg'

export const GENDER_MALE = 1
export const GENDER_FEMALE = 2

export const DEFAULT_MALE_AVATARS = [
  '/avatars/male/01.png',
  '/avatars/male/02.png',
  '/avatars/male/03.png'
]

export const DEFAULT_FEMALE_AVATARS = [
  '/avatars/female/01.png',
  '/avatars/female/02.png',
  '/avatars/female/03.png'
]

export function poolByGender(gender) {
  if (gender === GENDER_MALE) return DEFAULT_MALE_AVATARS
  if (gender === GENDER_FEMALE) return DEFAULT_FEMALE_AVATARS
  return []
}

export function isSystemDefaultAvatar(url) {
  if (!url) return true
  const value = String(url).trim()
  if (!value || value === DEFAULT_AVATAR) return true
  return DEFAULT_MALE_AVATARS.includes(value) || DEFAULT_FEMALE_AVATARS.includes(value)
}

export function pickRandomDefaultAvatar(gender) {
  const pool = poolByGender(gender)
  if (!pool.length) return DEFAULT_AVATAR
  return pool[Math.floor(Math.random() * pool.length)]
}

export function genderLabel(gender) {
  if (gender === GENDER_MALE) return '男'
  if (gender === GENDER_FEMALE) return '女'
  return '—'
}

export function resolveAvatar(url) {
  if (!url) {
    return DEFAULT_AVATAR
  }
  const value = String(url).trim()
  if (!value || value === DEFAULT_AVATAR) {
    return DEFAULT_AVATAR
  }
  if (value.startsWith('http://') || value.startsWith('https://') || value.startsWith('data:')) {
    return value
  }
  if (value.startsWith('/files/')) {
    return '/api' + value
  }
  if (value.startsWith('/api/')) {
    return value
  }
  return value
}
