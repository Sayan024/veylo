/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Manrope', 'system-ui', 'sans-serif'] },
      colors: {
        forest: { DEFAULT: '#163D2A', deep: '#0F2A1D', soft: '#2B5A40' },
        lime: { DEFAULT: '#C8FF65', deep: '#8FD13F', tint: 'rgb(var(--lime-tint) / <alpha-value>)' },
        cream: '#F7F7F2',
        charcoal: '#1C211D',
        bg: 'rgb(var(--bg) / <alpha-value>)',
        card: 'rgb(var(--card) / <alpha-value>)',
        cardalt: 'rgb(var(--card-alt) / <alpha-value>)',
        ink: 'rgb(var(--ink) / <alpha-value>)',
        muted: 'rgb(var(--muted) / <alpha-value>)',
        line: 'rgb(var(--line) / <alpha-value>)',
        owe: { DEFAULT: 'rgb(var(--owe) / <alpha-value>)', bg: 'rgb(var(--owe-bg) / <alpha-value>)' },
        owed: { DEFAULT: 'rgb(var(--owed) / <alpha-value>)', bg: 'rgb(var(--owed-bg) / <alpha-value>)' },
        warn: 'rgb(var(--warn) / <alpha-value>)',
      },
      borderRadius: { xl2: '20px', xl3: '24px', xl4: '28px' },
      boxShadow: { soft: '0 6px 24px -10px rgba(22,61,42,0.18)', lift: '0 18px 40px -16px rgba(15,42,29,0.35)' },
      keyframes: {
        rise: { from: { opacity: '0', transform: 'translateY(8px)' }, to: { opacity: '1', transform: 'none' } },
        fade: { from: { opacity: '0' }, to: { opacity: '1' } },
        ping2: { '0%': { transform: 'scale(0.2)', opacity: '0.6' }, '100%': { transform: 'scale(1)', opacity: '0' } },
      },
      // `fade` is opacity-only: a transform on a page wrapper would trap position:fixed bars inside it.
      animation: { rise: 'rise .35s ease-out both', fade: 'fade .3s ease-out both', ping2: 'ping2 1.8s ease-out infinite' },
    },
  },
  plugins: [],
}
