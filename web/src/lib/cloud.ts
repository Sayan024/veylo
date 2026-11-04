import { createClient, type SupabaseClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined
const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined

/** True when Supabase keys are configured. Without them VEYLO runs fully local. */
export const cloudEnabled = Boolean(url && key && !url.includes('YOUR-PROJECT'))

export const supabase: SupabaseClient | null = cloudEnabled
  ? createClient(url!, key!, { auth: { persistSession: false, autoRefreshToken: false } })
  : null

export class CloudError extends Error {
  constructor(message: string, readonly network: boolean) {
    super(message)
  }
}

/** Calls a SECURITY DEFINER database function. All table access goes through these. */
export async function rpc<T>(fn: string, args: Record<string, unknown>): Promise<T> {
  if (!supabase) throw new CloudError('Cloud sync is not configured', false)
  if (!navigator.onLine) throw new CloudError('Offline', true)
  try {
    const { data, error } = await supabase.rpc(fn, args)
    if (error) {
      const network = /fetch|network|timeout|Failed to fetch/i.test(error.message)
      throw new CloudError(error.message, network)
    }
    return data as T
  } catch (e) {
    if (e instanceof CloudError) throw e
    throw new CloudError((e as Error).message, true)
  }
}
