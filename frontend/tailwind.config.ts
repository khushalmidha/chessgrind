import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        display: ['"Space Grotesk"', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      colors: {
        boardLight: '#ead8b7',
        boardDark: '#9f6f43',
        ink: '#221812',
        night: '#15111d',
        copper: '#c9652f',
        ember: '#f08a3c',
        forge: '#ffe0b0',
        ash: '#f4efe8',
        violet: '#4b3a73',
      },
      boxShadow: {
        board: '0 28px 70px rgba(41,24,13,.30)',
        forge: '0 18px 45px rgba(117,61,28,.16)',
        insetGlow: 'inset 0 1px 0 rgba(255,255,255,.45)',
      },
      borderRadius: {
        forge: '14px',
        tool: '10px',
      },
    },
  },
  plugins: [],
} satisfies Config;
