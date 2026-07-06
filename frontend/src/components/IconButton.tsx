import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { clsx } from 'clsx';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: ReactNode;
  label: string;
  active?: boolean;
}

export function IconButton({ icon, label, active, className, ...props }: Props) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      className={clsx(
        'inline-flex h-10 w-10 items-center justify-center rounded-tool border transition focus:outline-none focus:ring-2 focus:ring-ember/45',
        active
          ? 'border-copper bg-copper text-white shadow-forge'
          : 'border-black/10 bg-white/80 text-ink shadow-insetGlow hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:text-white dark:hover:border-ember dark:hover:text-ember',
        className,
      )}
      {...props}
    >
      {icon}
    </button>
  );
}
