/** Trip style — same keys and detection rules as the Android app. */
export type TripTheme = 'hills' | 'beach' | 'business' | 'vacation' | 'cafe'

export const THEMES: { id: TripTheme; label: string; emoji: string; accent: string }[] = [
  { id: 'hills', label: 'Hill station', emoji: '🏔', accent: '#2F7A4A' },
  { id: 'beach', label: 'Beach', emoji: '🏖', accent: '#0E9AA7' },
  { id: 'business', label: 'Business', emoji: '💼', accent: '#3D5A9E' },
  { id: 'vacation', label: 'Vacation', emoji: '🌴', accent: '#E07A3F' },
  { id: 'cafe', label: 'Café & outing', emoji: '☕', accent: '#8B5E3C' },
]

export const themeInfo = (t: TripTheme) => THEMES.find((x) => x.id === t) ?? THEMES[3]

const HILLS = ['hill', 'mountain', 'himalaya', 'trek', 'valley', 'snow', 'ski', 'peak', 'darjeeling', 'shimla', 'manali', 'ooty', 'munnar',
  'kodaikanal', 'mussoorie', 'nainital', 'gangtok', 'sikkim', 'leh', 'ladakh', 'kashmir', 'srinagar', 'gulmarg', 'pahalgam', 'coorg',
  'mahabaleshwar', 'lonavala', 'khandala', 'matheran', 'kasol', 'spiti', 'auli', 'rishikesh', 'dharamshala', 'mcleodganj', 'shillong',
  'tawang', 'kedarnath', 'chopta', 'mount abu', 'kalimpong', 'sandakphu', 'everest', 'nepal', 'bhutan', 'landour', 'yercaud', 'lansdowne']
const BEACH = ['beach', 'sea', 'ocean', 'coast', 'island', 'goa', 'gokarna', 'andaman', 'havelock', 'neil island', 'puri', 'digha',
  'mandarmani', 'pondicherry', 'puducherry', 'kovalam', 'varkala', 'alibaug', 'tarkarli', 'diu', 'daman', 'lakshadweep', 'marina', 'juhu',
  'maldives', 'bali', 'phuket', 'krabi', 'boracay', 'hawaii', 'cancun', 'seychelles', 'mauritius', 'miami', 'pattaya', 'langkawi', 'zanzibar']
const BUSINESS = ['business', 'office', 'conference', 'client', 'meeting', 'work', 'offsite', 'summit', 'expo', 'corporate', 'workshop',
  'training', 'seminar', 'sales', 'team trip', 'hackathon', 'onsite']

const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Guesses the style from the destination and name. Business words win (a Goa conference is business). */
export function detectTheme(destination: string, name: string, isOuting: boolean): TripTheme {
  const text = ` ${destination.toLowerCase()} ${name.toLowerCase()} `
  const hit = (words: string[]) => words.some((w) => new RegExp(`(^|[^a-z])${esc(w)}(s|es)?(?![a-z])`).test(text))
  if (hit(BUSINESS)) return 'business'
  if (hit(BEACH)) return 'beach'
  if (hit(HILLS)) return 'hills'
  return isOuting ? 'cafe' : 'vacation'
}

/** Groups saved before styles existed: work it out from what we know. */
export const themeOf = (g: { theme?: TripTheme | null; destination: string; name: string; type: string }): TripTheme =>
  g.theme ?? detectTheme(g.destination, g.name, g.type === 'outing')
