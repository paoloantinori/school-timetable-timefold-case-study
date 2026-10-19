import type { ReactNode } from 'react';

interface ActionButtonProps {
  variant: 'primary' | 'secondary' | 'danger' | 'teal' | 'outline' | 'warning';
  onClick: () => void;
  disabled?: boolean;
  loading?: boolean;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
  title?: string;
}

const variantClasses: Record<ActionButtonProps['variant'], string> = {
  primary: 'bg-green-500 text-white shadow-lg shadow-green-500/30 hover:bg-green-600',
  secondary: 'bg-blue-500 text-white shadow-lg shadow-blue-500/30 hover:bg-blue-600',
  danger: 'bg-red-500 text-white shadow-lg shadow-red-500/30 hover:bg-red-600',
  teal: 'bg-teal-500 text-white shadow-lg shadow-teal-500/30 hover:bg-teal-600',
  outline: 'bg-transparent text-content border-border hover:bg-surface-hover',
  warning: 'bg-amber-500 text-white shadow-lg shadow-amber-500/30 hover:bg-amber-600',
};

export function ActionButton({
  variant,
  onClick,
  disabled,
  loading,
  icon,
  children,
  className = '',
  title,
}: ActionButtonProps) {
  const baseClasses = 'inline-flex items-center gap-1.5 px-3 py-2 border rounded-md text-xs font-semibold transition-all duration-150 whitespace-nowrap cursor-pointer';
  const disabledClasses = (disabled || loading) ? 'opacity-50 cursor-not-allowed' : '';

  return (
    <button
      className={`${baseClasses} ${variantClasses[variant]} ${disabledClasses} ${className}`}
      onClick={onClick}
      disabled={disabled || loading}
      title={title}
    >
      {loading ? (
        <span className="animate-spin text-sm">◌</span>
      ) : icon ? (
        <span className="flex items-center leading-none">{icon}</span>
      ) : null}
      <span>{children}</span>
    </button>
  );
}
