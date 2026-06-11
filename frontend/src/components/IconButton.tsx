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
        'inline-flex h-10 w-10 items-center justify-center rounded-md border transition focus:outline-none focus:ring-2 focus:ring-moss',
        active
          ? 'border-moss bg-moss text-white'
          : 'border-black/10 bg-white/85 text-ink hover:border-moss dark:border-white/10 dark:bg-white/10 dark:text-white',
        className,
      )}
      {...props}
    >
      {icon}
    </button>
  );
}
