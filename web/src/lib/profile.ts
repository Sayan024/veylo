import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ThemeMode = 'light' | 'dark' | 'system'

interface ProfileState {
  deviceId: string
  name: string
  upiId: string
  currency: string
  theme: ThemeMode
  onboarded: boolean
  update: (p: Partial<Omit<ProfileState, 'update' | 'deviceId'>>) => void
  reset: () => void
}

/** No-login identity: a random per-device id plus a display name. */
export const useProfile = create<ProfileState>()(
  persist(
    (set) => ({
      deviceId: crypto.randomUUID(),
      name: '',
      upiId: '',
      currency: 'INR',
      theme: 'light',
      onboarded: false,
      update: (p) => set(p),
      reset: () => set({ name: '', upiId: '', onboarded: false, deviceId: crypto.randomUUID() }),
    }),
    { name: 'veylo-profile' },
  ),
)

export const profile = () => useProfile.getState()
