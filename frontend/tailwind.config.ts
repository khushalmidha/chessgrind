import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        boardLight: '#eeeed2',
        boardDark: '#769656',
        ink: '#151713',
        moss: '#81b64c',
        ember: '#f6a04d',
        skywash: '#d7ecff',
      },
      boxShadow: {
        board: '0 24px 60px rgba(0,0,0,.28)',
      },
    },
  },
  plugins: [],
} satisfies Config;
